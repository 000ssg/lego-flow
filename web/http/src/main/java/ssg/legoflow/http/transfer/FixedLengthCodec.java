package ssg.legoflow.http.transfer;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;

import java.nio.ByteBuffer;

public class FixedLengthCodec extends AbstractDataFilter<ByteBuffer> {

    private final long expectedLength;

    public FixedLengthCodec(long expectedLength) {
        super(ByteBuffer.class);
        this.expectedLength = expectedLength;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        long totalLength = 0;
        for (var d : data) {
            totalLength += d.remaining();
        }
        if (expectedLength >= 0 && totalLength > expectedLength) {
            ctx.handleError(new IllegalStateException(
                    "Content length exceeds expected: " + totalLength + " > " + expectedLength));
            return new ByteBuffer[0];
        }
        return data;
    }

    public long getExpectedLength() {
        return expectedLength;
    }
}
