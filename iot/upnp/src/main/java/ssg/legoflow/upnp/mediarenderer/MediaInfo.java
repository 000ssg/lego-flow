package ssg.legoflow.upnp.mediarenderer;

import java.time.Duration;

/**
 * Media information from the AVTransport service.
 *
 * @param nrTracks            the number of tracks in the current media
 * @param mediaDuration       the total duration of the media
 * @param currentUri          the URI of the current media
 * @param currentUriMetadata  DIDL-Lite XML metadata for the current media
 * @param nextUri             the URI of the next media (if set)
 * @param nextUriMetadata     DIDL-Lite metadata for the next media
 * @param playMedium          the play medium (e.g. "NETWORK")
 * @param recordMedium        the record medium (e.g. "NOT_IMPLEMENTED")
 * @param writeStatus         the write status (e.g. "NOT_IMPLEMENTED")
 * @since 1.0.0
 */
public record MediaInfo(
        int nrTracks,
        Duration mediaDuration,
        String currentUri,
        String currentUriMetadata,
        String nextUri,
        String nextUriMetadata,
        String playMedium,
        String recordMedium,
        String writeStatus
) {

    /**
     * Creates a default media info with no media loaded.
     *
     * @return empty media info
     * @since 1.0.0
     */
    public static MediaInfo empty() {
        return new MediaInfo(0, Duration.ZERO, "", "", "", "",
                "NONE", "NOT_IMPLEMENTED", "NOT_IMPLEMENTED");
    }
}
