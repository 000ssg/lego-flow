package ssg.legoflow.media.rtsp.server;

import ssg.legoflow.media.common.sdp.SessionDescription;
import java.util.Optional;
/**
 * Abstraction for a media source that can be streamed via RTSP.
 *
 * <p>Implementations provide SDP descriptions and control access to
 * the underlying media data for playback and recording.
 *
 * @since 0.1.0
 */
public interface MediaSource {

    /**
     * Returns the path that identifies this media source.
     *
     * @return the media path (e.g., "/live/stream1")
     */
    String path();

    /**
     * Returns the SDP description for this media source.
     *
     * @return the session description
     */
    SessionDescription describe();

    /**
     * Returns the total duration of the media in seconds, or empty for live streams.
     *
     * @return the duration, or empty for live/unbounded media
     */
    Optional<Double> duration();

    /**
     * Returns true if this source supports recording.
     *
     * @return true if recording is supported
     */
    default boolean supportsRecord() {
        return false;
    }

    /**
     * Returns true if this is a live (unbounded) stream.
     *
     * @return true for live streams
     */
    default boolean isLive() {
        return duration().isEmpty();
    }
}
