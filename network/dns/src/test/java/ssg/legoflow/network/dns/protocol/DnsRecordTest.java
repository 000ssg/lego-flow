package ssg.legoflow.network.dns.protocol;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.rdata.ARecord;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class DnsRecordTest {

    @Test
    void testOfWithDnsName() {
        ARecord a = ARecord.of("1.2.3.4");
        DnsRecord r = DnsRecord.of(DnsName.of("example.com"), 300, a);
        
        assertThat(r.name()).isEqualTo(DnsName.of("example.com"));
        assertThat(r.type()).isEqualTo(RecordType.A);
        assertThat(r.recordClass()).isEqualTo(RecordClass.IN);
        assertThat(r.ttl()).isEqualTo(300);
    }

    @Test
    void testOfWithStringName() {
        ARecord a = ARecord.of("5.6.7.8");
        DnsRecord r = DnsRecord.of("test.com", 60, a);
        
        assertThat(r.name().toString()).isEqualTo("test.com");
    }

    @Test
    void testWithTtl() {
        ARecord a = ARecord.of("1.2.3.4");
        DnsRecord r1 = DnsRecord.of("example.com", 300, a);
        DnsRecord r2 = r1.withTtl(600);
        
        assertThat(r1.ttl()).isEqualTo(300);
        assertThat(r2.ttl()).isEqualTo(600);
        assertThat(r2.name()).isEqualTo(r1.name());
    }

    @Test
    void testNullNameThrows() {
        assertThatThrownBy(() -> new DnsRecord(null, RecordType.A, RecordClass.IN, 300, ARecord.of("1.2.3.4")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullTypeThrows() {
        assertThatThrownBy(() -> new DnsRecord(DnsName.of("x.com"), null, RecordClass.IN, 300, ARecord.of("1.2.3.4")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullRdataThrows() {
        assertThatThrownBy(() -> new DnsRecord(DnsName.of("x.com"), RecordType.A, RecordClass.IN, 300, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testFullConstructor() {
        ARecord a = ARecord.of("1.2.3.4");
        DnsRecord r = new DnsRecord(DnsName.of("custom.com"), RecordType.A, RecordClass.CH, 120, a);
        
        assertThat(r.recordClass()).isEqualTo(RecordClass.CH);
        assertThat(r.ttl()).isEqualTo(120);
    }
}
