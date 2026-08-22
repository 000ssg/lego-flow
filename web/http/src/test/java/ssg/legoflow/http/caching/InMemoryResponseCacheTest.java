package ssg.legoflow.http.caching;

import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class InMemoryResponseCacheTest {

    @Test
    void testPutAndGet() {
        // Given
        var cache = new InMemoryResponseCache();
        var response = HttpResponse.of(HttpStatus.OK, "cached body");
        var cached = new ResponseCache.CachedResponse(response, System.currentTimeMillis(), 3600);

        // When
        cache.put("/api/data", cached);

        // Then
        assertThat(cache.get("/api/data")).isPresent();
        assertThat(cache.get("/api/data").get().response().getBodyAsString()).isEqualTo("cached body");
    }

    @Test
    void testGetMissingKeyReturnsEmpty() {
        // Given
        var cache = new InMemoryResponseCache();

        // Then
        assertThat(cache.get("/missing")).isEmpty();
    }

    @Test
    void testRemove() {
        // Given
        var cache = new InMemoryResponseCache();
        var response = HttpResponse.of(HttpStatus.OK);
        cache.put("/key", new ResponseCache.CachedResponse(response, System.currentTimeMillis(), 3600));

        // When
        cache.remove("/key");

        // Then
        assertThat(cache.get("/key")).isEmpty();
    }

    @Test
    void testClear() {
        // Given
        var cache = new InMemoryResponseCache();
        var response = HttpResponse.of(HttpStatus.OK);
        cache.put("/a", new ResponseCache.CachedResponse(response, System.currentTimeMillis(), 3600));
        cache.put("/b", new ResponseCache.CachedResponse(response, System.currentTimeMillis(), 3600));

        // When
        cache.clear();

        // Then
        assertThat(cache.size()).isZero();
    }

    @Test
    void testEvictionWhenMaxEntriesExceeded() {
        // Given
        var cache = new InMemoryResponseCache(2);
        var response = HttpResponse.of(HttpStatus.OK);

        // When
        cache.put("/a", new ResponseCache.CachedResponse(response, System.currentTimeMillis(), 3600));
        cache.put("/b", new ResponseCache.CachedResponse(response, System.currentTimeMillis(), 3600));
        cache.put("/c", new ResponseCache.CachedResponse(response, System.currentTimeMillis(), 3600));

        // Then
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void testExpiredEntryRemovedOnGet() {
        // Given
        var cache = new InMemoryResponseCache();
        var response = HttpResponse.of(HttpStatus.OK);
        // Create entry that expired 2 seconds ago (maxAge=0, stored 1 second ago)
        var expired = new ResponseCache.CachedResponse(response, System.currentTimeMillis() - 2000, 0);
        cache.put("/expired", expired);

        // When
        var result = cache.get("/expired");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testSize() {
        // Given
        var cache = new InMemoryResponseCache();
        var response = HttpResponse.of(HttpStatus.OK);

        // Then
        assertThat(cache.size()).isZero();

        // When
        cache.put("/a", new ResponseCache.CachedResponse(response, System.currentTimeMillis(), 3600));

        // Then
        assertThat(cache.size()).isEqualTo(1);
    }
}
