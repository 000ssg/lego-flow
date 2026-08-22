package ssg.legoflow.network.dns.server;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.*;
import ssg.legoflow.network.dns.rdata.ARecord;
import ssg.legoflow.network.dns.rdata.SoaRecord;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class AuthoritativeZoneTest {

    @Test
    void testCreate() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        assertThat(zone.origin().toString()).isEqualTo("example.com");
        assertThat(zone.soa()).isNotNull();
    }

    @Test
    void testAddAndGetRecord() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        DnsRecord record = DnsRecord.of("www.example.com", 300, ARecord.of("1.2.3.4"));
        zone.addRecord(record);
        
        var results = zone.lookup(DnsName.of("www.example.com"), RecordType.A);
        assertThat(results).hasSize(1);
    }

    @Test
    void testSoaLookup() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 42L, 3600, 900, 604800, 86400);
        
        var soaRecords = zone.lookup(DnsName.of("example.com"), RecordType.SOA);
        assertThat(soaRecords).hasSize(1);
        assertThat(((SoaRecord) soaRecords.get(0).rdata()).serial()).isEqualTo(42L);
    }

    @Test
    void testNullOriginThrows() {
        SoaRecord soa = SoaRecord.of("ns.com", "admin.com", 1, 3600, 900, 604800, 86400);
        assertThatThrownBy(() -> new AuthoritativeZone(null, soa))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullSoaThrows() {
        assertThatThrownBy(() -> new AuthoritativeZone(DnsName.of("x.com"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testHandleQueryInZone() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        DnsRecord record = DnsRecord.of("www.example.com", 300, ARecord.of("1.2.3.4"));
        zone.addRecord(record);
        
        DnsMessage query = DnsMessage.query("www.example.com", RecordType.A);
        DnsMessage response = zone.handleQuery(query);
        
        assertThat(response.header().qr()).isTrue();
    }

    @Test
    void testHandleNxDomain() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        DnsMessage query = DnsMessage.query("nonexistent.example.com", RecordType.A);
        DnsMessage response = zone.handleQuery(query);
        
        assertThat(response.header().rCode()).isEqualTo(ResponseCode.NXDOMAIN);
    }

    @Test
    void testHandleSoaQuery() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 42L, 3600, 900, 604800, 86400);
        
        DnsMessage query = DnsMessage.query("example.com", RecordType.SOA);
        DnsMessage response = zone.handleQuery(query);
        
        assertThat(response.answers()).hasSize(1);
        assertThat(response.answers().get(0).type()).isEqualTo(RecordType.SOA);
    }

    @Test
    void testMultipleRecords() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        zone.addRecord(DnsRecord.of("x.example.com", 60, ARecord.of("1.1.1.1")));
        zone.addRecord(DnsRecord.of("x.example.com", 60, ARecord.of("2.2.2.2")));
        zone.addRecord(DnsRecord.of("x.example.com", 60, ARecord.of("3.3.3.3")));
        
        var records = zone.lookup(DnsName.of("x.example.com"), RecordType.A);
        assertThat(records).hasSize(3);
    }

    @Test
    void testAddAConvenience() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        zone.addA("www.example.com", 300, "1.2.3.4");
        var records = zone.lookup(DnsName.of("www.example.com"), RecordType.A);
        assertThat(records).hasSize(1);
    }

    @Test
    void testAddAAAAConvenience() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        zone.addAAAA("www.example.com", 300, "2001:db8::1");
        var records = zone.lookup(DnsName.of("www.example.com"), RecordType.AAAA);
        assertThat(records).hasSize(1);
    }

    @Test
    void testAddMXConvenience() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        zone.addMX("example.com", 3600, 10, "mail.example.com");
        var records = zone.lookup(DnsName.of("example.com"), RecordType.MX);
        assertThat(records).hasSize(1);
    }

    @Test
    void testAddTXTConvenience() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        zone.addTXT("example.com", 3600, "v=spf1 ~all");
        var records = zone.lookup(DnsName.of("example.com"), RecordType.TXT);
        assertThat(records).hasSize(1);
    }

    @Test
    void testNameExists() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        zone.addA("www.example.com", 300, "1.2.3.4");
        assertThat(zone.nameExists(DnsName.of("www.example.com"))).isTrue();
        assertThat(zone.nameExists(DnsName.of("nonexistent.example.com"))).isFalse();
    }

    @Test
    void testAllRecords() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        zone.addA("www.example.com", 300, "1.2.3.4");
        var all = zone.allRecords();
        assertThat(all).hasSizeGreaterThanOrEqualTo(2); // SOA + A record
    }

    @Test
    void testNsRecords() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        zone.addNS("example.com", 86400, "ns1.example.com");
        var ns = zone.nsRecords();
        assertThat(ns).isNotEmpty();
    }

    @Test
    void testLookupNonExistentType() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        var result = zone.lookup(DnsName.of("www.example.com"), RecordType.AAAA);
        assertThat(result).isEmpty();
    }

    @Test
    void testAddSRVConvenience() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        zone.addSRV("_sip._tcp.example.com", 300, 10, 60, 5060, "sipserver.example.com");
        var records = zone.lookup(DnsName.of("_sip._tcp.example.com"), RecordType.SRV);
        assertThat(records).hasSize(1);
    }

    @Test
    void testAddCNAMEConvenience() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        zone.addCNAME("www.example.com", 300, "host.example.com");
        var records = zone.lookup(DnsName.of("www.example.com"), RecordType.CNAME);
        assertThat(records).hasSize(1);
    }

    @Test
    void testAddNSConvenience() {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        
        zone.addNS("example.com", 86400, "ns2.example.com");
        var records = zone.lookup(DnsName.of("example.com"), RecordType.NS);
        assertThat(records).hasSize(1);
    }
}
