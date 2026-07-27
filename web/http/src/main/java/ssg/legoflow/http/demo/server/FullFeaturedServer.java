package ssg.legoflow.http.demo.server;

import ssg.legoflow.http.caching.CacheControl;
import ssg.legoflow.http.caching.CacheValidator;
import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.security.HstsPolicy;
import ssg.legoflow.http.security.SslConfig;
import ssg.legoflow.http.server.HttpServer;
import ssg.legoflow.http.staticcontent.ContentResolver;
import ssg.legoflow.http.staticcontent.StaticContentConfig;
import ssg.legoflow.http.staticcontent.StaticContentHandler;
import ssg.legoflow.http.websocket.WebSocketHandshake;
import ssg.legoflow.http.websocket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full-featured HTTP server demo combining static content, REST routes,
 * caching, security (TLS + HSTS), WebSocket, and compression.
 *
 * @since 1.0
 */
public class FullFeaturedServer {

    private final HttpServer server;
    private final StaticContentHandler contentHandler;
    private final CacheValidator cacheValidator;
    private final WebSocketHandshake wsHandshake;
    private final HstsPolicy hstsPolicy;
    private final Map<String, WebSocketSession> wsSessions = new ConcurrentHashMap<>();
    private final Map<String, String> dataStore = new ConcurrentHashMap<>();

    public FullFeaturedServer(ContentResolver resolver) {
        this(resolver, 8443);
    }

    public FullFeaturedServer(ContentResolver resolver, int port) {
        var sslConfig = new SslConfig();
        sslConfig.setKeystorePath("/etc/ssl/keystore.jks");
        sslConfig.setKeystorePassword("changeit");

        this.hstsPolicy = new HstsPolicy(31536000, true, true);
        this.cacheValidator = new CacheValidator();
        this.wsHandshake = new WebSocketHandshake();

        var staticConfig = new StaticContentConfig();
        this.contentHandler = new StaticContentHandler(resolver, staticConfig);

        var config = new ServerConfig(StandardProfiles.serverFull());
        config.setPort(port);
        config.setSslConfig(sslConfig);
        config.setStaticContentConfig(staticConfig);
        this.server = new HttpServer("full-featured-server", config);

        var router = server.getRouter();

        router.get("/", (ctx, req) -> {
            var response = HttpResponse.of(HttpStatus.OK, "Full-Featured Server");
            applySecurityHeaders(response);
            return response;
        });

        router.get("/static", (ctx, req) -> {
            var response = contentHandler.handle(req);
            applySecurityHeaders(response);
            return response;
        });

        router.get("/api/data", (ctx, req) -> {
            var sb = new StringBuilder("{");
            var entries = dataStore.entrySet().stream().toList();
            for (int i = 0; i < entries.size(); i++) {
                var e = entries.get(i);
                sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
                if (i < entries.size() - 1) sb.append(",");
            }
            sb.append("}");
            var response = HttpResponse.of(HttpStatus.OK, sb.toString());
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            response.getHeaders().set(HttpHeaders.CACHE_CONTROL,
                    new CacheControl().setPublic(true).maxAge(60).toString());
            applySecurityHeaders(response);
            return response;
        });

        router.post("/api/data", (ctx, req) -> {
            var body = req.getBodyAsString();
            if (body == null || body.isBlank()) {
                return HttpResponse.of(HttpStatus.BAD_REQUEST, "Body required");
            }
            var key = "item-" + dataStore.size();
            dataStore.put(key, body.strip());
            var response = HttpResponse.of(HttpStatus.CREATED, "{\"key\":\"" + key + "\"}");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            applySecurityHeaders(response);
            return response;
        });

        router.get("/ws", (ctx, req) -> {
            if (!wsHandshake.isWebSocketUpgrade(req)) {
                return HttpResponse.of(HttpStatus.BAD_REQUEST, "WebSocket upgrade required");
            }
            var response = wsHandshake.createHandshakeResponse(req);
            var session = new WebSocketSession("ws-" + wsSessions.size());
            wsSessions.put(session.getId(), session);
            return response;
        });
    }

    private void applySecurityHeaders(HttpResponse response) {
        response.getHeaders().set(HttpHeaders.STRICT_TRANSPORT_SECURITY, hstsPolicy.toHeaderValue());
    }

    /**
     * Returns the underlying HttpServer instance.
     *
     * @return the server
     */
    public HttpServer getServer() {
        return server;
    }

    /**
     * Returns the data store for direct inspection.
     *
     * @return the data store map
     */
    public Map<String, String> getDataStore() {
        return dataStore;
    }

    /**
     * Returns the WebSocket sessions map.
     *
     * @return the sessions map
     */
    public Map<String, WebSocketSession> getWsSessions() {
        return wsSessions;
    }

    /**
     * Returns the cache validator.
     *
     * @return the cache validator
     */
    public CacheValidator getCacheValidator() {
        return cacheValidator;
    }

    /**
     * Returns the HSTS policy.
     *
     * @return the HSTS policy
     */
    public HstsPolicy getHstsPolicy() {
        return hstsPolicy;
    }
}
