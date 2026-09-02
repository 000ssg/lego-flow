package ssg.legoflow.http.proxy.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
class InMemoryProxyCacheStoreTest {

    private InMemoryProxyCacheStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryProxyCacheStore(5, 1024);
    }

    @Test
    void testPutAndGet() {
        var entry = createEntry("test body", 60000);
        store.put("key1", entry);
        var result = store.get("key1");
        assertThat(result).isPresent();
        assertThat(result.get().statusCode()).isEqualTo(200);
    }

    @Test
    void testGetMissing() {
        var result = store.get("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void testGetExpired() {
        var entry = createEntry("expired", -1000); // already expired
        store.put("key1", entry);
        var result = store.get("key1");
        assertThat(result).isEmpty();
    }

    @Test
    void testRemove() {
        store.put("key1", createEntry("body", 60000));
        store.remove("key1");
        assertThat(store.get("key1")).isEmpty();
        assertThat(store.size()).isEqualTo(0);
    }

    @Test
    void testClear() {
        store.put("key1", createEntry("body1", 60000));
        store.put("key2", createEntry("body2", 60000));
        store.clear();
        assertThat(store.size()).isEqualTo(0);
        assertThat(store.sizeInBytes()).isEqualTo(0);
    }

    @Test
    void testSize() {
        assertThat(store.size()).isEqualTo(0);
        store.put("key1", createEntry("body1", 60000));
        assertThat(store.size()).isEqualTo(1);
        store.put("key2", createEntry("body2", 60000));
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void testSizeInBytes() {
        byte[] body = "hello".getBytes();
        var entry = new ProxyCacheStore.CacheEntry(200, Map.of(), body,
                null, null, System.currentTimeMillis(),
                System.currentTimeMillis() + 60000);
        store.put("key1", entry);
        assertThat(store.sizeInBytes()).isEqualTo(5);
    }

    @Test
    void testLruEvictionByEntryCount() {
        store = new InMemoryProxyCacheStore(3, 1024 * 1024);
        store.put("a", createEntry("a", 60000));
        store.put("b", createEntry("b", 60000));
        store.put("c", createEntry("c", 60000));
        store.put("d", createEntry("d", 60000)); // should evict "a"
        assertThat(store.size()).isEqualTo(3);
        assertThat(store.get("a")).isEmpty(); // evicted
        assertThat(store.get("d")).isPresent();
    }

    @Test
    void testLruEvictionBySizeBytes() {
        store = new InMemoryProxyCacheStore(100, 15); // 15 bytes max
        store.put("a", createEntry("12345", 60000)); // 5 bytes
        store.put("b", createEntry("12345", 60000)); // 5 bytes
        store.put("c", createEntry("12345", 60000)); // 5 bytes = 15
        store.put("d", createEntry("12345", 60000)); // should evict oldest
        assertThat(store.sizeInBytes()).isLessThanOrEqualTo(15);
    }

    @Test
    void testHitCount() {
        store.put("key1", createEntry("body", 60000));
        store.get("key1");
        store.get("key1");
        assertThat(store.getHitCount()).isEqualTo(2);
    }

    @Test
    void testMissCount() {
        store.get("missing1");
        store.get("missing2");
        assertThat(store.getMissCount()).isEqualTo(2);
    }

    @Test
    void testHitRatio() {
        store.put("key1", createEntry("body", 60000));
        store.get("key1"); // hit
        store.get("missing"); // miss
        assertThat(store.getHitRatio()).isEqualTo(0.5);
    }

    @Test
    void testHitRatioZeroRequests() {
        assertThat(store.getHitRatio()).isEqualTo(0.0);
    }

    @Test
    void testEvictionCount() {
        store = new InMemoryProxyCacheStore(2, 1024 * 1024);
        store.put("a", createEntry("a", 60000));
        store.put("b", createEntry("b", 60000));
        store.put("c", createEntry("c", 60000)); // evicts "a"
        assertThat(store.getEvictionCount()).isEqualTo(1);
    }

    @Test
    void testReplaceSameKey() {
        store.put("key1", createEntry("old", 60000));
        store.put("key1", createEntry("new", 60000));
        assertThat(store.size()).isEqualTo(1);
        var result = store.get("key1");
        assertThat(result).isPresent();
    }

    @Test
    void testGetMaxEntries() {
        assertThat(store.getMaxEntries()).isEqualTo(5);
    }

    @Test
    void testGetMaxSizeBytes() {
        assertThat(store.getMaxSizeBytes()).isEqualTo(1024);
    }

    @Test
    void testDefaultConstructor() {
        var defaultStore = new InMemoryProxyCacheStore();
        assertThat(defaultStore.getMaxEntries()).isEqualTo(10_000);
        assertThat(defaultStore.getMaxSizeBytes()).isEqualTo(64 * 1024 * 1024);
    }

    @Test
    void testCacheEntryIsExpired() {
        var expired = new ProxyCacheStore.CacheEntry(200, Map.of(), new byte[0],
                null, null, 0, System.currentTimeMillis() - 1000);
        assertThat(expired.isExpired()).isTrue();

        var fresh = new ProxyCacheStore.CacheEntry(200, Map.of(), new byte[0],
                null, null, 0, System.currentTimeMillis() + 60000);
        assertThat(fresh.isExpired()).isFalse();
    }

    @Test
    void testCacheEntryBodySize() {
        var entry = new ProxyCacheStore.CacheEntry(200, Map.of(), "hello".getBytes(),
                null, null, 0, Long.MAX_VALUE);
        assertThat(entry.bodySize()).isEqualTo(5);

        var nullBody = new ProxyCacheStore.CacheEntry(200, Map.of(), null,
                null, null, 0, Long.MAX_VALUE);
        assertThat(nullBody.bodySize()).isEqualTo(0);
    }

    private ProxyCacheStore.CacheEntry createEntry(String body, long ttlMs) {
        long now = System.currentTimeMillis();
        return new ProxyCacheStore.CacheEntry(200, Map.of(), body.getBytes(),
                "\"etag\"", null, now, now + ttlMs);
    }
}
