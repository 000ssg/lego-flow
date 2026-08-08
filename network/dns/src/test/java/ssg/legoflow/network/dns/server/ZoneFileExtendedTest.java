package ssg.legoflow.network.dns.server;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import ssg.legoflow.network.dns.protocol.DnsFormatException;
import ssg.legoflow.network.dns.protocol.DnsName;

import static org.assertj.core.api.Assertions.*;

/**
 * Extended ZoneFile tests covering edge cases: comments, wildcards, TTL suffixes,
 * CNAME chains, multi-line records, and various record type combinations.
 */
@Timeout(10)
class ZoneFileExtendedTest {

    @Test
    void testZoneWithComments() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. (
                        2024010100 ; serial
                        3600       ; refresh
                        900        ; retry
                        604800     ; expire
                        86400      ; minimum
                )
                @ IN A 1.2.3.4  ; primary server
                www IN CNAME @  ; web alias
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithWildcardRecord() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                * IN A 0.0.0.0
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
        // Wildcard records should be present
        assertThat(parsed.allRecords()).isNotEmpty();
    }

    @Test
    void testZoneWithTtlSuffixes() {
        String zone = """
                $ORIGIN example.com.
                $TTL 1h
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                www IN A 1.2.3.4
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithCnameChain() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                www IN CNAME web.example.com.
                web IN A 1.2.3.4
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithoutSoaThrows() {
        String zone = "$ORIGIN example.com.";

        assertThatThrownBy(() -> ZoneFile.parse(zone))
                .isInstanceOf(DnsFormatException.class)
                .hasMessageContaining("no SOA");
    }

    @Test
    void testZoneWithMultipleTtlFormats() {
        String zone = """
                $ORIGIN example.com.
                $TTL 1d
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                www IN A 5.6.7.8
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithAbsoluteNames() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                www IN NS ns1.other.net.
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithAtOrigin() {
        String zone = """
                $ORIGIN example.com.
                $TTL 7200
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                @ IN A 5.6.7.8
                @ IN MX 10 mail.example.com.
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed.lookup(DnsName.of("example.com."), ssg.legoflow.network.dns.protocol.RecordType.A)).isNotEmpty();
    }

    @Test
    void testZoneWithImplicitName() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                www IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                  IN A 1.2.3.4
                  IN A 5.6.7.8
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithSrvRecords() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                _sip._tcp IN SRV 10 60 5060 sipserver.example.com.
                _https._tcp IN SRV 1 1 443 www.example.com.
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithTxtRecords() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                @ IN TXT "v=spf1 include:_spf.example.com ~all"
                _dmarc IN TXT "v=DMARC1; p=reject"
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithPtrRecords() {
        String zone = """
                $ORIGIN 34.216.184.93.in-addr.arpa.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                @ IN PTR example.com.
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithCaaRecords() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                @ IN CAA 0 issue "letsencrypt.org"
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithAaaaRecords() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                ipv6 IN AAAA 2001:db8::1
                v6site IN AAAA 2606:2800:220:1:248:1893:25c8:1946
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithMultipleNsRecords() {
        String zone = """
                $ORIGIN example.com.
                $TTL 86400
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                @ IN NS ns1.example.com.
                @ IN NS ns2.example.com.
                @ IN NS ns3.other.net.
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithExplicitTtlPerRecord() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                www 7200 IN A 1.2.3.4
                mail IN A 5.6.7.8
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithMultipleMxRecords() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                @ IN MX 10 mail.primary.com.
                @ IN MX 20 mail.backup.com.
                @ IN MX 50 mail.emergency.com.
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithTtlWeekSuffix() {
        String zone = """
                $ORIGIN example.com.
                $TTL 1w
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                www IN A 1.2.3.4
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithTtlSecondsSuffix() {
        String zone = """
                $ORIGIN example.com.
                $TTL 300s
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                mail IN A 10.0.0.1
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithSubdomainRecords() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                api.v1 IN A 1.1.1.1
                api.v2 IN A 2.2.2.2
                db.internal IN A 10.0.0.1
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneWithRelativeNames() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1 admin 2024010100 3600 900 604800 86400
                www IN A 1.2.3.4
                mail IN MX 10 mx
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneEmptyContentThrows() {
        assertThatThrownBy(() -> ZoneFile.parse(""))
                .isInstanceOf(DnsFormatException.class)
                .hasMessageContaining("no SOA");
    }

    @Test
    void testZoneOnlyDirectivesNoRecordsThrows() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                """;

        assertThatThrownBy(() -> ZoneFile.parse(zone))
                .isInstanceOf(DnsFormatException.class)
                .hasMessageContaining("no SOA");
    }

    @Test
    void testZoneWithMultiLineTxtRecord() {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                @ IN TXT (
                        "v=spf1"
                        "include:_spf.google.com"
                        "~all"
                )
                """;

        var parsed = ZoneFile.parse(zone);
        assertThat(parsed).isNotNull();
    }

    @Test
    void testZoneReaderParsing() throws Exception {
        String zone = """
                $ORIGIN example.com.
                $TTL 3600
                @ IN SOA ns1.example.com. admin.example.com. 2024010100 3600 900 604800 86400
                www IN A 1.2.3.4
                """;

        var reader = new java.io.BufferedReader(new java.io.StringReader(zone));
        var parsed = ZoneFile.parse(reader);
        assertThat(parsed).isNotNull();
    }
}
