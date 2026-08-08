package ssg.legoflow.http3;

import java.nio.ByteBuffer;

/**
 * An HTTP/3 frame consisting of a type and payload.
 *
 * <p>HTTP/3 frames are simpler than HTTP/2 frames: they have no flags
 * or stream ID (the stream is implicit from the QUIC stream they
 * are carried on). Each frame is a variable-length integer type
 * followed by a variable-length integer length and the payload.</p>
 *
 * @param type    the frame type
 * @param payload the frame payload data
 * @since 0.1.0
 */
public record Http3Frame(Http3FrameType type, ByteBuffer payload) {

    /**
     * Creates an HTTP/3 frame with a read-only payload.
     *
     * @param type    the frame type
     * @param payload the frame payload (will be made read-only)
     * @since 0.1.0
     */
    public Http3Frame {
        payload = payload != null ? payload.asReadOnlyBuffer() : ByteBuffer.allocate(0).asReadOnlyBuffer();
    }

    /**
     * Returns the payload length in bytes.
     *
     * @return the number of remaining bytes in the payload
     * @since 0.1.0
     */
    public int payloadLength() {
        return payload.remaining();
    }

    /**
     * Creates a DATA frame.
     *
     * @param data the body data
     * @return a new DATA frame
     * @since 0.1.0
     */
    public static Http3Frame data(ByteBuffer data) {
        return new Http3Frame(Http3FrameType.DATA, data);
    }

    /**
     * Creates a HEADERS frame.
     *
     * @param headerBlock the encoded header block
     * @return a new HEADERS frame
     * @since 0.1.0
     */
    public static Http3Frame headers(ByteBuffer headerBlock) {
        return new Http3Frame(Http3FrameType.HEADERS, headerBlock);
    }

    /**
     * Creates a SETTINGS frame.
     *
     * @param settingsPayload the encoded settings
     * @return a new SETTINGS frame
     * @since 0.1.0
     */
    public static Http3Frame settings(ByteBuffer settingsPayload) {
        return new Http3Frame(Http3FrameType.SETTINGS, settingsPayload);
    }

    /**
     * Creates a GOAWAY frame.
     *
     * @param streamId the last stream ID as a variable-length integer payload
     * @return a new GOAWAY frame
     * @since 0.1.0
     */
    public static Http3Frame goaway(ByteBuffer streamId) {
        return new Http3Frame(Http3FrameType.GOAWAY, streamId);
    }

    /**
     * Creates a CANCEL_PUSH frame.
     *
     * @param pushId the push ID payload
     * @return a new CANCEL_PUSH frame
     * @since 0.1.0
     */
    public static Http3Frame cancelPush(ByteBuffer pushId) {
        return new Http3Frame(Http3FrameType.CANCEL_PUSH, pushId);
    }

    /**
     * Creates a PUSH_PROMISE frame.
     *
     * @param payload the push ID + encoded header block
     * @return a new PUSH_PROMISE frame
     * @since 0.1.0
     */
    public static Http3Frame pushPromise(ByteBuffer payload) {
        return new Http3Frame(Http3FrameType.PUSH_PROMISE, payload);
    }

    /**
     * Creates a MAX_PUSH_ID frame.
     *
     * @param pushIdPayload the maximum push ID payload
     * @return a new MAX_PUSH_ID frame
     * @since 0.1.0
     */
    public static Http3Frame maxPushId(ByteBuffer pushIdPayload) {
        return new Http3Frame(Http3FrameType.MAX_PUSH_ID, pushIdPayload);
    }
}
