package ssg.legoflow.network.dns.server;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.*;

import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class ZoneFileTest {

    @Test
    void testParseZoneWithOrigin() {
        String zoneContent = 
            "$ORIGIN example.com.\n" +
            "$TTL 7200\n" +
            "@ IN SOA ns1. admin. 1 3600 900 604800 86400\n" +
            "@ IN NS ns1.\n" +
            "ns1 IN A 10.0.0.1\n";
        
        AuthoritativeZone zone = ZoneFile.parse(zoneContent);
        assertThat(zone.origin().toString()).isEqualTo("example.com");
    }

    @Test
    void testParseZoneWithMxRecords() {
        String zoneContent = 
            "$ORIGIN example.com.\n" +
            "@ IN SOA ns1. admin. 1 3600 900 604800 86400\n" +
            "@ IN MX 10 mail1.\n" +
            "@ IN MX 20 mail2.\n";
        
        AuthoritativeZone zone = ZoneFile.parse(zoneContent);
        var records = zone.lookup(DnsName.of("example.com"), RecordType.MX);
        assertThat(records).hasSize(2);
    }

    @Test
    void testParseZoneWithCname() {
        String zoneContent = 
            "$ORIGIN example.com.\n" +
            "@ IN SOA ns1. admin. 1 3600 900 604800 86400\n" +
            "www IN CNAME host.\n";
        
        AuthoritativeZone zone = ZoneFile.parse(zoneContent);
        var records = zone.lookup(DnsName.of("www.example.com"), RecordType.CNAME);
        assertThat(records).hasSize(1);
    }

    @Test
    void testParseZoneWithTxt() {
        String zoneContent = 
            "$ORIGIN example.com.\n" +
            "@ IN SOA ns1. admin. 1 3600 900 604800 86400\n" +
            "@ IN TXT \"v=spf1 ~all\"\n";
        
        AuthoritativeZone zone = ZoneFile.parse(zoneContent);
        var records = zone.lookup(DnsName.of("example.com"), RecordType.TXT);
        assertThat(records).hasSize(1);
    }

    @Test  
    void testParseZoneWithAaaa() {
        String zoneContent = 
            "$ORIGIN example.com.\n" +
            "@ IN SOA ns1. admin. 1 3600 900 604800 86400\n" +
            "host IN AAAA 2001:db8::1\n";
        
        AuthoritativeZone zone = ZoneFile.parse(zoneContent);
        var records = zone.lookup(DnsName.of("host.example.com"), RecordType.AAAA);
        assertThat(records).hasSize(1);
    }

    @Test
    void testParseZoneWithSrv() {
        String zoneContent = 
            "$ORIGIN example.com.\n" +
            "@ IN SOA ns1. admin. 1 3600 900 604800 86400\n" +
            "_sip._tcp IN SRV 10 60 5060 sipserver.\n";
        
        AuthoritativeZone zone = ZoneFile.parse(zoneContent);
        var records = zone.lookup(DnsName.of("_sip._tcp.example.com"), RecordType.SRV);
        assertThat(records).hasSize(1);
    }

    @Test
    void testParseZoneWithMultipleRecords() {
        String zoneContent = 
            "$ORIGIN example.com.\n" +
            "$TTL 3600\n" +
            "@ IN SOA ns1. admin. 2024010100 3600 900 604800 86400\n" +
            "@ IN NS ns1.\n" +
            "@ IN A 192.0.2.1\n" +
            "www IN CNAME @\n" +
            "mail IN A 192.0.2.2\n";
        
        AuthoritativeZone zone = ZoneFile.parse(zoneContent);
        assertThat(zone.lookup(DnsName.of("example.com"), RecordType.A)).hasSize(1);
        assertThat(zone.lookup(DnsName.of("www.example.com"), RecordType.CNAME)).hasSize(1);
        assertThat(zone.lookup(DnsName.of("mail.example.com"), RecordType.A)).hasSize(1);
    }

    @Test
    void testParseEmptyThrows() {
        String zoneContent = "; just a comment\n";
        assertThatThrownBy(() -> ZoneFile.parse(zoneContent))
                .isInstanceOf(ssg.legoflow.network.dns.protocol.DnsFormatException.class);
    }

    @Test
    void testParseWithInlineComments() {
        String zoneContent = 
            "$ORIGIN example.com.\n" +
            "@ IN SOA ns1. admin. 1 3600 900 604800 86400\n" +
            "@ IN A 192.0.2.1 ; web server IP\n";
        
        AuthoritativeZone zone = ZoneFile.parse(zoneContent);
        var records = zone.lookup(DnsName.of("example.com"), RecordType.A);
        assertThat(records).hasSize(1);
    }

    @Test
    void testParseWithCaaRecords() {
        String zoneContent = 
            "$ORIGIN example.com.\n" +
            "@ IN SOA ns1. admin. 1 3600 900 604800 86400\n" +
            "@ IN CAA 0 issue \"letsencrypt.org\"\n";
        
        AuthoritativeZone zone = ZoneFile.parse(zoneContent);
        var records = zone.lookup(DnsName.of("example.com"), RecordType.CAA);
        assertThat(records).hasSize(1);
    }

    @Test
    void testParseWithNsRecords() {
        String zoneContent = 
            "$ORIGIN example.com.\n" +
            "@ IN SOA ns1. admin. 1 3600 900 604800 86400\n" +
            "@ IN NS ns1.\n" +
            "@ IN NS ns2.\n";
        
        AuthoritativeZone zone = ZoneFile.parse(zoneContent);
        var records = zone.lookup(DnsName.of("example.com"), RecordType.NS);
        assertThat(records).hasSize(2);
    }



    @Test
    void testParseFromReaderWithMultipleRecords() {
        String zoneContent = 
            "$ORIGIN example.org.\n" +
            "@ IN SOA ns1. admin. 2025010101 3600 900 604800 86400\n" +
            "@ IN A 10.0.0.1\n";
        
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.StringReader(zoneContent));
        AuthoritativeZone zone = ZoneFile.parse(reader);
        assertThat(zone.origin().toString()).isEqualTo("example.org");
        assertThat(zone.lookup(DnsName.of("example.org"), RecordType.A)).hasSize(1);
    }
}
