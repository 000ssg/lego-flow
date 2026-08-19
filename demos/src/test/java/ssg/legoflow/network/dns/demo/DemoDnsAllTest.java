package ssg.legoflow.network.dns.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive DNS demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code DnsServer}. To test against
 * an external BIND/Unbound/CoreDNS/PowerDNS, set {@code DemoDnsAll.USE_EXTERNAL = true}
 * and configure host/port before running.</p>
 */
class DemoDnsAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoDnsAll.runAll();

        assertThat(results.aRecordQuery())
                .as("A record query returns IPv4 addresses")
                .isGreaterThanOrEqualTo(1);

        assertThat(results.aaaaRecordQuery())
                .as("AAAA record query returns IPv6 addresses")
                .isGreaterThanOrEqualTo(1);

        assertThat(results.mxRecordQuery())
                .as("MX record query returns mail exchanges")
                .isGreaterThanOrEqualTo(2);

        assertThat(results.txtRecordQuery())
                .as("TXT record query returns text records")
                .isGreaterThanOrEqualTo(2);

        assertThat(results.srvRecordQuery())
                .as("SRV record query returns service records")
                .isGreaterThanOrEqualTo(1);

        assertThat(results.authoritativeZone())
                .as("Authoritative zone handles queries and NXDOMAIN")
                .isTrue();

        assertThat(results.zoneFileParsing())
                .as("Zone file parsing extracts records")
                .isGreaterThanOrEqualTo(8);

        assertThat(results.cachingResolver())
                .as("Caching resolver caches and serves results")
                .isTrue();

        assertThat(results.wildcardRecords())
                .as("Wildcard records match arbitrary subdomains")
                .isTrue();

        assertThat(results.cnameChasing())
                .as("CNAME resolution returns alias record")
                .isTrue();
    }
}
