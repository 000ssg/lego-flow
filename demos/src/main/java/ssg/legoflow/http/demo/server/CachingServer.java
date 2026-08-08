package ssg.legoflow.http.demo.server;

import ssg.legoflow.http.caching.CacheControl;
import ssg.legoflow.http.caching.CacheValidator;
import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.header.EntityTag;
import ssg.legoflow.http.server.HttpServer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * HTTP server demo with response caching using ETags and If-Modified-Since.
 *
 * <p>Uses {@link CacheValidator} to handle conditional GET requests and return
 * 304 Not Modified when the resource has not changed.
 *
 * @since 0.1
 */
public class CachingServer {

    private final HttpServer server;
    private final CacheValidator cacheValidator;
    private String resourceContent = "Cached Resource v1";
    private String resourceETag = "\"v1\"";
    private Instant lastModified = Instant.parse("2025-01-01T00:00:00Z");

    public CachingServer() {
        this(8084);
    }

    public CachingServer(int port) {
        var config = new ServerConfig(StandardProfiles.serverStandard());
        config.setPort(port);
        this.server = new HttpServer("caching-server", config);
        this.cacheValidator = new CacheValidator();

        var router = server.getRouter();

        router.get("/", (ctx, req) -> HttpResponse.of(HttpStatus.OK, "Caching Server"));

        router.get("/resource", (ctx, req) -> {
            if (cacheValidator.isConditionalGet(req)) {
                if (cacheValidator.validateETag(req, resourceETag)
                        || cacheValidator.validateLastModified(req, lastModified)) {
                    return cacheValidator.notModifiedResponse();
                }
            }

            var response = HttpResponse.of(HttpStatus.OK, resourceContent);
            response.getHeaders().set(HttpHeaders.ETAG, resourceETag);
            response.getHeaders().set(HttpHeaders.LAST_MODIFIED, formatHttpDate(lastModified));
            response.getHeaders().set(HttpHeaders.CACHE_CONTROL,
                    new CacheControl().setPublic(true).maxAge(3600).toString());
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain");
            return response;
        });
    }

    /**
     * Updates the cached resource, changing its content, ETag, and last-modified time.
     *
     * @param content the new content
     * @param etag    the new ETag value (including quotes)
     */
    public void updateResource(String content, String etag) {
        this.resourceContent = content;
        this.resourceETag = etag;
        this.lastModified = Instant.now();
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
     * Returns the cache validator.
     *
     * @return the cache validator
     */
    public CacheValidator getCacheValidator() {
        return cacheValidator;
    }

    /**
     * Returns the current resource ETag.
     *
     * @return the ETag string
     */
    public String getResourceETag() {
        return resourceETag;
    }

    /**
     * Returns the last modified instant.
     *
     * @return the last modified time
     */
    public Instant getLastModified() {
        return lastModified;
    }

    private static String formatHttpDate(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME
                .format(instant.atZone(ZoneOffset.UTC));
    }
}
