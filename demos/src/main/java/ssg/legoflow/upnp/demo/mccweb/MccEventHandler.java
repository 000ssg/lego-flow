package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.http.core.HttpContext;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.DeviceListener;
import ssg.legoflow.upnp.controlpoint.DeviceProxy;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.mediarenderer.PlaybackEvent;
import ssg.legoflow.upnp.mediarenderer.PlaybackListener;
import ssg.legoflow.upnp.mediaserver.ContentItem;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles Server-Sent Events (SSE) for real-time updates in the Media Control Center.
 *
 * <p>Manages SSE connections and pushes events for device discovery changes,
 * transport state changes, position updates, and volume changes. Integrates
 * with {@link DeviceListener} and {@link PlaybackListener} for event sources.
 *
 * <p>Since the lego-flow HTTP server processes requests synchronously and
 * returns responses, this handler collects recent events and returns them
 * as an SSE-formatted response. Clients poll this endpoint for updates.
 *
 * @since 1.0.0
 */
public class MccEventHandler implements DeviceListener, PlaybackListener, AutoCloseable {

    private final ControlPoint controlPoint;
    private final List<String> eventBuffer = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> positionPollingTask;
    private static final int MAX_BUFFER_SIZE = 100;

    /**
     * Creates a new event handler and registers listeners.
     *
     * @param controlPoint the UPnP control point
     * @since 1.0.0
     */
    public MccEventHandler(ControlPoint controlPoint) {
        this.controlPoint = Objects.requireNonNull(controlPoint, "controlPoint must not be null");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = Thread.ofVirtual().unstarted(r);
            t.setName("mcc-event-poller");
            return t;
        });
        controlPoint.addDeviceListener(this);
        startPositionPolling();
    }

    /**
     * Handles GET /api/events - returns buffered SSE events.
     *
     * <p>Returns all accumulated events since the last call, formatted as
     * Server-Sent Events. Each event has the format:
     * {@code data: {"type": "...", ...}\n\n}
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with SSE-formatted event data
     * @since 1.0.0
     */
    public HttpResponse getEvents(HttpContext ctx, HttpRequest request) {
        var sb = new StringBuilder();
        var events = List.copyOf(eventBuffer);
        eventBuffer.clear();

        for (String event : events) {
            sb.append("data: ").append(event).append("\n\n");
        }

        // Always send a heartbeat comment to keep connection alive
        if (events.isEmpty()) {
            sb.append(": heartbeat\n\n");
        }

        HttpResponse response = HttpResponse.of(HttpStatus.OK, sb.toString());
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/event-stream");
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.getHeaders().set(HttpHeaders.CONNECTION, "keep-alive");
        return response;
    }

    @Override
    public void onDeviceAdded(DeviceProxy device) {
        var sb = new StringBuilder();
        sb.append("{\"type\":\"deviceAdded\",");
        sb.append("\"udn\":\"").append(escapeJson(device.getUdn())).append("\",");
        sb.append("\"friendlyName\":\"").append(escapeJson(device.getFriendlyName())).append("\",");
        sb.append("\"deviceType\":\"").append(escapeJson(device.getDeviceType())).append("\"}");
        addEvent(sb.toString());
    }

    @Override
    public void onDeviceRemoved(DeviceProxy device) {
        var sb = new StringBuilder();
        sb.append("{\"type\":\"deviceRemoved\",");
        sb.append("\"udn\":\"").append(escapeJson(device.getUdn())).append("\",");
        sb.append("\"friendlyName\":\"").append(escapeJson(device.getFriendlyName())).append("\"}");
        addEvent(sb.toString());
    }

    @Override
    public void onPlaybackEvent(PlaybackEvent event) {
        switch (event) {
            case PlaybackEvent.PlayStarted e -> {
                addEvent("{\"type\":\"transportState\",\"state\":\"PLAYING\",\"uri\":\""
                        + escapeJson(e.uri()) + "\"}");
            }
            case PlaybackEvent.PlayPaused e -> {
                addEvent("{\"type\":\"transportState\",\"state\":\"PAUSED_PLAYBACK\",\"position\":\""
                        + ContentItem.formatDuration(e.position()) + "\"}");
            }
            case PlaybackEvent.PlayStopped _ -> {
                addEvent("{\"type\":\"transportState\",\"state\":\"STOPPED\"}");
            }
            case PlaybackEvent.PlayCompleted _ -> {
                addEvent("{\"type\":\"transportState\",\"state\":\"STOPPED\",\"completed\":true}");
            }
            case PlaybackEvent.PositionChanged e -> {
                addEvent("{\"type\":\"positionChanged\",\"position\":\""
                        + ContentItem.formatDuration(e.position())
                        + "\",\"duration\":\"" + ContentItem.formatDuration(e.duration()) + "\"}");
            }
            case PlaybackEvent.VolumeChanged e -> {
                addEvent("{\"type\":\"volumeChanged\",\"volume\":" + e.volume()
                        + ",\"muted\":" + e.muted() + "}");
            }
        }
    }

    /**
     * Subscribes this handler to playback events from a renderer.
     *
     * @param renderer the media renderer proxy
     * @since 1.0.0
     */
    public void subscribeRenderer(MediaRendererProxy renderer) {
        renderer.subscribeTransportEvents(this);
    }

    /**
     * Returns the number of buffered events.
     *
     * @return the event count
     * @since 1.0.0
     */
    public int getBufferedEventCount() {
        return eventBuffer.size();
    }

    @Override
    public void close() {
        if (positionPollingTask != null) {
            positionPollingTask.cancel(true);
        }
        scheduler.shutdown();
        controlPoint.removeDeviceListener(this);
    }

    private void startPositionPolling() {
        positionPollingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                for (var renderer : controlPoint.discoverMediaRenderers()) {
                    var state = renderer.getTransportState();
                    if (state == ssg.legoflow.upnp.mediarenderer.TransportState.PLAYING) {
                        var position = renderer.getPosition();
                        var sb = new StringBuilder();
                        sb.append("{\"type\":\"positionChanged\",");
                        sb.append("\"udn\":\"").append(escapeJson(renderer.getUdn())).append("\",");
                        sb.append("\"relTime\":\"")
                                .append(ContentItem.formatDuration(position.relTime())).append("\",");
                        sb.append("\"trackDuration\":\"")
                                .append(ContentItem.formatDuration(position.trackDuration()))
                                .append("\"}");
                        addEvent(sb.toString());
                    }
                }
            } catch (Exception e) {
                // Swallow polling exceptions
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void addEvent(String event) {
        eventBuffer.add(event);
        // Trim buffer if too large
        while (eventBuffer.size() > MAX_BUFFER_SIZE) {
            eventBuffer.removeFirst();
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
