package ssg.legoflow.http.demo;

import ssg.legoflow.http.caching.CacheControl;
import ssg.legoflow.http.caching.CacheValidator;
import ssg.legoflow.http.caching.InMemoryResponseCache;
import ssg.legoflow.http.caching.ResponseCache.CachedResponse;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import static org.assertj.core.api.Assertions.*;
/**
 * Demonstrates HTTP caching mechanisms: CacheControl directive parsing,
 * InMemoryResponseCache put/get/expiry, and CacheValidator conditional
 * GET validation.
 */
class CachingDemoTest {

    @Test
    void testCacheControlParse() {
        // Given: a Cache-Control header with multiple directives
        var header = "public, max-age=3600, must-revalidate, no-transform";

        // When: parsing the header
        var cc = CacheControl.parse(header);

        // Then: all directives are recognized
        assertThat(cc.isPublic()).isTrue();
        assertThat(cc.getMaxAge()).isEqualTo(3600);
        assertThat(cc.isMustRevalidate()).isTrue();
        assertThat(cc.isNoTransform()).isTrue();
        assertThat(cc.isNoCache()).isFalse();
        assertThat(cc.isNoStore()).isFalse();
        assertThat(cc.isPrivate()).isFalse();
    }

    @Test
    void testCacheControlBuildAndToString() {
        // Given: a CacheControl built programmatically
        var cc = new CacheControl()
                .noCache(true)
                .noStore(true)
                .maxAge(0);

        // When: converting to string
        var result = cc.toString();

        // Then: produces valid Cache-Control header value
        assertThat(result).contains("no-cache");
        assertThat(result).contains("no-store");
        assertThat(result).contains("max-age=0");
    }

    @Test
    void testInMemoryCachePutAndGet() {
        // Given: a response cache
        var cache = new InMemoryResponseCache(100);
        var response = HttpResponse.of(HttpStatus.OK, "cached content");
        var entry = new CachedResponse(response, System.currentTimeMillis(), 3600);

        // When: storing and retrieving
        cache.put("/resource", entry);
        var result = cache.get("/resource");

        // Then: the entry is found and not expired
        assertThat(result).isPresent();
        assertThat(result.get().response().getBodyAsString()).isEqualTo("cached content");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void testInMemoryCacheExpiry() {
        // Given: a cached response that has already expired (maxAge=0, stored in the past)
        var cache = new InMemoryResponseCache(100);
        var response = HttpResponse.of(HttpStatus.OK, "stale content");
        var entry = new CachedResponse(response, System.currentTimeMillis() - 5000, 1);

        // When: storing and then retrieving the expired entry
        cache.put("/stale", entry);
        var result = cache.get("/stale");

        // Then: the expired entry is not returned
        assertThat(result).isEmpty();
        assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    void testInMemoryCacheEviction() {
        // Given: a cache with max 3 entries
        var cache = new InMemoryResponseCache(3);

        // When: adding 4 entries
        for (int i = 1; i <= 4; i++) {
            var resp = HttpResponse.of(HttpStatus.OK, "content-" + i);
            cache.put("/item/" + i, new CachedResponse(resp, System.currentTimeMillis(), 3600));
        }

        // Then: cache size does not exceed max
        assertThat(cache.size()).isLessThanOrEqualTo(3);
    }

    @Test
    void testCacheValidatorConditionalGetWithETag() {
        // Given: a conditional GET request with If-None-Match header
        var validator = new CacheValidator();
        var request = HttpRequest.of(HttpMethod.GET, "/resource");
        request.getHeaders().set(HttpHeaders.IF_NONE_MATCH, "\"abc123\"");

        // When: validating against the current ETag
        var matches = validator.validateETag(request, "\"abc123\"");

        // Then: the ETag matches (resource not modified)
        assertThat(matches).isTrue();
        assertThat(validator.isConditionalGet(request)).isTrue();

        // And: the 304 response has the correct status
        var notModified = validator.notModifiedResponse();
        assertThat(notModified.getStatus()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void testCacheValidatorConditionalGetWithLastModified() {
        // Given: a conditional GET request with If-Modified-Since header
        var validator = new CacheValidator();
        var request = HttpRequest.of(HttpMethod.GET, "/resource");
        var lastModified = Instant.parse("2024-01-15T10:30:00Z");
        var ifModifiedSince = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(lastModified.atZone(ZoneOffset.UTC));
        request.getHeaders().set(HttpHeaders.IF_MODIFIED_SINCE, ifModifiedSince);

        // When: validating against the actual last-modified time (same or earlier)
        var notModified = validator.validateLastModified(request, lastModified);

        // Then: resource has not been modified
        assertThat(notModified).isTrue();
    }

    @Test
    void testCacheValidatorNonConditionalGet() {
        // Given: a regular GET request without conditional headers
        var validator = new CacheValidator();
        var request = HttpRequest.of(HttpMethod.GET, "/resource");

        // When: checking if it is a conditional GET
        var isConditional = validator.isConditionalGet(request);

        // Then: it is not a conditional GET
        assertThat(isConditional).isFalse();
    }
}
