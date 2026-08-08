package ssg.legoflow.upnp.demo;

import ssg.legoflow.upnp.mediarenderer.MediaRendererDevice;
import ssg.legoflow.upnp.mediarenderer.PlaybackEvent;
import ssg.legoflow.upnp.mediarenderer.PlaybackListener;

/**
 * Demo application: UPnP Media Renderer that simulates playback.
 *
 * <p>Creates a media renderer device that logs all playback events
 * (play, pause, stop, seek, volume changes) to demonstrate the
 * AVTransport and RenderingControl services.
 *
 * @since 0.1.0
 */
public class SimpleMediaRendererDemo {

    private final MediaRendererDevice renderer;
    private final LoggingPlaybackListener loggingListener;

    /**
     * Creates and initializes the demo media renderer.
     *
     * @since 0.1.0
     */
    public SimpleMediaRendererDemo() {
        this("Lego Flow Demo Renderer");
    }

    /**
     * Creates a demo media renderer with a custom name.
     *
     * @param name the friendly name
     * @since 0.1.0
     */
    public SimpleMediaRendererDemo(String name) {
        renderer = new MediaRendererDevice(name);
        renderer.setHttpPort(8300);
        renderer.setHostAddress("127.0.0.1");
        loggingListener = new LoggingPlaybackListener(name);
        renderer.addPlaybackListener(loggingListener);
    }

    /**
     * Returns the media renderer device.
     *
     * @return the renderer device
     * @since 0.1.0
     */
    public MediaRendererDevice getRenderer() {
        return renderer;
    }

    /**
     * Returns the logging listener for verifying events in tests.
     *
     * @return the logging listener
     * @since 0.1.0
     */
    public LoggingPlaybackListener getLoggingListener() {
        return loggingListener;
    }

    /**
     * Starts the demo media renderer.
     *
     * @since 0.1.0
     */
    public void start() {
        renderer.start();
    }

    /**
     * Stops the demo media renderer.
     *
     * @since 0.1.0
     */
    public void stop() {
        renderer.stop();
    }

    /**
     * Playback listener that logs events and records them for testing.
     *
     * @since 0.1.0
     */
    public static class LoggingPlaybackListener implements PlaybackListener {

        private final String deviceName;
        private final java.util.List<PlaybackEvent> events =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        /**
         * Creates a new logging listener.
         *
         * @param deviceName the device name for log messages
         * @since 0.1.0
         */
        public LoggingPlaybackListener(String deviceName) {
            this.deviceName = deviceName;
        }

        @Override
        public void onPlaybackEvent(PlaybackEvent event) {
            events.add(event);
            switch (event) {
                case PlaybackEvent.PlayStarted e ->
                        System.out.println("[" + deviceName + "] Play started: " + e.uri());
                case PlaybackEvent.PlayPaused e ->
                        System.out.println("[" + deviceName + "] Paused at: " + e.position());
                case PlaybackEvent.PlayStopped e ->
                        System.out.println("[" + deviceName + "] Stopped");
                case PlaybackEvent.PlayCompleted e ->
                        System.out.println("[" + deviceName + "] Playback completed");
                case PlaybackEvent.PositionChanged e ->
                        System.out.println("[" + deviceName + "] Position: " + e.position()
                                + " / " + e.duration());
                case PlaybackEvent.VolumeChanged e ->
                        System.out.println("[" + deviceName + "] Volume: " + e.volume()
                                + " muted: " + e.muted());
            }
        }

        /**
         * Returns all recorded events.
         *
         * @return the list of events
         * @since 0.1.0
         */
        public java.util.List<PlaybackEvent> getEvents() {
            return java.util.Collections.unmodifiableList(events);
        }

        /**
         * Clears recorded events.
         *
         * @since 0.1.0
         */
        public void clear() {
            events.clear();
        }
    }

    /**
     * Main entry point for running the demo standalone.
     *
     * @param args command-line arguments (unused)
     * @since 0.1.0
     */
    public static void main(String[] args) {
        var demo = new SimpleMediaRendererDemo();
        demo.start();
        System.out.println("Media Renderer started: " + demo.getRenderer().getFriendlyName());
    }
}
