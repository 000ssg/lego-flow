package ssg.legoflow.http2.frame;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import java.nio.ByteBuffer;
import ssg.legoflow.service.util.BufferPool;
import java.util.ArrayList;
public class Http2FrameCodec extends AbstractDataFilter<ByteBuffer> {

    private final Mode mode;
    private ByteBuffer accumulator;

    public enum Mode { ENCODE, DECODE }

    public Http2FrameCodec(Mode mode) {
        super(ByteBuffer.class);
        this.mode = mode;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        return switch (mode) {
            case ENCODE -> encodeFrames(data);
            case DECODE -> decodeFrames(data);
        };
    }

    private ByteBuffer[] encodeFrames(ByteBuffer[] data) {
        var results = new ByteBuffer[data.length];
        for (int i = 0; i < data.length; i++) {
            var frame = Http2Frame.decode(data[i]);
            results[i] = frame.encode();
        }
        return results;
    }

    private ByteBuffer[] decodeFrames(ByteBuffer[] data) {
        var combined = combineWithAccumulator(data);
        var frames = new ArrayList<ByteBuffer>();

        while (combined.remaining() >= Http2Frame.HEADER_SIZE) {
            combined.mark();
            int length = ((combined.get() & 0xFF) << 16)
                       | ((combined.get() & 0xFF) << 8)
                       | (combined.get() & 0xFF);
            combined.reset();

            int totalFrameSize = Http2Frame.HEADER_SIZE + length;
            if (combined.remaining() < totalFrameSize) {
                break;
            }

            var frameBytes = BufferPool.getBuffer(totalFrameSize);
            for (int i = 0; i < totalFrameSize; i++) {
                frameBytes.put(combined.get());
            }
            frameBytes.flip();
            frames.add(frameBytes);
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

    public boolean hasBufferedData() {
        return accumulator != null && accumulator.hasRemaining();
    }

    public Http2Frame decodeFrame(ByteBuffer data) {
        return Http2Frame.decode(data);
    }

    public ByteBuffer encodeFrame(Http2Frame frame) {
        return frame.encode();
    }
}
