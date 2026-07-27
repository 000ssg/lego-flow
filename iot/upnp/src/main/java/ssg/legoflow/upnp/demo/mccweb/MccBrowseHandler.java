package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.http.core.HttpContext;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.mediaserver.ContentItem;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static ssg.legoflow.upnp.demo.mccweb.MccDeviceHandler.jsonResponse;

/**
 * Handles content browsing REST API requests for the Media Control Center.
 *
 * <p>Provides endpoints for browsing media server content directories,
 * searching content, and retrieving individual content items.
 *
 * @since 1.0.0
 */
public class MccBrowseHandler {

    private final ControlPoint controlPoint;

    /**
     * Creates a new browse handler.
     *
     * @param controlPoint the UPnP control point
     * @since 1.0.0
     */
    public MccBrowseHandler(ControlPoint controlPoint) {
        this.controlPoint = Objects.requireNonNull(controlPoint, "controlPoint must not be null");
    }

    /**
     * Handles GET /api/servers/{udn}/browse - browses server content.
     *
     * <p>Query parameters: id (objectId, default "0"), start (default 0), count (default 50).
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with JSON array of content items
     * @since 1.0.0
     */
    public HttpResponse browse(HttpContext ctx, HttpRequest request) {
        String udn = MccDeviceHandler.extractUdn(request.getUri(), "/api/servers/");
        MediaServerProxy server = findServer(udn);
        if (server == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Server not found: " + udn));
        }

        Map<String, String> params = request.getQueryParams();
        String objectId = params.getOrDefault("id", "0");
        int start = parseIntParam(params, "start", 0);
        int count = parseIntParam(params, "count", 50);

        try {
            var browseResult = server.browseChildren(objectId, start, count);
            var items = new ssg.legoflow.upnp.mediaserver.DidlLiteParser()
                    .parse(browseResult.didlXml());
            String json = buildBrowseResultJson(items, browseResult.numberReturned(),
                    browseResult.totalMatches(), start);
            return jsonResponse(HttpStatus.OK, json);
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Browse failed: " + e.getMessage()));
        }
    }

    /**
     * Handles GET /api/servers/{udn}/browse/root - browses root container.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with JSON array of root items
     * @since 1.0.0
     */
    public HttpResponse browseRoot(HttpContext ctx, HttpRequest request) {
        String udn = extractServerUdn(request.getUri(), "/api/servers/", "/browse/root");
        MediaServerProxy server = findServer(udn);
        if (server == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Server not found: " + udn));
        }

        try {
            List<ContentItem> items = server.browseRoot();
            return jsonResponse(HttpStatus.OK, MccJsonSerializer.contentItemsToJson(items));
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Browse root failed: " + e.getMessage()));
        }
    }

    /**
     * Handles GET /api/servers/{udn}/search - searches server content.
     *
     * <p>Query parameter: query (the search string).
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with JSON array of matching items
     * @since 1.0.0
     */
    public HttpResponse search(HttpContext ctx, HttpRequest request) {
        String udn = extractServerUdn(request.getUri(), "/api/servers/", "/search");
        MediaServerProxy server = findServer(udn);
        if (server == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Server not found: " + udn));
        }

        Map<String, String> params = request.getQueryParams();
        String query = params.getOrDefault("query", "");
        if (query.isEmpty()) {
            return jsonResponse(HttpStatus.BAD_REQUEST,
                    MccJsonSerializer.errorToJson("Missing query parameter"));
        }

        try {
            List<ContentItem> results = server.search(query);
            return jsonResponse(HttpStatus.OK, MccJsonSerializer.contentItemsToJson(results));
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Search failed: " + e.getMessage()));
        }
    }

    /**
     * Handles GET /api/servers/{udn}/content/{itemId} - gets a single content item.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with content item JSON or 404
     * @since 1.0.0
     */
    public HttpResponse getContent(HttpContext ctx, HttpRequest request) {
        String path = request.getUri().contains("?")
                ? request.getUri().substring(0, request.getUri().indexOf('?'))
                : request.getUri();
        // Path: /api/servers/{udn}/content/{itemId}
        String afterServers = path.substring("/api/servers/".length());
        int contentIdx = afterServers.indexOf("/content/");
        if (contentIdx < 0) {
            return jsonResponse(HttpStatus.BAD_REQUEST,
                    MccJsonSerializer.errorToJson("Invalid content path"));
        }
        String udn = afterServers.substring(0, contentIdx);
        String itemId = afterServers.substring(contentIdx + "/content/".length());

        MediaServerProxy server = findServer(udn);
        if (server == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Server not found: " + udn));
        }

        try {
            ContentItem item = server.getContent(itemId);
            if (item == null) {
                return jsonResponse(HttpStatus.NOT_FOUND,
                        MccJsonSerializer.errorToJson("Content not found: " + itemId));
            }
            return jsonResponse(HttpStatus.OK, MccJsonSerializer.contentItemToJson(item));
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Get content failed: " + e.getMessage()));
        }
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

    private static String extractServerUdn(String uri, String prefix, String suffix) {
        String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
        if (!path.startsWith(prefix)) return null;
        String afterPrefix = path.substring(prefix.length());
        int suffixIdx = afterPrefix.indexOf(suffix);
        return suffixIdx >= 0 ? afterPrefix.substring(0, suffixIdx) : afterPrefix;
    }

    private static int parseIntParam(Map<String, String> params, String key, int defaultValue) {
        String value = params.get(key);
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String buildBrowseResultJson(List<ContentItem> items, int numberReturned,
                                                 int totalMatches, int startIndex) {
        var sb = new StringBuilder();
        sb.append('{');
        sb.append("\"items\":").append(MccJsonSerializer.contentItemsToJson(items));
        sb.append(',');
        sb.append("\"numberReturned\":").append(numberReturned);
        sb.append(',');
        sb.append("\"totalMatches\":").append(totalMatches);
        sb.append(',');
        sb.append("\"startIndex\":").append(startIndex);
        sb.append('}');
        return sb.toString();
    }
}
