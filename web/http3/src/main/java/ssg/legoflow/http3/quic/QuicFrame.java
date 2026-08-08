package ssg.legoflow.http3.quic;

import java.nio.ByteBuffer;

/**
 * Represents a single QUIC frame within a packet.
 *
 * <p>A QUIC packet may contain one or more frames. Each frame carries
 * a type, an optional stream identifier, a payload, an offset within
 * the stream, and a FIN flag indicating whether this is the last
 * frame for the stream.</p>
 *
 * @param type    the frame type
 * @param streamId the stream identifier (0 for connection-level frames)
 * @param payload  the frame payload data
 * @param offset   the byte offset within the stream
 * @param fin      {@code true} if this frame ends the stream
 * @since 0.1.0
 */
public record QuicFrame(
        QuicFrameType type,
        long streamId,
        ByteBuffer payload,
        long offset,
        boolean fin
) {

    /**
     * Creates a connection-level frame with no stream context.
     *
     * @param type    the frame type
     * @param payload the frame payload
     * @return a new {@code QuicFrame} with streamId 0, offset 0, and fin false
     * @since 0.1.0
     */
    public static QuicFrame connectionFrame(QuicFrameType type, ByteBuffer payload) {
        return new QuicFrame(type, 0, payload, 0, false);
    }

    /**
     * Creates a stream data frame.
     *
     * @param streamId the stream identifier
     * @param payload  the data payload
     * @param offset   the byte offset in the stream
     * @param fin      {@code true} if this is the last frame for the stream
     * @return a new {@code QuicFrame} of type {@link QuicFrameType#STREAM}
     * @since 0.1.0
     */
    public static QuicFrame streamFrame(long streamId, ByteBuffer payload, long offset, boolean fin) {
        return new QuicFrame(QuicFrameType.STREAM, streamId, payload, offset, fin);
    }
}
