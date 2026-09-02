package ssg.legoflow.http.transfer;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
/**
 * Close-delimited message body codec per RFC 7230 §3.3.3.
 *
 * <p>When neither Content-Length nor Transfer-Encoding: chunked is present,
 * the message body is delimited by the connection close. This codec accumulates
 * all data until the connection is closed, then produces the complete body.
 *
 * @since 0.1.0
 */
public class CloseDelimitedCodec extends AbstractDataFilter<ByteBuffer> {

    private final ByteArrayOutputStream accumulator = new ByteArrayOutputStream();
    private volatile boolean connectionClosed = false;

    public CloseDelimitedCodec() {
        super(ByteBuffer.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        for (var buf : data) {
            var dup = buf.duplicate();
            var bytes = new byte[dup.remaining()];
            dup.get(bytes);
            accumulator.writeBytes(bytes);
        }
        if (connectionClosed) {
            return new ByteBuffer[]{ByteBuffer.wrap(accumulator.toByteArray())};
        }
        return new ByteBuffer[0];
    }

    /**
     * Signals that the connection has been closed, finalizing the body.
     *
     * @return the accumulated body as a ByteBuffer
     */
    public ByteBuffer finishBody() {
        connectionClosed = true;
        return ByteBuffer.wrap(accumulator.toByteArray());
    }

    /**
     * Returns whether the connection has been closed.
     *
     * @return true if the connection is closed
     */
    public boolean isConnectionClosed() {
        return connectionClosed;
    }

    /**
     * Returns the number of bytes accumulated so far.
     *
     * @return the accumulated byte count
     */
    public int getAccumulatedSize() {
        return accumulator.size();
    }

    /**
     * Determines the message body framing strategy based on headers.
     *
     * <p>Per RFC 7230 §3.3.3, the priority is:
     * <ol>
     *   <li>Transfer-Encoding: chunked</li>
     *   <li>Content-Length</li>
     *   <li>Close-delimited (connection close)</li>
     * </ol>
     *
     * @param transferEncoding the Transfer-Encoding header value, or null
     * @param contentLength    the Content-Length header value, or null
     * @return the body framing strategy
     */
    public static BodyFraming determineFraming(String transferEncoding, String contentLength) {
        if (transferEncoding != null && transferEncoding.toLowerCase().contains("chunked")) {
            return BodyFraming.CHUNKED;
        }
        if (contentLength != null) {
            return BodyFraming.CONTENT_LENGTH;
        }
        return BodyFraming.CLOSE_DELIMITED;
    }

    /**
     * Body framing strategies per RFC 7230 §3.3.3.
     *
     * @since 0.1.0
     */
    public enum BodyFraming {
        /** Body length determined by Transfer-Encoding: chunked. */
        CHUNKED,
        /** Body length determined by Content-Length header. */
        CONTENT_LENGTH,
        /** Body delimited by connection close. */
        CLOSE_DELIMITED
    }
}
