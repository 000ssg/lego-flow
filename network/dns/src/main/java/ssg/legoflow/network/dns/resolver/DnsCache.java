package ssg.legoflow.network.dns.resolver;

import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.DnsRecord;
import ssg.legoflow.network.dns.protocol.RecordType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TTL-based DNS response cache.
 *
 * <p>Caches DNS resource records keyed by domain name and record type.
 * Entries expire based on the record's TTL. Thread-safe via
 * {@link ConcurrentHashMap}.
 *
 * @since 1.0.0
 */
public final class DnsCache {

    private final Map<CacheKey, List<CacheEntry>> cache = new ConcurrentHashMap<>();
    private final long maxEntries;

    /**
     * Creates a cache with a maximum number of entries.
     *
     * @param maxEntries the maximum number of cache entries
     * @since 1.0.0
     */
    public DnsCache(long maxEntries) {
        this.maxEntries = maxEntries;
    }

    /**
     * Creates a cache with default capacity (10000).
     *
     * @since 1.0.0
     */
    public DnsCache() {
        this(10_000);
    }

    /**
     * Stores records from a DNS response in the cache.
     *
     * @param response the DNS response to cache
     * @since 1.0.0
     */
    public void put(DnsMessage response) {
        cacheRecords(response.answers());
        cacheRecords(response.authority());
        cacheRecords(response.additional());
    }

    /**
     * Stores a single record in the cache.
     *
     * @param record the record to cache
     * @since 1.0.0
     */
    public void put(DnsRecord record) {
        CacheKey key = new CacheKey(record.name(), record.type());
        Instant expiry = Instant.now().plusSeconds(record.ttl());
        cache.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new CacheEntry(record, expiry));
    }

    /**
     * Looks up cached records by name and type.
     *
     * @param name the domain name
     * @param type the record type
     * @return list of cached records, empty if not found or expired
     * @since 1.0.0
     */
    public List<DnsRecord> get(DnsName name, RecordType type) {
        CacheKey key = new CacheKey(name, type);
        List<CacheEntry> entries = cache.get(key);
        if (entries == null) {
            return List.of();
        }

        Instant now = Instant.now();
        List<DnsRecord> result = new ArrayList<>();
        synchronized (entries) {
            Iterator<CacheEntry> it = entries.iterator();
            while (it.hasNext()) {
                CacheEntry entry = it.next();
                if (now.isAfter(entry.expiry)) {
                    it.remove();
                } else {
                    // Adjust TTL to remaining time
                    long remainingTtl = entry.expiry.getEpochSecond() - now.getEpochSecond();
                    result.add(entry.record.withTtl(remainingTtl));
                }
            }
            if (entries.isEmpty()) {
                cache.remove(key);
            }
        }
        return result;
    }

    /**
     * Returns the number of entries in the cache.
     *
     * @return the cache size
     * @since 1.0.0
     */
    public int size() {
        return cache.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Clears all entries from the cache.
     *
     * @since 1.0.0
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Evicts expired entries from the cache.
     *
     * @return the number of evicted entries
     * @since 1.0.0
     */
    public int evictExpired() {
        Instant now = Instant.now();
        int evicted = 0;
        Iterator<Map.Entry<CacheKey, List<CacheEntry>>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<CacheKey, List<CacheEntry>> entry = it.next();
            List<CacheEntry> entries = entry.getValue();
            synchronized (entries) {
                int before = entries.size();
                entries.removeIf(e -> now.isAfter(e.expiry));
                evicted += before - entries.size();
                if (entries.isEmpty()) {
                    it.remove();
                }
            }
        }
        return evicted;
    }

    private void cacheRecords(List<DnsRecord> records) {
        for (DnsRecord record : records) {
            if (record.ttl() > 0 && record.type() != RecordType.OPT) {
                put(record);
            }
        }
    }

    private record CacheKey(DnsName name, RecordType type) {}

    private record CacheEntry(DnsRecord record, Instant expiry) {}
}
