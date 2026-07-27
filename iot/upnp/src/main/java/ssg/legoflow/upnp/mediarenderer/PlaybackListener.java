package ssg.legoflow.upnp.mediarenderer;

/**
 * Functional interface for receiving playback events from a media renderer.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface PlaybackListener {

    /**
     * Called when a playback event occurs.
     *
     * @param event the playback event
     * @since 1.0.0
     */
    void onPlaybackEvent(PlaybackEvent event);
}
