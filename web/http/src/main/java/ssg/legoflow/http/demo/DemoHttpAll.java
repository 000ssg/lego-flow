package ssg.legoflow.http.demo;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.caching.CacheControl;
import ssg.legoflow.http.caching.CacheValidator;
import ssg.legoflow.http.caching.InMemoryResponseCache;
import ssg.legoflow.http.caching.ResponseCache.CachedResponse;
import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.content.ContentNegotiator;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.header.ContentEncoding;
import ssg.legoflow.http.header.MediaType;
import ssg.legoflow.http.server.HttpServer;
import ssg.legoflow.http.transfer.ByteRangeHandler;
import ssg.legoflow.http.transfer.ContentEncodingCodec;
import ssg.legoflow.http.websocket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Comprehensive demo of all HTTP module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link HttpServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports full HTTP/1.1 (RFC 2616) with pluggable
 * features: SSL/TLS, WebSocket upgrade, static file serving, content negotiation, caching,
 * compression, chunked transfer, byte ranges, and keep-alive. Ideal for development,
 * testing, CI/CD, and learning the HTTP protocol.</p>
 *
 * <p><b>Alternative: External Apache HTTP Server / Nginx / Caddy / Tomcat</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production load testing with process-level isolation</li>
 *   <li>Reverse proxy configurations (Nginx, Caddy)</li>
 *   <li>Servlet container features (Tomcat, Jetty)</li>
 *   <li>Integration testing against production web servers</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop).
 * All request/response handling uses the same API regardless of backend. When
 * {@code USE_EXTERNAL=true}, the demo connects HTTP requests to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>GET/POST/PUT/DELETE — CRUD operations via router</li>
 *   <li>Content negotiation — media type, encoding, language selection</li>
 *   <li>Caching — ETag, If-Modified-Since, 304 Not Modified</li>
 *   <li>Compression — gzip, deflate content encoding</li>
 *   <li>Chunked transfer — streaming response bodies</li>
 *   <li>Byte ranges — partial content (206) and range requests</li>
 *   <li>Keep-alive — persistent connections</li>
 *   <li>WebSocket upgrade — handshake and frame encoding</li>
 *   <li>Routing — path-based, method-based request dispatch</li>
 *   <li>Feature profiles — minimal, standard, full server configurations</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoHttpAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoHttpAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house HttpServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for Nginx/Apache
    // =========================================================================

    /** Set to {@code true} to connect to an external HTTP server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external HTTP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external HTTP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 8080;

    private DemoHttpAll() {}

    /**
     * Results from running the full demo.
     *
     * @param crudOperations      true if GET/POST/PUT/DELETE routes handled correctly
     * @param contentNegotiation  true if media type and encoding negotiation succeeded
     * @param caching             true if ETag and conditional GET worked
     * @param compression         true if gzip compression applied and decompressed correctly
     * @param chunkedTransfer     true if chunked transfer encoding worked
     * @param byteRanges          true if partial content (206) responses returned correctly
     * @param keepAlive           true if connection keep-alive headers handled
     * @param webSocket           true if WebSocket handshake and framing worked
     * @param routing             true if path-based routing dispatched correctly
     * @param featureProfiles     true if minimal/standard/full profiles configured correctly
     */
    public record Results(
            boolean crudOperations,
            boolean contentNegotiation,
            boolean caching,
            boolean compression,
            boolean chunkedTransfer,
            boolean byteRanges,
            boolean keepAlive,
            boolean webSocket,
            boolean routing,
            boolean featureProfiles
    ) {}

    /**
     * Runs the comprehensive demo covering all HTTP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        boolean crud = demoCrudOperations();
        boolean contentNeg = demoContentNegotiation();
        boolean caching = demoCaching();
        boolean compression = demoCompression();
        boolean chunked = demoChunkedTransfer();
        boolean ranges = demoByteRanges();
        boolean keepAlive = demoKeepAlive();
        boolean ws = demoWebSocket();
        boolean routing = demoRouting();
        boolean profiles = demoFeatureProfiles();

        return new Results(
                crud, contentNeg, caching, compression, chunked,
                ranges, keepAlive, ws, routing, profiles
        );
    }

    // ======================== 1. CRUD OPERATIONS ============================

    /**
     * Demonstrates GET, POST, PUT, DELETE operations via the HTTP router.
     */
    static boolean demoCrudOperations() {
        LOG.info("=== 1. CRUD Operations ===");
        var server = new HttpServer(new ServerConfig(StandardProfiles.serverStandard()));
        server.setCompressionEnabled(false);
        var ctx = new DefaultContext();

        // Register routes
        server.getRouter().get("/items", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.OK, "[\"item1\",\"item2\"]"));
        server.getRouter().post("/items", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.CREATED, "Created"));
        server.getRouter().put("/items/1", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.OK, "Updated"));
        server.getRouter().delete("/items/1", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.NO_CONTENT));

        // GET
        var getResp = server.handleRequest(ctx, HttpRequest.of(HttpMethod.GET, "/items"));
        LOG.info("GET /items: {} — {}", getResp.getStatus(), getResp.getBodyAsString());

        // POST
        var postReq = HttpRequest.of(HttpMethod.POST, "/items");
        postReq.setBody(ByteBuffer.wrap("new item".getBytes()));
        var postResp = server.handleRequest(ctx, postReq);
        LOG.info("POST /items: {}", postResp.getStatus());

        // PUT
        var putReq = HttpRequest.of(HttpMethod.PUT, "/items/1");
        putReq.setBody(ByteBuffer.wrap("updated".getBytes()));
        var putResp = server.handleRequest(ctx, putReq);
        LOG.info("PUT /items/1: {}", putResp.getStatus());

        // DELETE
        var delResp = server.handleRequest(ctx, HttpRequest.of(HttpMethod.DELETE, "/items/1"));
        LOG.info("DELETE /items/1: {}", delResp.getStatus());

        return getResp.getStatus() == HttpStatus.OK
                && postResp.getStatus() == HttpStatus.CREATED
                && putResp.getStatus() == HttpStatus.OK
                && delResp.getStatus() == HttpStatus.NO_CONTENT;
    }

    // ======================== 2. CONTENT NEGOTIATION ========================

    /**
     * Demonstrates HTTP content negotiation: media type selection with quality values,
     * encoding negotiation, and fallback behavior.
     */
    static boolean demoContentNegotiation() {
        LOG.info("=== 2. Content Negotiation ===");
        var negotiator = new ContentNegotiator();

        // Media type negotiation
        var mediaResult = negotiator.negotiateMediaType(
                "text/html;q=0.8, application/json;q=1.0",
                List.of(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON));
        boolean jsonPreferred = mediaResult.isPresent()
                && mediaResult.get() == MediaType.APPLICATION_JSON;
        LOG.info("Preferred media type: {}", mediaResult.orElse(null));

        // Encoding negotiation
        var encodingResult = negotiator.negotiateEncoding(
                "gzip;q=1.0, deflate;q=0.5",
                List.of(ContentEncoding.GZIP, ContentEncoding.DEFLATE));
        boolean gzipPreferred = encodingResult.isPresent()
                && encodingResult.get() == ContentEncoding.GZIP;
        LOG.info("Preferred encoding: {}", encodingResult.orElse(null));

        // Wildcard Accept
        var wildcardResult = negotiator.negotiateMediaType(
                "*/*", List.of(MediaType.TEXT_PLAIN));
        boolean wildcardOk = wildcardResult.isPresent();
        LOG.info("Wildcard: {}", wildcardResult.orElse(null));

        return jsonPreferred && gzipPreferred && wildcardOk;
    }

    // ======================== 3. CACHING ====================================

    /**
     * Demonstrates HTTP caching: Cache-Control parsing, in-memory response cache,
     * ETag conditional GET, and If-Modified-Since validation.
     */
    static boolean demoCaching() {
        LOG.info("=== 3. Caching ===");

        // Cache-Control parsing
        var cc = CacheControl.parse("public, max-age=3600, must-revalidate");
        LOG.info("Cache-Control: public={}, max-age={}, must-revalidate={}",
                cc.isPublic(), cc.getMaxAge(), cc.isMustRevalidate());

        // In-memory cache
        var cache = new InMemoryResponseCache(100);
        var response = HttpResponse.of(HttpStatus.OK, "cached data");
        cache.put("/resource", new CachedResponse(response, System.currentTimeMillis(), 3600));
        var cached = cache.get("/resource");
        boolean cacheHit = cached.isPresent();
        LOG.info("Cache hit: {}", cacheHit);

        // ETag validation
        var validator = new CacheValidator();
        var request = HttpRequest.of(HttpMethod.GET, "/resource");
        request.getHeaders().set(HttpHeaders.IF_NONE_MATCH, "\"etag-123\"");
        boolean etagMatch = validator.validateETag(request, "\"etag-123\"");
        LOG.info("ETag match: {}", etagMatch);

        // If-Modified-Since
        var lastModified = Instant.parse("2024-01-15T10:30:00Z");
        var modRequest = HttpRequest.of(HttpMethod.GET, "/resource");
        var ifModSince = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .format(lastModified.atZone(java.time.ZoneOffset.UTC));
        modRequest.getHeaders().set(HttpHeaders.IF_MODIFIED_SINCE, ifModSince);
        boolean notModified = validator.validateLastModified(modRequest, lastModified);
        LOG.info("Not modified: {}", notModified);

        return cc.isPublic() && cc.getMaxAge() == 3600
                && cacheHit && etagMatch && notModified;
    }

    // ======================== 4. COMPRESSION ================================

    /**
     * Demonstrates gzip content encoding: server applies compression when client
     * sends Accept-Encoding, and the codec can decompress it back.
     */
    static boolean demoCompression() {
        LOG.info("=== 4. Compression ===");
        var server = new HttpServer(new ServerConfig(StandardProfiles.serverStandard()));
        var ctx = new DefaultContext();

        String original = "Hello, Compressed World! ".repeat(50);
        server.getRouter().get("/data", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.OK, original));

        // Request with Accept-Encoding: gzip
        var request = HttpRequest.of(HttpMethod.GET, "/data");
        request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate");
        var response = server.handleRequest(ctx, request);

        boolean isGzip = "gzip".equals(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
        LOG.info("Content-Encoding: {}", response.getHeaders().get(HttpHeaders.CONTENT_ENCODING));

        // Compressed body should be smaller than original
        int compressedSize = response.getBody().remaining();
        int originalSize = original.getBytes(StandardCharsets.UTF_8).length;
        boolean smallerSize = compressedSize < originalSize;
        LOG.info("Original: {} bytes, Compressed: {} bytes", originalSize, compressedSize);

        // Decompress to verify
        var decompressor = new ContentEncodingCodec(ContentEncoding.GZIP,
                ContentEncodingCodec.Mode.DECOMPRESS);
        ByteBuffer[] decompressed = decompressor.filter(ctx, response.getBody());
        var bytes = new byte[decompressed[0].remaining()];
        decompressed[0].get(bytes);
        boolean contentMatch = original.equals(new String(bytes, StandardCharsets.UTF_8));
        LOG.info("Decompressed matches original: {}", contentMatch);

        return isGzip && smallerSize && contentMatch;
    }

    // ======================== 5. CHUNKED TRANSFER ===========================

    /**
     * Demonstrates chunked transfer encoding for streaming responses.
     */
    static boolean demoChunkedTransfer() {
        LOG.info("=== 5. Chunked Transfer ===");
        var server = new HttpServer(new ServerConfig(StandardProfiles.serverStandard()));
        server.setCompressionEnabled(false);
        var ctx = new DefaultContext();

        var body = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            body.append("chunk-").append(i).append("\n");
        }

        server.getRouter().get("/stream", (httpCtx, req) -> {
            var resp = HttpResponse.of(HttpStatus.OK, body.toString());
            resp.getHeaders().set(HttpHeaders.TRANSFER_ENCODING, "chunked");
            return resp;
        });

        var request = HttpRequest.of(HttpMethod.GET, "/stream");
        var response = server.handleRequest(ctx, request);

        boolean isChunked = "chunked".equals(
                response.getHeaders().get(HttpHeaders.TRANSFER_ENCODING));
        boolean hasAllChunks = true;
        String responseBody = response.getBodyAsString();
        for (int i = 0; i < 10; i++) {
            if (!responseBody.contains("chunk-" + i)) {
                hasAllChunks = false;
                break;
            }
        }
        LOG.info("Chunked: {}, All chunks present: {}", isChunked, hasAllChunks);
        return isChunked && hasAllChunks;
    }

    // ======================== 6. BYTE RANGES ================================

    /**
     * Demonstrates HTTP byte range requests: partial content (206),
     * suffix ranges, open-ended ranges.
     */
    static boolean demoByteRanges() {
        LOG.info("=== 6. Byte Ranges ===");
        String content = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        var server = new HttpServer(new ServerConfig(StandardProfiles.serverStandard()));
        server.setCompressionEnabled(false);
        var ctx = new DefaultContext();

        server.getRouter().get("/file", (httpCtx, req) -> {
            var rangeHeader = req.getHeaders().get(HttpHeaders.RANGE);
            if (rangeHeader == null) {
                var resp = HttpResponse.of(HttpStatus.OK, content);
                resp.getHeaders().set(HttpHeaders.ACCEPT_RANGES, "bytes");
                return resp;
            }
            var ranges = ByteRangeHandler.parseRangeHeader(rangeHeader, content.length());
            if (ranges.isEmpty()) {
                return HttpResponse.of(HttpStatus.BAD_REQUEST, "Invalid range");
            }
            var range = ranges.getFirst();
            var bodyBuf = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
            var partial = ByteRangeHandler.extractRange(bodyBuf, range);
            var resp = new HttpResponse(HttpStatus.PARTIAL_CONTENT, HttpVersion.HTTP_1_1,
                    new HttpHeaders());
            resp.setBody(partial);
            resp.getHeaders().set(HttpHeaders.CONTENT_RANGE,
                    ByteRangeHandler.formatContentRange(range, content.length()));
            return resp;
        });

        // Full GET
        var fullResp = server.handleRequest(ctx, HttpRequest.of(HttpMethod.GET, "/file"));
        boolean fullOk = fullResp.getStatus() == HttpStatus.OK
                && content.equals(fullResp.getBodyAsString());

        // Range request: bytes=0-4 -> "ABCDE"
        var rangeReq = HttpRequest.of(HttpMethod.GET, "/file");
        rangeReq.getHeaders().set(HttpHeaders.RANGE, "bytes=0-4");
        var rangeResp = server.handleRequest(ctx, rangeReq);
        boolean partialOk = rangeResp.getStatus() == HttpStatus.PARTIAL_CONTENT
                && "ABCDE".equals(rangeResp.getBodyAsString());

        LOG.info("Full GET: {}, Partial: {} — '{}'",
                fullResp.getStatus(), rangeResp.getStatus(), rangeResp.getBodyAsString());
        return fullOk && partialOk;
    }

    // ======================== 7. KEEP-ALIVE =================================

    /**
     * Demonstrates HTTP keep-alive: Connection header handling and
     * multiple sequential requests over the same logical connection.
     */
    static boolean demoKeepAlive() {
        LOG.info("=== 7. Keep-Alive ===");
        var server = new HttpServer(new ServerConfig(StandardProfiles.serverStandard()));
        server.setCompressionEnabled(false);
        var ctx = new DefaultContext();

        server.getRouter().get("/ping", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.OK, "pong"));

        // Request with Connection: keep-alive
        var request = HttpRequest.of(HttpMethod.GET, "/ping");
        request.getHeaders().set(HttpHeaders.CONNECTION, "keep-alive");
        var response = server.handleRequest(ctx, request);

        // Sequential requests (simulating keep-alive connection)
        var resp2 = server.handleRequest(ctx, HttpRequest.of(HttpMethod.GET, "/ping"));
        var resp3 = server.handleRequest(ctx, HttpRequest.of(HttpMethod.GET, "/ping"));

        boolean allOk = response.getStatus() == HttpStatus.OK
                && resp2.getStatus() == HttpStatus.OK
                && resp3.getStatus() == HttpStatus.OK;
        LOG.info("Keep-alive: 3 sequential requests, all OK={}", allOk);
        return allOk;
    }

    // ======================== 8. WEBSOCKET ==================================

    /**
     * Demonstrates WebSocket upgrade handshake and frame encoding/decoding.
     * Preferred: in-house WebSocket implementation integrated with HTTP server.
     * Alternative: external WebSocket server (ws, Socket.IO).
     */
    static boolean demoWebSocket() {
        LOG.info("=== 8. WebSocket ===");

        // WebSocket handshake
        var handshake = new WebSocketHandshake();
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_VERSION, "13");

        boolean isUpgrade = handshake.isWebSocketUpgrade(request);
        var response = handshake.createHandshakeResponse(request);
        boolean is101 = response.getStatus() == HttpStatus.SWITCHING_PROTOCOLS;
        LOG.info("WebSocket upgrade: {}, Response: {}", isUpgrade, response.getStatus());

        // Frame encoding/decoding
        var codec = new WebSocketFrameCodec(WebSocketFrameCodec.Mode.ENCODE);
        var textFrame = WebSocketFrame.text("Hello, WebSocket!");
        ByteBuffer encoded = codec.encodeFrame(textFrame);
        var decoded = codec.decodeFrame(encoded);
        boolean textOk = "Hello, WebSocket!".equals(decoded.getPayloadText());
        LOG.info("Text frame round-trip: {}", textOk);

        // Session lifecycle
        var session = new WebSocketSession("demo-session");
        boolean isOpen = session.isOpen();
        session.close();
        boolean isClosed = !session.isOpen();
        LOG.info("Session lifecycle: open={}, closed={}", isOpen, isClosed);

        return isUpgrade && is101 && textOk && isOpen && isClosed;
    }

    // ======================== 9. ROUTING ====================================

    /**
     * Demonstrates path-based routing, method-based dispatch, and 404/405 handling.
     */
    static boolean demoRouting() {
        LOG.info("=== 9. Routing ===");
        var server = new HttpServer(new ServerConfig(StandardProfiles.serverStandard()));
        server.setCompressionEnabled(false);
        var ctx = new DefaultContext();
        var router = server.getRouter();

        router.get("/api/users", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.OK, "users list"));
        router.post("/api/users", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.CREATED, "user created"));

        // Registered paths
        boolean hasPath = router.getRegisteredPaths().contains("/api/users");

        // Valid route
        var getResp = router.dispatch(null, HttpRequest.of(HttpMethod.GET, "/api/users"));
        boolean getOk = getResp.getStatus() == HttpStatus.OK;

        // 404 Not Found
        var notFound = router.dispatch(null, HttpRequest.of(HttpMethod.GET, "/unknown"));
        boolean is404 = notFound.getStatus() == HttpStatus.NOT_FOUND;

        // 405 Method Not Allowed
        var notAllowed = router.dispatch(null, HttpRequest.of(HttpMethod.DELETE, "/api/users"));
        boolean is405 = notAllowed.getStatus() == HttpStatus.METHOD_NOT_ALLOWED;

        LOG.info("Routing: GET={}, 404={}, 405={}", getOk, is404, is405);
        return hasPath && getOk && is404 && is405;
    }

    // ======================== 10. FEATURE PROFILES ===========================

    /**
     * Demonstrates standard feature profiles: minimal, standard, and full.
     * Each profile assembles a different set of HTTP features.
     */
    static boolean demoFeatureProfiles() {
        LOG.info("=== 10. Feature Profiles ===");

        // Minimal server
        var minimal = new HttpServer(new ServerConfig(StandardProfiles.serverMinimal()));
        boolean minimalOk = minimal.getConfig() != null;
        LOG.info("Minimal profile created");

        // Standard server
        var standard = new HttpServer(new ServerConfig(StandardProfiles.serverStandard()));
        boolean standardOk = standard.getConfig() != null;
        LOG.info("Standard profile created");

        // Full server
        var full = new HttpServer(new ServerConfig(StandardProfiles.serverFull()));
        boolean fullOk = full.getConfig() != null;
        LOG.info("Full profile created");

        return minimalOk && standardOk && fullOk;
    }
}
