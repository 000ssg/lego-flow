package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.http.core.HttpContext;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Objects;
import static ssg.legoflow.upnp.demo.mccweb.MccDeviceHandler.jsonResponse;
/**
 * Proxies media streams from DLNA sources to the browser.
 *
 * <p>Browsers cannot directly fetch media from DLNA devices due to CORS
 * restrictions. This handler acts as a same-origin reverse proxy, forwarding
 * HTTP Range requests for seeking support and setting the appropriate DLNA
 * transfer mode headers.
 *
 * <p>Two endpoints are provided:
 * <ul>
 *   <li>{@code GET /api/media/stream?url=...&mime=...} — proxy from any URL</li>
 *   <li>{@code GET /api/servers/{udn}/stream/{itemId}} — resolve item then proxy</li>
 * </ul>
 *
 * <p>Media is streamed (not buffered) using {@link HttpResponse#setBodyStream}
 * so that large files are piped directly from the DLNA server to the browser
 * without loading the entire content into JVM memory. A preliminary HEAD request
 * is issued to obtain the {@code Content-Length}, which HTML5 media elements
 * need for seeking and duration display.
 *
 * @since 0.1.0
 */
public class MccMediaProxyHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MccMediaProxyHandler.class);

    private final ControlPoint controlPoint;
    private final HttpClient httpClient;

    /**
     * Creates a new media proxy handler.
     *
     * @param controlPoint the UPnP control point
     * @since 0.1.0
     */
    public MccMediaProxyHandler(ControlPoint controlPoint) {
        this.controlPoint = Objects.requireNonNull(controlPoint, "controlPoint must not be null");
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Handles {@code GET /api/media/stream?url={encoded-url}&mime={mime-type}}.
     *
     * <p>Proxies the media content from the given URL, forwarding Range headers
     * from the browser request for seeking support.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with proxied media content
     * @since 0.1.0
     */
    public HttpResponse streamByUrl(HttpContext ctx, HttpRequest request) {
        Map<String, String> params = request.getQueryParams();
        String url = params.get("url");
        LOG.info("streamByUrl called: url={}, mime={}, range={}, all params={}",
                url, params.get("mime"), request.getHeaders().get(HttpHeaders.RANGE), params.keySet());
        if (url == null || url.isEmpty()) {
            return jsonResponse(HttpStatus.BAD_REQUEST,
                    MccJsonSerializer.errorToJson("Missing 'url' query parameter"));
        }

        // Note: getQueryParams() already URL-decodes the value, so 'url' is ready to use
        String mimeType = params.getOrDefault("mime", "");
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        }
        String rangeHeader = request.getHeaders().get(HttpHeaders.RANGE);

        return proxyStream(url, mimeType, rangeHeader);
    }

    /**
     * Handles {@code GET /api/servers/{udn}/stream/{itemId}}.
     *
     * <p>Resolves the content item from the media server, extracts its resource
     * URL and MIME type, then proxies the stream.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with proxied media content
     * @since 0.1.0
     */
    public HttpResponse streamByItem(HttpContext ctx, HttpRequest request) {
        String path = request.getUri().contains("?")
                ? request.getUri().substring(0, request.getUri().indexOf('?'))
                : request.getUri();

        // Path: /api/servers/{udn}/stream/{itemId}
        String afterServers = path.substring("/api/servers/".length());
        int streamIdx = afterServers.indexOf("/stream/");
        if (streamIdx < 0) {
            return jsonResponse(HttpStatus.BAD_REQUEST,
                    MccJsonSerializer.errorToJson("Invalid stream path"));
        }
        String udn = afterServers.substring(0, streamIdx);
        String itemId = afterServers.substring(streamIdx + "/stream/".length());

        if (itemId.isEmpty()) {
            return jsonResponse(HttpStatus.BAD_REQUEST,
                    MccJsonSerializer.errorToJson("Missing item ID"));
        }

        MediaServerProxy server = findServer(udn);
        if (server == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Server not found: " + udn));
        }

        ContentItem item;
        try {
            item = server.getContent(itemId);
        } catch (Exception e) {
            return jsonResponse(HttpStatus.BAD_GATEWAY,
                    MccJsonSerializer.errorToJson("Failed to resolve content: " + e.getMessage()));
        }

        if (item == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Content not found: " + itemId));
        }

        if (item.getResourceUrl() == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Content has no resource URL: " + itemId));
        }

        String mimeType = item.getProtocolInfo() != null
                ? item.getProtocolInfo().contentFormat()
                : "application/octet-stream";
        String rangeHeader = request.getHeaders().get(HttpHeaders.RANGE);

        return proxyStream(item.getResourceUrl().toString(), mimeType, rangeHeader);
    }

    /**
     * Proxies a media stream from the given upstream URL.
     *
     * <p>The upstream body is piped as a stream to avoid loading the entire
     * media file into memory. If the upstream GET response does not include
     * a {@code Content-Length} header, a preliminary HEAD request is issued
     * to determine the total size — HTML5 media elements require
     * {@code Content-Length} for seeking and duration display.
     *
     * @param upstreamUrl the URL to fetch from
     * @param mimeType    the MIME type for the Content-Type header
     * @param rangeHeader the browser's Range header value, or null
     * @return the HTTP response with streaming body
     */
    private HttpResponse proxyStream(String upstreamUrl, String mimeType, String rangeHeader) {
        try {
            // DLNA transfer mode depends on content type:
            // - "Streaming" for audio/video (A/V streaming with seek support)
            // - "Interactive" for images and other non-streaming content
            // Using the wrong mode causes DLNA servers to return 406 Not Acceptable
            String transferMode = isStreamingContent(mimeType) ? "Streaming" : "Interactive";

            var requestBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(upstreamUrl))
                    .header("transferMode.dlna.org", transferMode)
                    .GET();

            if (rangeHeader != null && !rangeHeader.isEmpty()) {
                requestBuilder.header("Range", rangeHeader);
            }

            var upstreamResponse = httpClient.send(
                    requestBuilder.build(),
                    java.net.http.HttpResponse.BodyHandlers.ofInputStream());

            int statusCode = upstreamResponse.statusCode();

            HttpStatus responseStatus;
            if (statusCode == 206) {
                responseStatus = HttpStatus.PARTIAL_CONTENT;
            } else if (statusCode >= 200 && statusCode < 300) {
                responseStatus = HttpStatus.OK;
            } else {
                // Error: consume body and return error
                try (var errorStream = upstreamResponse.body()) {
                    // consume and discard
                }
                return jsonResponse(HttpStatus.BAD_GATEWAY,
                        MccJsonSerializer.errorToJson(
                                "Upstream returned status " + statusCode));
            }

            // Content-Length from the GET response
            long contentLength = upstreamResponse.headers()
                    .firstValueAsLong("content-length").orElse(-1L);

            // If GET didn't include Content-Length and this is not a Range response,
            // try a HEAD request so the browser knows the total size
            if (contentLength < 0 && responseStatus == HttpStatus.OK) {
                contentLength = probeContentLength(upstreamUrl);
            }

            // Resolve effective MIME type: prefer caller-supplied if it looks real,
            // otherwise use upstream's Content-Type, finally fall back to URL extension
            String effectiveMime = resolveEffectiveMime(mimeType, upstreamResponse, upstreamUrl);

            HttpResponse response = new HttpResponse(
                    responseStatus,
                    ssg.legoflow.http.core.HttpVersion.HTTP_1_1,
                    new HttpHeaders());
            response.setBodyStream(upstreamResponse.body(), contentLength);
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, effectiveMime);
            response.getHeaders().set(HttpHeaders.ACCEPT_RANGES, "bytes");

            // Forward Content-Range from upstream if present (for 206 responses)
            upstreamResponse.headers().firstValue("content-range")
                    .ifPresent(cr -> response.getHeaders().set(HttpHeaders.CONTENT_RANGE, cr));

            LOG.info("Proxying {} {} bytes, status={}, mime={} (caller={}, upstream={}), transferMode={}",
                    upstreamUrl, contentLength, responseStatus, effectiveMime,
                    mimeType,
                    upstreamResponse.headers().firstValue("content-type").orElse("(none)"),
                    transferMode);

            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return jsonResponse(HttpStatus.BAD_GATEWAY,
                    MccJsonSerializer.errorToJson("Stream interrupted"));
        } catch (Exception e) {
            LOG.warn("Failed to proxy media from {}: {}", upstreamUrl, e.getMessage());
            return jsonResponse(HttpStatus.BAD_GATEWAY,
                    MccJsonSerializer.errorToJson("Failed to fetch media: " + e.getMessage()));
        }
    }

    /**
     * Determines if the given MIME type represents streaming content (audio/video)
     * that requires DLNA "Streaming" transfer mode, as opposed to interactive
     * content (images, text) that requires "Interactive" transfer mode.
     *
     * @param mimeType the MIME type to check
     * @return true for audio/video content, false for everything else
     */
    private static boolean isStreamingContent(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return true; // default to Streaming for unknown types (safer for A/V)
        }
        String lower = mimeType.toLowerCase();
        return lower.startsWith("audio/") || lower.startsWith("video/");
    }

    /**
     * Issues a HEAD request to determine the content length of the upstream resource.
     *
     * @param url the upstream URL to probe
     * @return the content length in bytes, or -1 if unknown
     */
    private long probeContentLength(String url) {
        try {
            var headRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody())
                    .build();
            var headResponse = httpClient.send(headRequest,
                    java.net.http.HttpResponse.BodyHandlers.discarding());
            return headResponse.headers()
                    .firstValueAsLong("content-length").orElse(-1L);
        } catch (Exception e) {
            LOG.debug("HEAD probe failed for {}: {}", url, e.getMessage());
            return -1L;
        }
    }

    /**
     * Resolves the effective MIME type for a proxied response. Tries (in order):
     * <ol>
     *   <li>Caller-supplied mime if it looks like a real type (not blank, not {@code *}, not {@code application/octet-stream})</li>
     *   <li>Upstream response's {@code Content-Type} header</li>
     *   <li>Inferred from the URL file extension</li>
     *   <li>Fallback to {@code application/octet-stream}</li>
     * </ol>
     */
    private static String resolveEffectiveMime(String callerMime,
                                                java.net.http.HttpResponse<?> upstreamResponse,
                                                String upstreamUrl) {
        // 1. Use caller-supplied if it looks real
        if (callerMime != null && !callerMime.isBlank()
                && !"*".equals(callerMime)
                && !"application/octet-stream".equals(callerMime)) {
            return callerMime;
        }

        // 2. Use upstream Content-Type if available
        String upstreamCt = upstreamResponse.headers()
                .firstValue("content-type").orElse(null);
        if (upstreamCt != null && !upstreamCt.isBlank()
                && !"application/octet-stream".equals(upstreamCt)) {
            // Strip charset/params for cleanliness
            int semi = upstreamCt.indexOf(';');
            return semi > 0 ? upstreamCt.substring(0, semi).strip() : upstreamCt.strip();
        }

        // 3. Infer from URL extension
        String inferred = inferMimeFromUrl(upstreamUrl);
        if (inferred != null) {
            return inferred;
        }

        // 4. Fallback
        return "application/octet-stream";
    }

    /**
     * Infers MIME type from URL file extension.
     */
    private static String inferMimeFromUrl(String url) {
        if (url == null) return null;
        // Strip query params
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot >= path.length() - 1) return null;
        String ext = path.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "mp3" -> "audio/mpeg";
            case "mp4", "m4v" -> "video/mp4";
            case "m4a", "aac" -> "audio/mp4";
            case "ogg", "oga" -> "audio/ogg";
            case "ogv" -> "video/ogg";
            case "wav" -> "audio/wav";
            case "flac" -> "audio/flac";
            case "wma" -> "audio/x-ms-wma";
            case "wmv" -> "video/x-ms-wmv";
            case "avi" -> "video/x-msvideo";
            case "mkv" -> "video/x-matroska";
            case "webm" -> "video/webm";
            case "ts" -> "video/mp2t";
            case "mpg", "mpeg" -> "video/mpeg";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "tiff", "tif" -> "image/tiff";
            default -> null;
        };
    }

    private MediaServerProxy findServer(String udn) {
        if (udn == null) return null;
        for (var server : controlPoint.discoverMediaServers()) {
            if (server.getUdn().equals(udn)) {
                return server;
            }
        }
        return null;
    }
}
