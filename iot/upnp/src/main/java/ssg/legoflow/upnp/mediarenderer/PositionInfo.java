package ssg.legoflow.upnp.mediarenderer;

import java.time.Duration;

/**
 * Position information from the AVTransport service.
 *
 * @param track         the current track number (1-based)
 * @param trackDuration the total duration of the current track
 * @param trackMetadata DIDL-Lite XML metadata for the current track
 * @param trackUri      the URI of the current track
 * @param relTime       the current position relative to track start
 * @param absTime       the absolute time position
 * @param relCount      the relative counter position
 * @param absCount      the absolute counter position
 * @since 0.1.0
 */
public record PositionInfo(
        int track,
        Duration trackDuration,
        String trackMetadata,
        String trackUri,
        Duration relTime,
        Duration absTime,
        int relCount,
        int absCount
) {

    /**
     * Creates a default position info with no media loaded.
     *
     * @return empty position info
     * @since 0.1.0
     */
    public static PositionInfo empty() {
        return new PositionInfo(0, Duration.ZERO, "", "", Duration.ZERO, Duration.ZERO, 0, 0);
    }
}
