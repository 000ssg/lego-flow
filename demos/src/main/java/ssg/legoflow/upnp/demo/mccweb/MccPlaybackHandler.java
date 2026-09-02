package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.http.core.HttpContext;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.dlna.DlnaProtocolInfo;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.ContentItemType;
import java.time.Duration;
import java.util.Objects;
import static ssg.legoflow.upnp.demo.mccweb.MccDeviceHandler.jsonResponse;
/**
 * Handles playback control REST API requests for the Media Control Center.
 *
 * <p>Provides endpoints for play, pause, stop, seek, next, previous,
 * and retrieving transport and position information from media renderers.
 *
 * @since 0.1.0
 */
public class MccPlaybackHandler {

    private final ControlPoint controlPoint;

    /**
     * Creates a new playback handler.
     *
     * @param controlPoint the UPnP control point
     * @since 0.1.0
     */
    public MccPlaybackHandler(ControlPoint controlPoint) {
        this.controlPoint = Objects.requireNonNull(controlPoint, "controlPoint must not be null");
    }

    /**
     * Handles POST /api/renderers/{udn}/play - starts or resumes playback.
     *
     * <p>If the request body contains an itemUri, sets the transport URI first.
     * Otherwise resumes current playback.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with transport state JSON
     * @since 0.1.0
     */
    public HttpResponse play(HttpContext ctx, HttpRequest request) {
        String udn = extractRendererUdn(request.getUri(), "/play");
        MediaRendererProxy renderer = findRenderer(udn);
        if (renderer == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Renderer not found: " + udn));
        }

        try {
            String body = request.getBodyAsString();
            var playRequest = MccJsonSerializer.parsePlayRequest(body);
            if (playRequest.uri() != null && !playRequest.uri().isEmpty()) {
                // Build a content item with proper metadata for the renderer
                String uri = playRequest.uri();
                String mimeType = MediaRendererProxy.inferMimeType(uri);
                ContentItemType itemType = guessItemType(mimeType);
                String title = extractTitle(uri);

                var tempItem = new ContentItem("temp", "0", title, itemType);
                try {
                    tempItem.setResourceUrl(java.net.URI.create(uri).toURL());
                } catch (Exception e) {
                    // If URL is malformed, try direct play
                }

                // Set protocol info from MIME type so DIDL-Lite metadata includes it
                if (mimeType != null) {
                    tempItem.setProtocolInfo(DlnaProtocolInfo.httpGetSimple(mimeType));
                }

                // If the play request includes pre-built metadata, use it directly
                // (e.g., when playing items browsed from a media server)
                renderer.playItem(tempItem);
            } else {
                renderer.play();
            }
            return transportStateResponse(renderer);
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Play failed: " + e.getMessage()));
        }
    }

    /**
     * Handles POST /api/renderers/{udn}/pause - pauses playback.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with transport state JSON
     * @since 0.1.0
     */
    public HttpResponse pause(HttpContext ctx, HttpRequest request) {
        String udn = extractRendererUdn(request.getUri(), "/pause");
        MediaRendererProxy renderer = findRenderer(udn);
        if (renderer == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Renderer not found: " + udn));
        }

        try {
            renderer.pause();
            return transportStateResponse(renderer);
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Pause failed: " + e.getMessage()));
        }
    }

    /**
     * Handles POST /api/renderers/{udn}/stop - stops playback.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with transport state JSON
     * @since 0.1.0
     */
    public HttpResponse stop(HttpContext ctx, HttpRequest request) {
        String udn = extractRendererUdn(request.getUri(), "/stop");
        MediaRendererProxy renderer = findRenderer(udn);
        if (renderer == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Renderer not found: " + udn));
        }

        try {
            renderer.stop();
            return transportStateResponse(renderer);
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Stop failed: " + e.getMessage()));
        }
    }

    /**
     * Handles POST /api/renderers/{udn}/seek - seeks to a position.
     *
     * <p>Request body: {"position": "0:02:30"}
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with transport state JSON
     * @since 0.1.0
     */
    public HttpResponse seek(HttpContext ctx, HttpRequest request) {
        String udn = extractRendererUdn(request.getUri(), "/seek");
        MediaRendererProxy renderer = findRenderer(udn);
        if (renderer == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Renderer not found: " + udn));
        }

        try {
            String body = request.getBodyAsString();
            Duration position = MccJsonSerializer.parseSeekRequest(body);
            renderer.seek(position);
            return transportStateResponse(renderer);
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Seek failed: " + e.getMessage()));
        }
    }

    /**
     * Handles POST /api/renderers/{udn}/next - advances to next track.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with transport state JSON
     * @since 0.1.0
     */
    public HttpResponse next(HttpContext ctx, HttpRequest request) {
        String udn = extractRendererUdn(request.getUri(), "/next");
        MediaRendererProxy renderer = findRenderer(udn);
        if (renderer == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Renderer not found: " + udn));
        }

        try {
            renderer.next();
            return transportStateResponse(renderer);
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Next failed: " + e.getMessage()));
        }
    }

    /**
     * Handles POST /api/renderers/{udn}/previous - returns to previous track.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with transport state JSON
     * @since 0.1.0
     */
    public HttpResponse previous(HttpContext ctx, HttpRequest request) {
        String udn = extractRendererUdn(request.getUri(), "/previous");
        MediaRendererProxy renderer = findRenderer(udn);
        if (renderer == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Renderer not found: " + udn));
        }

        try {
            renderer.previous();
            return transportStateResponse(renderer);
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Previous failed: " + e.getMessage()));
        }
    }

    /**
     * Handles GET /api/renderers/{udn}/transport - gets transport state and position info.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with transport and position JSON
     * @since 0.1.0
     */
    public HttpResponse getTransportInfo(HttpContext ctx, HttpRequest request) {
        String udn = extractRendererUdn(request.getUri(), "/transport");
        MediaRendererProxy renderer = findRenderer(udn);
        if (renderer == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Renderer not found: " + udn));
        }

        try {
            return transportStateResponse(renderer);
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Get transport failed: " + e.getMessage()));
        }
    }

    /**
     * Handles GET /api/renderers/{udn}/position - gets current position only.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with position JSON
     * @since 0.1.0
     */
    public HttpResponse getPositionInfo(HttpContext ctx, HttpRequest request) {
        String udn = extractRendererUdn(request.getUri(), "/position");
        MediaRendererProxy renderer = findRenderer(udn);
        if (renderer == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Renderer not found: " + udn));
        }

        try {
            var position = renderer.getPosition();
            return jsonResponse(HttpStatus.OK, MccJsonSerializer.positionInfoToJson(position));
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Get position failed: " + e.getMessage()));
        }
    }

    private MediaRendererProxy findRenderer(String udn) {
        if (udn == null) return null;
        for (var renderer : controlPoint.discoverMediaRenderers()) {
            if (renderer.getUdn().equals(udn)) {
                return renderer;
            }
        }
        return null;
    }

    private HttpResponse transportStateResponse(MediaRendererProxy renderer) {
        var transportInfo = ssg.legoflow.upnp.mediarenderer.TransportInfo.of(
                renderer.getTransportState());
        var positionInfo = renderer.getPosition();
        return jsonResponse(HttpStatus.OK,
                MccJsonSerializer.transportInfoToJson(transportInfo, positionInfo));
    }

    static String extractRendererUdn(String uri, String actionSuffix) {
        String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
        String prefix = "/api/renderers/";
        if (!path.startsWith(prefix)) return null;
        String afterPrefix = path.substring(prefix.length());
        int suffixIdx = afterPrefix.indexOf(actionSuffix);
        return suffixIdx >= 0 ? afterPrefix.substring(0, suffixIdx) : afterPrefix;
    }

    /**
     * Guesses the content item type from a MIME type string.
     *
     * @param mimeType the MIME type, or null
     * @return the appropriate content item type
     * @since 0.1.0
     */
    private static ContentItemType guessItemType(String mimeType) {
        if (mimeType == null) return ContentItemType.GENERIC_ITEM;
        if (mimeType.startsWith("video/")) return ContentItemType.VIDEO_ITEM;
        if (mimeType.startsWith("audio/")) return ContentItemType.AUDIO_ITEM;
        if (mimeType.startsWith("image/")) return ContentItemType.IMAGE_ITEM;
        return ContentItemType.GENERIC_ITEM;
    }

    /**
     * Extracts a human-readable title from a media URL.
     *
     * @param uri the media URI
     * @return a display title
     * @since 0.1.0
     */
    private static String extractTitle(String uri) {
        if (uri == null || uri.isEmpty()) return "Unknown";
        // Strip query parameters
        String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
        // Get the last path segment
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            String filename = path.substring(lastSlash + 1);
            // Remove file extension
            int dotIdx = filename.lastIndexOf('.');
            if (dotIdx > 0) {
                filename = filename.substring(0, dotIdx);
            }
            // URL-decode common patterns
            return filename.replace("%20", " ").replace("+", " ");
        }
        return "Media";
    }
}
