package ssg.legoflow.media.rtp.codec;

import ssg.legoflow.media.rtp.packet.HeaderExtension;
import ssg.legoflow.media.rtp.packet.RtpHeader;
import ssg.legoflow.media.rtp.packet.RtpPacket;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Codec for encoding and decoding RTP packets to/from {@link ByteBuffer} (RFC 3550 Section 5).
 *
 * <p>Handles the full RTP header with bit-level manipulation of the first byte
 * (version, padding, extension, CSRC count) and second byte (marker, payload type).
 *
 * @since 0.1.0
 */
public final class RtpCodec {

    private static final Queue<ByteBuffer> BUFFER_POOL = new ConcurrentLinkedQueue<>();
    private static final int MAX_BUFFER_POOL_SIZE = 100;
    private static final int DEFAULT_BUFFER_SIZE = 1024;

    private RtpCodec() {}

    /**
     * Encodes an RTP packet into a newly allocated {@link ByteBuffer}.
     *
     * @param packet the RTP packet to encode
     * @return a ByteBuffer containing the encoded packet (position=0, limit=length)
     */
    public static ByteBuffer encode(RtpPacket packet) {
        var header = packet.header();
        byte[] payload = packet.payload();
        int size = header.totalSize() + payload.length;
        
        // Try to get a buffer from the pool
        ByteBuffer buf = BUFFER_POOL.poll();
        if (buf == null || buf.capacity() < size) {
            // If buffer is null or too small, allocate a new one
            buf = ByteBuffer.allocate(Math.max(size, DEFAULT_BUFFER_SIZE));
        } else {
            buf.clear(); // Reset buffer for reuse
        }

        try {
            // Byte 0: V=2, P, X, CC
            int firstByte = (header.version() & 0x03) << 6;
            if (header.padding()) firstByte |= 0x20;
            if (header.extension()) firstByte |= 0x10;
            firstByte |= header.csrcCount() & 0x0F;
            buf.put((byte) firstByte);

            // Byte 1: M, PT
            int secondByte = header.payloadType() & 0x7F;
            if (header.marker()) secondByte |= 0x80;
            buf.put((byte) secondByte);

            // Bytes 2-3: sequence number
            buf.putShort((short) header.sequenceNumber());

            // Bytes 4-7: timestamp (32-bit unsigned)
            buf.putInt((int) header.timestamp());

            // Bytes 8-11: SSRC (32-bit unsigned)
            buf.putInt((int) header.ssrc());

            // CSRC list
            for (long csrc : header.csrcList()) {
                buf.putInt((int) csrc);
            }

            // Header extension
            if (header.headerExtension().isPresent()) {
                var ext = header.headerExtension().get();
                buf.putShort((short) ext.profile());
                buf.putShort((short) ext.lengthInWords());
                buf.put(ext.data());
            }

            // Payload
            buf.put(payload);

            buf.flip();
            return buf;
        } finally {
            // Return buffer to pool if space available
            if (BUFFER_POOL.size() < MAX_BUFFER_POOL_SIZE) {
                BUFFER_POOL.offer(buf);
            }
        }
    }

    /**
     * Decodes an RTP packet from a {@link ByteBuffer}.
     *
     * <p>Reads from the current position. After decoding, the buffer position
     * is advanced past the decoded packet.
     *
     * @param buf the source buffer
     * @return the decoded RTP packet
     * @throws IllegalArgumentException if the buffer contains invalid data
     */
    public static RtpPacket decode(ByteBuffer buf) {
        if (buf.remaining() < RtpHeader.FIXED_SIZE) {
            throw new IllegalArgumentException(
                    "Buffer too small for RTP header: " + buf.remaining() + " bytes");
        }

        // Byte 0: V, P, X, CC
        int firstByte = buf.get() & 0xFF;
        int version = (firstByte >> 6) & 0x03;
        boolean padding = (firstByte & 0x20) != 0;
        boolean extensionBit = (firstByte & 0x10) != 0;
        int csrcCount = firstByte & 0x0F;

        // Byte 1: M, PT
        int secondByte = buf.get() & 0xFF;
        boolean marker = (secondByte & 0x80) != 0;
        int payloadType = secondByte & 0x7F;

        // Bytes 2-3: sequence number
        int sequenceNumber = buf.getShort() & 0xFFFF;

        // Bytes 4-7: timestamp
        long timestamp = buf.getInt() & 0xFFFFFFFFL;

        // Bytes 8-11: SSRC
        long ssrc = buf.getInt() & 0xFFFFFFFFL;

        // CSRC list
        List<Long> csrcList = new ArrayList<>(csrcCount);
        for (int i = 0; i < csrcCount; i++) {
            csrcList.add(buf.getInt() & 0xFFFFFFFFL);
        }

        // Header extension
        Optional<HeaderExtension> headerExtension;
        if (extensionBit) {
            int profile = buf.getShort() & 0xFFFF;
            int extLengthWords = buf.getShort() & 0xFFFF;
            byte[] extData = new byte[extLengthWords * 4];
            buf.get(extData);
            headerExtension = Optional.of(new HeaderExtension(profile, extData));
        } else {
            headerExtension = Optional.empty();
        }

        // Handle padding: the last byte of the padding indicates the padding length
        int payloadLength = buf.remaining();
        byte[] rawPayload = new byte[payloadLength];
        buf.get(rawPayload);

        byte[] payload;
        if (padding && payloadLength > 0) {
            int paddingLength = rawPayload[payloadLength - 1] & 0xFF;
            if (paddingLength > payloadLength || paddingLength == 0) {
                throw new IllegalArgumentException("Invalid padding length: " + paddingLength);
            }
            payload = new byte[payloadLength - paddingLength];
            System.arraycopy(rawPayload, 0, payload, 0, payload.length);
        } else {
            payload = rawPayload;
        }

        var header = new RtpHeader(version, padding, extensionBit, marker,
                payloadType, sequenceNumber, timestamp, ssrc,
                csrcList, headerExtension);
        return new RtpPacket(header, payload);
    }

    /**
     * Decodes an RTP packet from a byte array.
     *
     * @param data   the source byte array
     * @param offset the start offset
     * @param length the number of bytes to decode
     * @return the decoded RTP packet
     */
    public static RtpPacket decode(byte[] data, int offset, int length) {
        return decode(ByteBuffer.wrap(data, offset, length));
    }
    
    // Enhanced pool management methods
    private static ByteBuffer getBufferFromPool(int requiredSize) {
        ByteBuffer buffer = BUFFER_POOL.poll();
        if (buffer == null || buffer.capacity() < requiredSize) {
            return ByteBuffer.allocate(Math.max(requiredSize, DEFAULT_BUFFER_SIZE));
        }
        buffer.clear();
        return buffer;
    }
    
    private static void returnBufferToPool(ByteBuffer buffer) {
        if (buffer != null && BUFFER_POOL.size() < MAX_BUFFER_POOL_SIZE) {
            BUFFER_POOL.offer(buffer);
        }
    }
}
