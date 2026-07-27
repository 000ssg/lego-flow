package ssg.legoflow.upnp.mediarenderer;

import java.time.Duration;

/**
 * Sealed interface representing playback events from a media renderer.
 *
 * <p>Each event corresponds to a state change in the renderer's playback
 * lifecycle, from starting playback to completion.
 *
 * @since 1.0.0
 */
public sealed interface PlaybackEvent {

    /**
     * Playback has started for the given URI.
     *
     * @param uri      the media URI being played
     * @param metadata the DIDL-Lite metadata for the media
     * @since 1.0.0
     */
    record PlayStarted(String uri, String metadata) implements PlaybackEvent {
    }

    /**
     * Playback has been paused at the given position.
     *
     * @param position the position at which playback was paused
     * @since 1.0.0
     */
    record PlayPaused(Duration position) implements PlaybackEvent {
    }

    /**
     * Playback has been stopped.
     *
     * @since 1.0.0
     */
    record PlayStopped() implements PlaybackEvent {
    }

    /**
     * Playback has completed (reached end of media).
     *
     * @since 1.0.0
     */
    record PlayCompleted() implements PlaybackEvent {
    }

    /**
     * The playback position has changed.
     *
     * @param position the current position
     * @param duration the total duration of the media
     * @since 1.0.0
     */
    record PositionChanged(Duration position, Duration duration) implements PlaybackEvent {
    }

    /**
     * The volume or mute state has changed.
     *
     * @param volume the current volume level (0-100)
     * @param muted  whether audio is muted
     * @since 1.0.0
     */
    record VolumeChanged(int volume, boolean muted) implements PlaybackEvent {
    }
}
