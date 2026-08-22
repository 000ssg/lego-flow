package ssg.legoflow.upnp.demo;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import java.util.ArrayList;
import java.util.List;
/**
 * Demo application: multi-room audio playback.
 *
 * <p>Creates one media server with a music library and multiple media
 * renderers representing different rooms (living room, bedroom, kitchen).
 * A control point plays the same track on all renderers simultaneously.
 *
 * @since 0.1.0
 */
public class MultiRoomDemo {

    private final SimpleMediaServerDemo serverDemo;
    private final List<SimpleMediaRendererDemo> rendererDemos = new ArrayList<>();
    private final ControlPoint controlPoint;

    /**
     * Creates the multi-room demo with one server and three renderers.
     *
     * @since 0.1.0
     */
    public MultiRoomDemo() {
        this.serverDemo = new SimpleMediaServerDemo();
        this.controlPoint = new ControlPoint();

        rendererDemos.add(new SimpleMediaRendererDemo("Living Room"));
        rendererDemos.add(new SimpleMediaRendererDemo("Bedroom"));
        rendererDemos.add(new SimpleMediaRendererDemo("Kitchen"));
    }

    /**
     * Starts all components.
     *
     * @since 0.1.0
     */
    public void start() {
        serverDemo.start();
        controlPoint.start();
        controlPoint.registerLocalServer(serverDemo.getServer());

        for (SimpleMediaRendererDemo rendererDemo : rendererDemos) {
            rendererDemo.start();
            controlPoint.registerLocalRenderer(rendererDemo.getRenderer());
        }
    }

    /**
     * Stops all components.
     *
     * @since 0.1.0
     */
    public void stop() {
        controlPoint.stop();
        for (SimpleMediaRendererDemo rendererDemo : rendererDemos) {
            rendererDemo.stop();
        }
        serverDemo.stop();
    }

    /**
     * Returns the control point.
     *
     * @return the control point
     * @since 0.1.0
     */
    public ControlPoint getControlPoint() {
        return controlPoint;
    }

    /**
     * Returns the server demo.
     *
     * @return the server demo
     * @since 0.1.0
     */
    public SimpleMediaServerDemo getServerDemo() {
        return serverDemo;
    }

    /**
     * Returns the list of renderer demos.
     *
     * @return the renderer demos
     * @since 0.1.0
     */
    public List<SimpleMediaRendererDemo> getRendererDemos() {
        return rendererDemos;
    }

    /**
     * Plays a content item on all renderers simultaneously.
     *
     * @param item the content item to play
     * @since 0.1.0
     */
    public void playOnAllRenderers(ContentItem item) {
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();
        for (MediaRendererProxy renderer : renderers) {
            renderer.playItem(item);
        }
    }

    /**
     * Stops playback on all renderers.
     *
     * @since 0.1.0
     */
    public void stopAll() {
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();
        for (MediaRendererProxy renderer : renderers) {
            renderer.stop();
        }
    }

    /**
     * Sets volume on a specific renderer.
     *
     * @param rendererIndex the index of the renderer (0=Living Room, 1=Bedroom, 2=Kitchen)
     * @param volume        the desired volume (0-100)
     * @since 0.1.0
     */
    public void setVolume(int rendererIndex, int volume) {
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();
        if (rendererIndex >= 0 && rendererIndex < renderers.size()) {
            renderers.get(rendererIndex).setVolume(volume);
        }
    }

    /**
     * Main entry point for running the demo standalone.
     *
     * @param args command-line arguments (unused)
     * @since 0.1.0
     */
    public static void main(String[] args) {
        var demo = new MultiRoomDemo();
        try {
            demo.start();

            // Get first audio track from server
            List<MediaServerProxy> servers = demo.getControlPoint().discoverMediaServers();
            if (!servers.isEmpty()) {
                List<ContentItem> tracks = servers.getFirst().browse("10"); // Album1
                if (!tracks.isEmpty()) {
                    System.out.println("Playing '" + tracks.getFirst().getTitle() + "' on all rooms");
                    demo.playOnAllRenderers(tracks.getFirst());
                }
            }
        } finally {
            demo.stop();
        }
    }
}
