package ssg.legoflow.upnp.demo;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.mediarenderer.MediaRendererDevice;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.MediaServerDevice;
import java.time.Duration;
import java.util.List;
/**
 * Demo application: DLNA control point that discovers and controls media devices.
 *
 * <p>Discovers media servers and renderers, browses the server's content
 * library, selects a track, and sends it to a renderer for playback.
 * Demonstrates the full control point workflow.
 *
 * @since 0.1.0
 */
public class MediaControllerDemo {

    private final ControlPoint controlPoint;

    /**
     * Creates a new media controller demo.
     *
     * @since 0.1.0
     */
    public MediaControllerDemo() {
        this.controlPoint = new ControlPoint();
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
     * Registers a local media server for in-process discovery.
     *
     * @param server the server device
     * @since 0.1.0
     */
    public void registerServer(MediaServerDevice server) {
        controlPoint.registerLocalServer(server);
    }

    /**
     * Registers a local media renderer for in-process discovery.
     *
     * @param renderer the renderer device
     * @since 0.1.0
     */
    public void registerRenderer(MediaRendererDevice renderer) {
        controlPoint.registerLocalRenderer(renderer);
    }

    /**
     * Starts the control point and performs discovery.
     *
     * @since 0.1.0
     */
    public void start() {
        controlPoint.start();
    }

    /**
     * Stops the control point.
     *
     * @since 0.1.0
     */
    public void stop() {
        controlPoint.stop();
    }

    /**
     * Browses the root container of the first discovered server.
     *
     * @return the list of root items
     * @since 0.1.0
     */
    public List<ContentItem> browseServerLibrary() {
        List<MediaServerProxy> servers = controlPoint.discoverMediaServers();
        if (servers.isEmpty()) {
            return List.of();
        }
        return servers.getFirst().browseRoot();
    }

    /**
     * Browses a container on the first discovered server.
     *
     * @param containerId the container ID to browse
     * @return the list of child items
     * @since 0.1.0
     */
    public List<ContentItem> browseContainer(String containerId) {
        List<MediaServerProxy> servers = controlPoint.discoverMediaServers();
        if (servers.isEmpty()) {
            return List.of();
        }
        return servers.getFirst().browse(containerId);
    }

    /**
     * Plays a content item on the first discovered renderer.
     *
     * @param item the content item to play
     * @since 0.1.0
     */
    public void playOnRenderer(ContentItem item) {
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();
        if (renderers.isEmpty()) {
            throw new IllegalStateException("No media renderers available");
        }
        renderers.getFirst().playItem(item);
    }

    /**
     * Pauses playback on the first discovered renderer.
     *
     * @since 0.1.0
     */
    public void pause() {
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();
        if (!renderers.isEmpty()) {
            renderers.getFirst().pause();
        }
    }

    /**
     * Stops playback on the first discovered renderer.
     *
     * @since 0.1.0
     */
    public void stopPlayback() {
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();
        if (!renderers.isEmpty()) {
            renderers.getFirst().stop();
        }
    }

    /**
     * Seeks to a position on the first discovered renderer.
     *
     * @param position the seek position
     * @since 0.1.0
     */
    public void seek(Duration position) {
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();
        if (!renderers.isEmpty()) {
            renderers.getFirst().seek(position);
        }
    }

    /**
     * Sets the volume on the first discovered renderer.
     *
     * @param volume the desired volume (0-100)
     * @since 0.1.0
     */
    public void setVolume(int volume) {
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();
        if (!renderers.isEmpty()) {
            renderers.getFirst().setVolume(volume);
        }
    }

    /**
     * Returns the volume of the first discovered renderer.
     *
     * @return the volume level (0-100)
     * @since 0.1.0
     */
    public int getVolume() {
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();
        if (!renderers.isEmpty()) {
            return renderers.getFirst().getVolume();
        }
        return 0;
    }

    /**
     * Main entry point for running the demo standalone.
     *
     * @param args command-line arguments (unused)
     * @since 0.1.0
     */
    public static void main(String[] args) {
        var demo = new MediaControllerDemo();
        demo.start();
        System.out.println("Control Point started, discovering devices...");
    }
}
