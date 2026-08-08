package ssg.legoflow.network.dns.resolver;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.*;
import ssg.legoflow.network.dns.rdata.ARecord;

import java.util.List;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class DnsCacheTest {

    @Test
    void testPutAndGet() {
        DnsCache cache = new DnsCache();
        DnsName name = DnsName.of("example.com");
        ARecord a = ARecord.of("1.2.3.4");
        DnsRecord record = DnsRecord.of(name, 300, a);
        
        cache.put(record);
        List<DnsRecord> results = cache.get(name, RecordType.A);
        
        assertThat(results).hasSize(1);
        assertThat(((ARecord) results.get(0).rdata()).address().getHostAddress()).isEqualTo("1.2.3.4");
    }

    @Test
    void testPutMessage() {
        DnsCache cache = new DnsCache();
        DnsMessage response = DnsMessage.builder()
                .qr(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.A))
                .addAnswer(DnsRecord.of("example.com", 300, ARecord.of("1.2.3.4")))
                .addAnswer(DnsRecord.of("example.com", 300, ARecord.of("5.6.7.8")))
                .build();
        
        cache.put(response);
        assertThat(cache.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testGetNonExistent() {
        DnsCache cache = new DnsCache();
        List<DnsRecord> results = cache.get(DnsName.of("nonexistent.com"), RecordType.A);
        assertThat(results).isEmpty();
    }

    @Test
    void testExpiration() throws InterruptedException {
        DnsCache cache = new DnsCache();
        DnsRecord record = DnsRecord.of("fast.com", 1, ARecord.of("1.2.3.4"));
        cache.put(record);
        
        assertThat(cache.get(DnsName.of("fast.com"), RecordType.A)).hasSize(1);
        
        Thread.sleep(1100); // Wait for expiration
        
        assertThat(cache.get(DnsName.of("fast.com"), RecordType.A)).isEmpty();
    }

    @Test
    void testSize() {
        DnsCache cache = new DnsCache();
        assertThat(cache.size()).isZero();
        
        cache.put(DnsRecord.of("x1.com", 300, ARecord.of("1.1.1.1")));
        cache.put(DnsRecord.of("x2.com", 300, ARecord.of("2.2.2.2")));
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void testClear() {
        DnsCache cache = new DnsCache();
        cache.put(DnsRecord.of("x.com", 300, ARecord.of("1.2.3.4")));
        cache.clear();
        assertThat(cache.size()).isZero();
    }

    @Test
    void testEvictExpired() throws InterruptedException {
        DnsCache cache = new DnsCache();
        cache.put(DnsRecord.of("fast.com", 1, ARecord.of("1.2.3.4")));
        cache.put(DnsRecord.of("slow.com", 3600, ARecord.of("5.6.7.8")));
        
        Thread.sleep(1100);
        int evicted = cache.evictExpired();
        
        assertThat(evicted).isEqualTo(1);
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void testZeroTtlNotCached() {
        DnsCache cache = new DnsCache();
        // Records with TTL=0 should not be cached when put via message
        DnsMessage response = DnsMessage.builder()
                .qr(true)
                .addAnswer(DnsRecord.of("zero.com", 0, ARecord.of("1.2.3.4")))
                .build();
        
        cache.put(response);
        assertThat(cache.size()).isZero();
    }

    @Test
    void testDefaultConstructor() {
        DnsCache cache = new DnsCache();
        // Should create with 10000 max entries
        assertThatNoException().isThrownBy(() -> cache.put(DnsRecord.of("x.com", 60, ARecord.of("1.2.3.4"))));
    }

    @Test
    void testTtlAdjustment() {
        DnsCache cache = new DnsCache();
        DnsRecord record = DnsRecord.of("example.com", 10, ARecord.of("1.2.3.4"));
        cache.put(record);
        
        List<DnsRecord> cached = cache.get(DnsName.of("example.com"), RecordType.A);
        assertThat(cached).hasSize(1);
        // TTL should be <= original (adjusted to remaining time)
        assertThat(cached.get(0).ttl()).isLessThanOrEqualTo(10);
    }

    @Test
    void testMultipleRecordsSameKey() {
        DnsCache cache = new DnsCache();
        cache.put(DnsRecord.of("multi.com", 300, ARecord.of("1.1.1.1")));
        cache.put(DnsRecord.of("multi.com", 300, ARecord.of("2.2.2.2")));
        cache.put(DnsRecord.of("multi.com", 300, ARecord.of("3.3.3.3")));
        
        List<DnsRecord> results = cache.get(DnsName.of("multi.com"), RecordType.A);
        assertThat(results).hasSize(3);
    }

    @Test
    void testDifferentTypesSameName() {
        DnsCache cache = new DnsCache();
        cache.put(DnsRecord.of("example.com", 300, ARecord.of("1.2.3.4")));
        cache.put(DnsRecord.of("example.com", 300, ssg.legoflow.network.dns.rdata.AaaaRecord.of("::1")));
        
        assertThat(cache.get(DnsName.of("example.com"), RecordType.A)).hasSize(1);
        assertThat(cache.get(DnsName.of("example.com"), RecordType.AAAA)).hasSize(1);
    }
}
