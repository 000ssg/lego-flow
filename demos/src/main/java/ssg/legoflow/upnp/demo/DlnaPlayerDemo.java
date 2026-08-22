package ssg.legoflow.upnp.demo;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.mediarenderer.PlaybackEvent;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.ContentItemType;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * Full end-to-end DLNA playback demo.
 *
 * <p>Creates a media server, a media renderer, and a control point, then
 * demonstrates the complete DLNA workflow:
 * <ol>
 *   <li>Discovery — control point finds server and renderer</li>
 *   <li>Browse — navigate the server's content library</li>
 *   <li>Select — choose a content item</li>
 *   <li>Play — send to renderer for playback</li>
 *   <li>Control — pause, seek, resume, stop</li>
 *   <li>Events — subscribe to real-time state/position updates</li>
 * </ol>
 *
 * @since 0.1.0
 */
public class DlnaPlayerDemo {

    private final SimpleMediaServerDemo serverDemo;
    private final SimpleMediaRendererDemo rendererDemo;
    private final ControlPoint controlPoint;
    private final List<PlaybackEvent> receivedEvents = new CopyOnWriteArrayList<>();

    /**
     * Creates the full DLNA player demo with server, renderer, and control point.
     *
     * @since 0.1.0
     */
    public DlnaPlayerDemo() {
        this.serverDemo = new SimpleMediaServerDemo();
        this.rendererDemo = new SimpleMediaRendererDemo();
        this.controlPoint = new ControlPoint();
    }

    /**
     * Starts all components and registers them with the control point.
     *
     * @since 0.1.0
     */
    public void start() {
        serverDemo.start();
        rendererDemo.start();
        controlPoint.start();

        controlPoint.registerLocalServer(serverDemo.getServer());
        controlPoint.registerLocalRenderer(rendererDemo.getRenderer());
    }

    /**
     * Stops all components.
     *
     * @since 0.1.0
     */
    public void stop() {
        controlPoint.stop();
        rendererDemo.stop();
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
     * Returns the renderer demo.
     *
     * @return the renderer demo
     * @since 0.1.0
     */
    public SimpleMediaRendererDemo getRendererDemo() {
        return rendererDemo;
    }

    /**
     * Returns all received playback events.
     *
     * @return the list of events
     * @since 0.1.0
     */
    public List<PlaybackEvent> getReceivedEvents() {
        return receivedEvents;
    }

    /**
     * Subscribes to transport events on the renderer.
     *
     * @since 0.1.0
     */
    public void subscribeToEvents() {
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();
        if (!renderers.isEmpty()) {
            renderers.getFirst().subscribeTransportEvents(receivedEvents::add);
        }
    }

    /**
     * Runs the full demo lifecycle: discover, browse, select, play, control.
     *
     * @since 0.1.0
     */
    public void runFullLifecycle() {
        // Subscribe to events
        subscribeToEvents();

        // Discover
        List<MediaServerProxy> servers = controlPoint.discoverMediaServers();
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();

        if (servers.isEmpty() || renderers.isEmpty()) {
            System.out.println("No devices found");
            return;
        }

        MediaServerProxy server = servers.getFirst();
        MediaRendererProxy renderer = renderers.getFirst();

        // Browse root
        List<ContentItem> rootItems = server.browseRoot();
        System.out.println("Root items: " + rootItems.size());

        // Browse Music container
        List<ContentItem> musicItems = server.browse("1");
        System.out.println("Music items: " + musicItems.size());

        // Browse Album1
        List<ContentItem> albumItems = server.browse("10");
        System.out.println("Album items: " + albumItems.size());

        // Select first audio track
        ContentItem track = null;
        for (ContentItem item : albumItems) {
            if (item.getType() == ContentItemType.AUDIO_ITEM) {
                track = item;
                break;
            }
        }

        if (track == null) {
            // Fallback: get track by ID
            track = server.getContent("100");
        }

        if (track != null) {
            // Play
            renderer.playItem(track);
            System.out.println("Playing: " + track.getTitle());

            // Pause
            renderer.pause();
            System.out.println("Paused");

            // Seek
            renderer.seek(Duration.ofSeconds(30));
            System.out.println("Seeked to 0:00:30");

            // Resume
            renderer.play();
            System.out.println("Resumed");

            // Stop
            renderer.stop();
            System.out.println("Stopped");
        }
    }

    /**
     * Main entry point for running the demo standalone.
     *
     * @param args command-line arguments (unused)
     * @since 0.1.0
     */
    public static void main(String[] args) {
        var demo = new DlnaPlayerDemo();
        try {
            demo.start();
            demo.runFullLifecycle();
        } finally {
            demo.stop();
        }
    }
}
