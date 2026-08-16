package ssg.legoflow.demos.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.cluster.dns.DnsSdConfig;
import ssg.legoflow.network.cluster.dns.DnsSdServiceRecord;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DNS-SD / mDNS discovery components used in {@link DnsSdDiscoveryDemo}.
 *
 * Tests the underlying configuration and data types rather than the live
 * multicast demo (which may not work in all environments).
 */
class DnsSdDiscoveryDemoTest {

    private static final InetAddress LOCAL;

    static {
        try {
            LOCAL = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testDnsSdConfigBuilder() {
        var config = DnsSdConfig.builder()
                .serviceType("_legoflow._tcp")
                .instanceName("NodeA")
                .port(8001)
                .ttl(Duration.ofSeconds(30))
                .txtAttributes(Map.of("role", "primary", "region", "local"))
                .bindAddress(LOCAL)
                .build();

        assertThat(config.serviceType()).isEqualTo("_legoflow._tcp");
        assertThat(config.instanceName()).isEqualTo("NodeA");
        assertThat(config.port()).isEqualTo(8001);
        assertThat(config.ttl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.txtAttributes()).containsEntry("role", "primary");
        assertThat(config.txtAttributes()).containsEntry("region", "local");
    }

    @Test
    void testDnsSdConfigDefaults() {
        var config = DnsSdConfig.builder()
                .serviceType("_test._tcp")
                .instanceName("test")
                .build();

        assertThat(config.port()).isEqualTo(0);
        assertThat(config.domain()).isEqualTo("local");
        assertThat(config.ttl()).isEqualTo(java.time.Duration.ofSeconds(120));
        assertThat(config.probeCount()).isEqualTo(3);
        assertThat(config.txtAttributes()).isEmpty();
    }

    @Test
    void testServiceRecordConstruction() {
        var record = DnsSdServiceRecord.builder()
                .instanceName("NodeA")
                .serviceType("_legoflow._tcp")
                .domain("local")
                .targetHostname("nodea.local")
                .targetAddress(LOCAL)
                .port(8001)
                .ttl(Duration.ofSeconds(60))
                .txtAttributes(Map.of("node-id", "A"))
                .build();

        assertThat(record.instanceName()).isEqualTo("NodeA");
        assertThat(record.serviceType()).isEqualTo("_legoflow._tcp");
        assertThat(record.port()).isEqualTo(8001);
        assertThat(record.txtAttributes()).containsEntry("node-id", "A");
    }

    @Test
    void testServiceRecordFullyQualifiedName() {
        var record = DnsSdServiceRecord.builder()
                .instanceName("NodeA")
                .serviceType("_legoflow._tcp")
                .domain("local")
                .targetHostname("nodea.local")
                .targetAddress(LOCAL)
                .port(8001)
                .build();

        String fqdn = record.instanceFqdn();
        assertThat(fqdn).contains("NodeA");
        assertThat(fqdn).contains("_legoflow._tcp");
        assertThat(fqdn).contains("local");
    }
}
