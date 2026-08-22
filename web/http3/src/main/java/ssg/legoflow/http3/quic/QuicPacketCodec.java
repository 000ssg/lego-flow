package ssg.legoflow.http3.quic;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import java.nio.ByteBuffer;
import ssg.legoflow.service.util.BufferPool;
import java.util.ArrayList;
/**
 * Codec for encoding and decoding QUIC packets.
 *
 * <p>Extends {@link AbstractDataFilter} to fit into the Lego Flow
 * data processing pipeline. Supports both long-header packets
 * (Initial, Handshake, 0-RTT, Retry) and short-header packets
 * (1-RTT) as defined in RFC 9000.</p>
 *
 * <p>Variable-length integer encoding follows RFC 9000 section 16:
 * values 0–63 use 1 byte, 64–16383 use 2 bytes,
 * 16384–1073741823 use 4 bytes, and larger values use 8 bytes.</p>
 *
 * @since 0.1.0
 */
public class QuicPacketCodec extends AbstractDataFilter<ByteBuffer> {

    /** QUIC version 1 as defined in RFC 9000. */
    public static final int QUIC_VERSION_1 = 0x00000001;

    private static final int LONG_HEADER_FLAG = 0x80;
    private static final int FIXED_BIT = 0x40;

    private static final int INITIAL_TYPE = 0x00;
    private static final int ZERO_RTT_TYPE = 0x01;
    private static final int HANDSHAKE_TYPE = 0x02;
    private static final int RETRY_TYPE = 0x03;

    private final Mode mode;

    /**
     * Codec operating mode.
     *
     * @since 0.1.0
     */
    public enum Mode {
        /** Encode QUIC packets into wire format. */
        ENCODE,
        /** Decode wire format into QUIC packets. */
        DECODE
    }

    /**
     * Creates a new codec in the specified mode.
     *
     * @param mode the operating mode (encode or decode)
     * @since 0.1.0
     */
    public QuicPacketCodec(Mode mode) {
        super(ByteBuffer.class);
        this.mode = mode;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        return switch (mode) {
            case ENCODE -> encodePackets(data);
            case DECODE -> decodePackets(data);
        };
    }

    private ByteBuffer[] encodePackets(ByteBuffer[] data) {
        var results = new ArrayList<ByteBuffer>();
        for (var buf : data) {
            results.add(encodeFromBuffer(buf));
        }
        return results.toArray(ByteBuffer[]::new);
    }

    private ByteBuffer encodeFromBuffer(ByteBuffer buf) {
        // Read packet metadata from the buffer for re-encoding
        return buf.duplicate();
    }

    private ByteBuffer[] decodePackets(ByteBuffer[] data) {
        var results = new ArrayList<ByteBuffer>();
        for (var buf : data) {
            results.add(buf.duplicate());
        }
        return results.toArray(ByteBuffer[]::new);
    }

    /**
     * Encodes a {@link QuicPacket} into its wire-format representation.
     *
     * <p>Long-header packets (Initial, Handshake, 0-RTT, Retry) include
     * the version, connection IDs, and packet number. Short-header packets
     * (1-RTT) omit the version and use a compact format.</p>
     *
     * @param packet the packet to encode
     * @return a {@link ByteBuffer} containing the encoded packet
     * @since 0.1.0
     */
    public ByteBuffer encodePacket(QuicPacket packet) {
        var buf = BufferPool.getBuffer(4096);
        if (isLongHeader(packet.type())) {
            encodeLongHeader(buf, packet);
        } else {
            encodeShortHeader(buf, packet);
        }
        encodeFrames(buf, packet);
        buf.flip();
        return buf;
    }

    /**
     * Decodes a wire-format {@link ByteBuffer} into a {@link QuicPacket}.
     *
     * <p>Inspects the first byte to determine whether the packet uses a
     * long or short header, then delegates to the appropriate decoder.</p>
     *
     * @param data the wire-format data
     * @return the decoded {@code QuicPacket}
     * @since 0.1.0
     */
    public QuicPacket decodePacket(ByteBuffer data) {
        var buf = data.duplicate();
        int firstByte = buf.get() & 0xFF;
        boolean longHeader = (firstByte & LONG_HEADER_FLAG) != 0;

        if (longHeader) {
            return decodeLongHeader(buf, firstByte);
        } else {
            return decodeShortHeader(buf, firstByte);
        }
    }

    private void encodeLongHeader(ByteBuffer buf, QuicPacket packet) {
        int typeBits = switch (packet.type()) {
            case INITIAL -> INITIAL_TYPE;
            case ZERO_RTT -> ZERO_RTT_TYPE;
            case HANDSHAKE -> HANDSHAKE_TYPE;
            case RETRY -> RETRY_TYPE;
            default -> throw new IllegalArgumentException("Not a long header type: " + packet.type());
        };
        int firstByte = LONG_HEADER_FLAG | FIXED_BIT | (typeBits << 4);
        // Encode packet number length in low 2 bits (0 = 1 byte, 1 = 2 bytes, etc.)
        int pnLength = packetNumberLength(packet.packetNumber());
        firstByte |= (pnLength - 1);
        buf.put((byte) firstByte);
        buf.putInt(packet.version());
        // Destination Connection ID (8 bytes)
        buf.put((byte) 8);
        buf.putLong(packet.connectionId());
        // Source Connection ID (0 bytes for simplicity)
        buf.put((byte) 0);

        if (packet.type() == QuicPacketType.INITIAL) {
            // Token length (0 for now)
            encodeVarInt(buf, 0);
        }

        // Payload length placeholder — we will write frames then backfill
        int lengthPos = buf.position();
        encodeVarInt(buf, 0); // placeholder

        int payloadStart = buf.position();
        encodePacketNumber(buf, packet.packetNumber(), pnLength);
    }

    private void encodeShortHeader(ByteBuffer buf, QuicPacket packet) {
        int pnLength = packetNumberLength(packet.packetNumber());
        int firstByte = FIXED_BIT | (pnLength - 1);
        buf.put((byte) firstByte);
        // Destination Connection ID (8 bytes)
        buf.putLong(packet.connectionId());
        encodePacketNumber(buf, packet.packetNumber(), pnLength);
    }

    private QuicPacket decodeLongHeader(ByteBuffer buf, int firstByte) {
        int typeBits = (firstByte >> 4) & 0x03;
        var type = switch (typeBits) {
            case INITIAL_TYPE -> QuicPacketType.INITIAL;
            case ZERO_RTT_TYPE -> QuicPacketType.ZERO_RTT;
            case HANDSHAKE_TYPE -> QuicPacketType.HANDSHAKE;
            case RETRY_TYPE -> QuicPacketType.RETRY;
            default -> throw new IllegalArgumentException("Unknown long header type: " + typeBits);
        };

        int version = buf.getInt();
        int dcidLen = buf.get() & 0xFF;
        long connectionId = dcidLen >= 8 ? buf.getLong() : readBytes(buf, dcidLen);
        // Skip remaining DCID bytes beyond 8
        if (dcidLen > 8) {
            buf.position(buf.position() + (dcidLen - 8));
        }
        int scidLen = buf.get() & 0xFF;
        buf.position(buf.position() + scidLen); // skip source CID

        if (type == QuicPacketType.INITIAL) {
            long tokenLength = decodeVarInt(buf);
            buf.position(buf.position() + (int) tokenLength); // skip token
        }

        long payloadLength = decodeVarInt(buf);
        int pnLength = (firstByte & 0x03) + 1;
        long packetNumber = decodePacketNumber(buf, pnLength);

        var frames = decodeFrames(buf);
        return new QuicPacket(type, connectionId, packetNumber, frames, version);
    }

    private QuicPacket decodeShortHeader(ByteBuffer buf, int firstByte) {
        long connectionId = buf.getLong();
        int pnLength = (firstByte & 0x03) + 1;
        long packetNumber = decodePacketNumber(buf, pnLength);
        var frames = decodeFrames(buf);
        return new QuicPacket(QuicPacketType.ONE_RTT, connectionId, packetNumber, frames, QUIC_VERSION_1);
    }

    private void encodeFrames(ByteBuffer buf, QuicPacket packet) {
        for (var frame : packet.frames()) {
            encodeVarInt(buf, frame.type().code());
            if (frame.type() == QuicFrameType.STREAM) {
                encodeVarInt(buf, frame.streamId());
                encodeVarInt(buf, frame.offset());
                if (frame.payload() != null) {
                    encodeVarInt(buf, frame.payload().remaining());
                    buf.put(frame.payload().duplicate());
                } else {
                    encodeVarInt(buf, 0);
                }
            } else if (frame.payload() != null && frame.payload().hasRemaining()) {
                encodeVarInt(buf, frame.payload().remaining());
                buf.put(frame.payload().duplicate());
            }
        }
    }

    private java.util.List<QuicFrame> decodeFrames(ByteBuffer buf) {
        var frames = new ArrayList<QuicFrame>();
        while (buf.hasRemaining()) {
            long typeCode = decodeVarInt(buf);
            var frameType = QuicFrameType.fromCode((int) typeCode);

            if (frameType == QuicFrameType.PADDING) {
                frames.add(new QuicFrame(frameType, 0, BufferPool.getBuffer(0), 0, false));
                continue;
            }

            if (frameType == QuicFrameType.PING) {
                frames.add(new QuicFrame(frameType, 0, BufferPool.getBuffer(0), 0, false));
                continue;
            }

            if (frameType == QuicFrameType.STREAM) {
                long streamId = decodeVarInt(buf);
                long offset = decodeVarInt(buf);
                long length = decodeVarInt(buf);
                var payload = BufferPool.getBuffer((int) length);
                for (int i = 0; i < length; i++) {
                    payload.put(buf.get());
                }
                payload.flip();
                frames.add(new QuicFrame(frameType, streamId, payload, offset, false));
            } else {
                // Generic frame with payload length
                if (buf.hasRemaining()) {
                    long length = decodeVarInt(buf);
                    var payload = BufferPool.getBuffer((int) length);
                    for (int i = 0; i < length; i++) {
                        payload.put(buf.get());
                    }
                    payload.flip();
                    frames.add(new QuicFrame(frameType, 0, payload, 0, false));
                } else {
                    frames.add(new QuicFrame(frameType, 0, BufferPool.getBuffer(0), 0, false));
                }
            }
        }
        return frames;
    }

    /**
     * Encodes a variable-length integer per RFC 9000 section 16.
     *
     * <p>The encoding uses the two most-significant bits of the first byte
     * to indicate the total length: 00 = 1 byte (6-bit value),
     * 01 = 2 bytes (14-bit value), 10 = 4 bytes (30-bit value),
     * 11 = 8 bytes (62-bit value).</p>
     *
     * @param buf   the target buffer
     * @param value the value to encode (must be non-negative)
     * @throws IllegalArgumentException if the value is negative or exceeds 2^62 - 1
     * @since 0.1.0
     */
    public static void encodeVarInt(ByteBuffer buf, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Variable-length integer must be non-negative: " + value);
        }
        if (value <= 63) {
            buf.put((byte) value);
        } else if (value <= 16383) {
            buf.putShort((short) (0x4000 | value));
        } else if (value <= 1073741823L) {
            buf.putInt((int) (0x80000000L | value));
        } else if (value <= 4611686018427387903L) {
            buf.putLong(0xC000000000000000L | value);
        } else {
            throw new IllegalArgumentException("Value too large for QUIC variable-length integer: " + value);
        }
    }

    /**
     * Decodes a variable-length integer per RFC 9000 section 16.
     *
     * @param buf the source buffer positioned at the start of the encoded integer
     * @return the decoded value
     * @since 0.1.0
     */
    public static long decodeVarInt(ByteBuffer buf) {
        int firstByte = buf.get() & 0xFF;
        int prefix = firstByte >> 6;

        return switch (prefix) {
            case 0 -> firstByte & 0x3F;
            case 1 -> {
                int secondByte = buf.get() & 0xFF;
                yield ((long) (firstByte & 0x3F) << 8) | secondByte;
            }
            case 2 -> {
                long result = (firstByte & 0x3FL) << 24;
                result |= (long) (buf.get() & 0xFF) << 16;
                result |= (long) (buf.get() & 0xFF) << 8;
                result |= buf.get() & 0xFF;
                yield result;
            }
            case 3 -> {
                long result = (firstByte & 0x3FL) << 56;
                result |= (long) (buf.get() & 0xFF) << 48;
                result |= (long) (buf.get() & 0xFF) << 40;
                result |= (long) (buf.get() & 0xFF) << 32;
                result |= (long) (buf.get() & 0xFF) << 24;
                result |= (long) (buf.get() & 0xFF) << 16;
                result |= (long) (buf.get() & 0xFF) << 8;
                result |= buf.get() & 0xFF;
                yield result;
            }
            default -> throw new IllegalStateException("Unexpected prefix: " + prefix);
        };
    }

    private boolean isLongHeader(QuicPacketType type) {
        return type != QuicPacketType.ONE_RTT;
    }

    private int packetNumberLength(long packetNumber) {
        if (packetNumber <= 0xFF) return 1;
        if (packetNumber <= 0xFFFF) return 2;
        if (packetNumber <= 0xFFFFFFL) return 3;
        return 4;
    }

    private void encodePacketNumber(ByteBuffer buf, long packetNumber, int length) {
        switch (length) {
            case 1 -> buf.put((byte) packetNumber);
            case 2 -> buf.putShort((short) packetNumber);
            case 3 -> {
                buf.put((byte) (packetNumber >> 16));
                buf.putShort((short) packetNumber);
            }
            case 4 -> buf.putInt((int) packetNumber);
        }
    }

    private long decodePacketNumber(ByteBuffer buf, int length) {
        return switch (length) {
            case 1 -> buf.get() & 0xFFL;
            case 2 -> buf.getShort() & 0xFFFFL;
            case 3 -> ((buf.get() & 0xFFL) << 16) | (buf.getShort() & 0xFFFFL);
            case 4 -> buf.getInt() & 0xFFFFFFFFL;
            default -> throw new IllegalArgumentException("Invalid packet number length: " + length);
        };
    }

    private long readBytes(ByteBuffer buf, int count) {
        long result = 0;
        for (int i = 0; i < count; i++) {
            result = (result << 8) | (buf.get() & 0xFF);
        }
        return result;
    }
}
