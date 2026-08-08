package ssg.legoflow.http3;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.http3.quic.QuicPacketCodec;

import java.nio.ByteBuffer;
import ssg.legoflow.service.util.BufferPool;
import java.util.ArrayList;

/**
 * Codec for encoding and decoding HTTP/3 frames.
 *
 * <p>Extends {@link AbstractDataFilter} to fit into the Lego Flow data
 * processing pipeline. HTTP/3 frames use variable-length integers for
 * both the frame type and the payload length, following RFC 9114.</p>
 *
 * @since 0.1.0
 */
public class Http3FrameCodec extends AbstractDataFilter<ByteBuffer> {

    private final Mode mode;
    private ByteBuffer accumulator;

    /**
     * Codec operating mode.
     *
     * @since 0.1.0
     */
    public enum Mode {
        /** Encode HTTP/3 frames into wire format. */
        ENCODE,
        /** Decode wire format into HTTP/3 frames. */
        DECODE
    }

    /**
     * Creates a new codec in the specified mode.
     *
     * @param mode the operating mode
     * @since 0.1.0
     */
    public Http3FrameCodec(Mode mode) {
        super(ByteBuffer.class);
        this.mode = mode;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        return switch (mode) {
            case ENCODE -> encodeAll(data);
            case DECODE -> decodeAll(data);
        };
    }

    private ByteBuffer[] encodeAll(ByteBuffer[] data) {
        var results = new ByteBuffer[data.length];
        for (int i = 0; i < data.length; i++) {
            results[i] = data[i].duplicate();
        }
        return results;
    }

    private ByteBuffer[] decodeAll(ByteBuffer[] data) {
        var combined = combineWithAccumulator(data);
        var frames = new ArrayList<ByteBuffer>();

        while (combined.hasRemaining()) {
            combined.mark();
            try {
                var frame = decodeFrame(combined);
                frames.add(encodeFrame(frame));
            } catch (Exception e) {
                combined.reset();
                break;
            }
        }

        if (combined.hasRemaining()) {
            accumulator = BufferPool.getBuffer(combined.remaining());
            accumulator.put(combined);
            accumulator.flip();
        } else {
            accumulator = null;
        }

        return frames.toArray(ByteBuffer[]::new);
    }

    /**
     * Encodes an HTTP/3 frame into wire format.
     *
     * <p>The wire format is: variable-length integer type, variable-length
     * integer length, payload bytes.</p>
     *
     * @param frame the frame to encode
     * @return a {@link ByteBuffer} containing the encoded frame
     * @since 0.1.0
     */
    public ByteBuffer encodeFrame(Http3Frame frame) {
        var payload = frame.payload().duplicate();
        int payloadLen = payload.remaining();

        var buf = BufferPool.getBuffer(16 + payloadLen);
        encodeVarInt(buf, frame.type().code());
        encodeVarInt(buf, payloadLen);
        buf.put(payload);
        buf.flip();
        return buf;
    }

    /**
     * Decodes an HTTP/3 frame from wire format.
     *
     * @param data the wire-format data
     * @return the decoded frame
     * @throws IllegalArgumentException if the data is insufficient
     * @since 0.1.0
     */
    public Http3Frame decodeFrame(ByteBuffer data) {
        long typeCode = decodeVarInt(data);
        long length = decodeVarInt(data);

        if (data.remaining() < length) {
            throw new IllegalArgumentException("Insufficient data for frame payload");
        }

        var payload = BufferPool.getBuffer((int) length);
        for (int i = 0; i < length; i++) {
            payload.put(data.get());
        }
        payload.flip();

        var type = Http3FrameType.fromCode(typeCode);
        return new Http3Frame(type, payload);
    }

    /**
     * Encodes a variable-length integer per RFC 9000 section 16.
     *
     * @param buf   the target buffer
     * @param value the value to encode
     * @since 0.1.0
     */
    public static void encodeVarInt(ByteBuffer buf, long value) {
        QuicPacketCodec.encodeVarInt(buf, value);
    }

    /**
     * Decodes a variable-length integer per RFC 9000 section 16.
     *
     * @param buf the source buffer
     * @return the decoded value
     * @since 0.1.0
     */
    public static long decodeVarInt(ByteBuffer buf) {
        return QuicPacketCodec.decodeVarInt(buf);
    }

    /**
     * Returns whether there is buffered data awaiting more input.
     *
     * @return {@code true} if partial data is buffered
     * @since 0.1.0
     */
    public boolean hasBufferedData() {
        return accumulator != null && accumulator.hasRemaining();
    }

    private ByteBuffer combineWithAccumulator(ByteBuffer[] data) {
        int totalSize = (accumulator != null ? accumulator.remaining() : 0);
        for (var buf : data) {
            totalSize += buf.remaining();
        }
        var combined = BufferPool.getBuffer(totalSize);
        if (accumulator != null) {
            combined.put(accumulator.duplicate());
            accumulator = null;
        }
        for (var buf : data) {
            combined.put(buf.duplicate());
        }
        combined.flip();
        return combined;
    }
}
