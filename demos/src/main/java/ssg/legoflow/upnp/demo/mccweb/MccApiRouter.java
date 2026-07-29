package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.http.core.HttpContext;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.server.HttpRouter;
import ssg.legoflow.upnp.controlpoint.ControlPoint;

import java.util.Objects;

/**
 * REST API route configuration for the Media Control Center.
 *
 * <p>Sets up all REST endpoints for device discovery, content browsing,
 * playback control, volume control, and server-sent events. Routes are
 * registered on an {@link HttpRouter} for dispatch by the HTTP server.
 *
 * <p>Since the lego-flow {@link HttpRouter} uses exact path matching,
 * dynamic path segments (e.g. {@code /api/devices/{udn}}) are handled
 * by registering a catch-all handler on the default route and dispatching
 * internally based on URI prefix matching.
 *
 * @since 1.0.0
 */
public class MccApiRouter {

    private final ControlPoint controlPoint;
    private final MccDeviceHandler deviceHandler;
    private final MccBrowseHandler browseHandler;
    private final MccPlaybackHandler playbackHandler;
    private final MccVolumeHandler volumeHandler;
    private final MccMediaProxyHandler mediaProxyHandler;
    private final MccEventHandler eventHandler;
    private final MccReactApp reactApp;

    /**
     * Creates a new API router with all handlers.
     *
     * @param controlPoint the UPnP control point
     * @since 1.0.0
     */
    public MccApiRouter(ControlPoint controlPoint) {
        this.controlPoint = Objects.requireNonNull(controlPoint, "controlPoint must not be null");
        this.deviceHandler = new MccDeviceHandler(controlPoint);
        this.browseHandler = new MccBrowseHandler(controlPoint);
        this.playbackHandler = new MccPlaybackHandler(controlPoint);
        this.volumeHandler = new MccVolumeHandler(controlPoint);
        this.mediaProxyHandler = new MccMediaProxyHandler(controlPoint);
        this.eventHandler = new MccEventHandler(controlPoint);
        this.reactApp = new MccReactApp();
    }

    /**
     * Returns the event handler for subscribing to renderer events.
     *
     * @return the event handler
     * @since 1.0.0
     */
    public MccEventHandler getEventHandler() {
        return eventHandler;
    }

    /**
     * Configures all routes on the given HTTP router.
     *
     * <p>Registers exact-match routes for static paths and sets a
     * default handler for dynamic path segments.
     *
     * @param router the HTTP router to configure
     * @since 1.0.0
     */
    public void configureRoutes(HttpRouter router) {
        // Static SPA routes
        router.get("/", reactApp::serveIndex);
        router.get("/app.js", reactApp::serveAppJs);
        router.get("/app.css", reactApp::serveAppCss);

        // Device discovery endpoints (exact paths)
        router.get("/api/devices", deviceHandler::listDevices);
        router.get("/api/devices/servers", deviceHandler::listServers);
        router.get("/api/devices/renderers", deviceHandler::listRenderers);
        router.get("/api/devices/unrecognized", deviceHandler::listFailedDevices);
        router.post("/api/devices/refresh", deviceHandler::refreshDevices);

        // Diagnostics / logging endpoints
        router.get("/api/log", this::getLog);
        router.post("/api/log/enable", this::enableLog);
        router.post("/api/log/disable", this::disableLog);
        router.post("/api/log/clear", this::clearLog);

        // Media proxy endpoint
        router.get("/api/media/stream", mediaProxyHandler::streamByUrl);

        // SSE events endpoint
        router.get("/api/events", eventHandler::getEvents);

        // Set default handler for dynamic routes (paths with UDN segments)
        router.setDefaultHandler(this::dispatchDynamic);
    }

    /**
     * Dispatches dynamic routes that contain path parameters (e.g. UDN).
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response
     * @since 1.0.0
     */
    private HttpResponse dispatchDynamic(HttpContext ctx, HttpRequest request) {
        String path = request.getUri().contains("?")
                ? request.getUri().substring(0, request.getUri().indexOf('?'))
                : request.getUri();
        HttpMethod method = request.getMethod();

        // Add CORS headers for all responses
        HttpResponse response = doDispatch(path, method, ctx, request);
        addCorsHeaders(response);
        return response;
    }

    private HttpResponse doDispatch(String path, HttpMethod method,
                                     HttpContext ctx, HttpRequest request) {
        // OPTIONS for CORS preflight
        if (method == HttpMethod.OPTIONS) {
            HttpResponse response = HttpResponse.of(HttpStatus.NO_CONTENT);
            addCorsHeaders(response);
            return response;
        }

        // Device detail: GET /api/devices/{udn}
        if (method == HttpMethod.GET && path.startsWith("/api/devices/")
                && !path.equals("/api/devices/servers")
                && !path.equals("/api/devices/renderers")
                && !path.equals("/api/devices/unrecognized")) {
            return deviceHandler.getDevice(ctx, request);
        }

        // Server browse/search/content/stream
        if (path.startsWith("/api/servers/")) {
            if (method == HttpMethod.GET && path.contains("/stream/")) {
                return mediaProxyHandler.streamByItem(ctx, request);
            }
            if (method == HttpMethod.GET && path.contains("/browse/root")) {
                return browseHandler.browseRoot(ctx, request);
            }
            if (method == HttpMethod.GET && path.contains("/browse")) {
                return browseHandler.browse(ctx, request);
            }
            if (method == HttpMethod.GET && path.contains("/search")) {
                return browseHandler.search(ctx, request);
            }
            if (method == HttpMethod.GET && path.contains("/content/")) {
                return browseHandler.getContent(ctx, request);
            }
        }

        // Renderer playback control
        if (path.startsWith("/api/renderers/")) {
            if (method == HttpMethod.POST) {
                if (path.endsWith("/play")) return playbackHandler.play(ctx, request);
                if (path.endsWith("/pause")) return playbackHandler.pause(ctx, request);
                if (path.endsWith("/stop")) return playbackHandler.stop(ctx, request);
                if (path.endsWith("/seek")) return playbackHandler.seek(ctx, request);
                if (path.endsWith("/next")) return playbackHandler.next(ctx, request);
                if (path.endsWith("/previous")) return playbackHandler.previous(ctx, request);
            }
            if (method == HttpMethod.GET) {
                if (path.endsWith("/transport")) return playbackHandler.getTransportInfo(ctx, request);
                if (path.endsWith("/position")) return playbackHandler.getPositionInfo(ctx, request);
                if (path.endsWith("/volume")) return volumeHandler.getVolume(ctx, request);
            }
            if (method == HttpMethod.PUT) {
                if (path.endsWith("/volume")) return volumeHandler.setVolume(ctx, request);
                if (path.endsWith("/mute")) return volumeHandler.setMute(ctx, request);
            }
        }

        // Not found
        return MccDeviceHandler.jsonResponse(HttpStatus.NOT_FOUND,
                MccJsonSerializer.errorToJson("Not found: " + path));
    }

    // --- Logging endpoints ---

    private HttpResponse getLog(HttpContext ctx, HttpRequest request) {
        var log = controlPoint.getMessageLog();
        var entries = log.getEntries();
        var sb = new StringBuilder();
        sb.append("{\"enabled\":").append(log.isEnabled());
        sb.append(",\"count\":").append(entries.size());
        sb.append(",\"entries\":[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(',');
            var e = entries.get(i);
            sb.append("{\"time\":\"").append(e.timestamp()).append('"');
            sb.append(",\"dir\":\"").append(e.direction()).append('"');
            sb.append(",\"proto\":\"").append(e.protocol()).append('"');
            sb.append(",\"summary\":\"").append(jsonEscape(e.summary())).append('"');
            if (e.body() != null) {
                sb.append(",\"body\":\"").append(jsonEscape(e.body())).append('"');
            }
            sb.append('}');
        }
        sb.append("]}");
        return MccDeviceHandler.jsonResponse(HttpStatus.OK, sb.toString());
    }

    private HttpResponse enableLog(HttpContext ctx, HttpRequest request) {
        controlPoint.getMessageLog().setEnabled(true);
        return MccDeviceHandler.jsonResponse(HttpStatus.OK,
                MccJsonSerializer.messageToJson("UPnP logging enabled"));
    }

    private HttpResponse disableLog(HttpContext ctx, HttpRequest request) {
        controlPoint.getMessageLog().setEnabled(false);
        return MccDeviceHandler.jsonResponse(HttpStatus.OK,
                MccJsonSerializer.messageToJson("UPnP logging disabled"));
    }

    private HttpResponse clearLog(HttpContext ctx, HttpRequest request) {
        controlPoint.getMessageLog().clear();
        return MccDeviceHandler.jsonResponse(HttpStatus.OK,
                MccJsonSerializer.messageToJson("Log cleared"));
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Adds CORS headers to a response for development mode.
     *
     * @param response the HTTP response
     * @since 1.0.0
     */
    static void addCorsHeaders(HttpResponse response) {
        response.getHeaders().set("access-control-allow-origin", "*");
        response.getHeaders().set("access-control-allow-methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.getHeaders().set("access-control-allow-headers", "Content-Type, Accept, Range");
        response.getHeaders().set("access-control-expose-headers", "Content-Range, Accept-Ranges, Content-Length, Content-Type");
        response.getHeaders().set("access-control-max-age", "86400");
    }

    /**
     * Closes resources held by this router (event handler scheduler).
     *
     * @since 1.0.0
     */
    public void close() {
        eventHandler.close();
    }
}
