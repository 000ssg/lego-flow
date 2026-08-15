package ssg.legoflow.network.cluster.dns;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class DnsSdConfigTest {

    @Test
    void defaultsFor_createsValidConfig() {
        DnsSdConfig config = DnsSdConfig.defaultsFor("_http._tcp", "MyServer", 8080);

        assertThat(config.serviceType()).isEqualTo("_http._tcp");
        assertThat(config.domain()).isEqualTo("local");
        assertThat(config.instanceName()).isEqualTo("MyServer");
        assertThat(config.port()).isEqualTo(8080);
        assertThat(config.txtAttributes()).isEmpty();
        assertThat(config.ttl()).isEqualTo(Duration.ofSeconds(120));
        assertThat(config.bindAddress()).isNull();
        assertThat(config.probeCount()).isEqualTo(3);
        assertThat(config.probeInterval()).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void serviceDomain_returnsCorrectFormat() {
        DnsSdConfig config = DnsSdConfig.defaultsFor("_grpc._tcp", "test", 50051);
        assertThat(config.serviceDomain()).isEqualTo("_grpc._tcp.local");
    }

    @Test
    void instanceFqdn_returnsCorrectFormat() {
        DnsSdConfig config = DnsSdConfig.defaultsFor("_http._tcp", "MyServer", 8080);
        assertThat(config.instanceFqdn()).isEqualTo("MyServer._http._tcp.local");
    }

    @Test
    void builder_createsCustomConfig() {
        DnsSdConfig config = DnsSdConfig.builder()
                .serviceType("_nats._tcp")
                .domain("local")
                .instanceName("nats-node-1")
                .port(4222)
                .addTxtAttribute("node_id", "abc-123")
                .addTxtAttribute("version", "0.2.0")
                .ttl(Duration.ofSeconds(60))
                .probeCount(5)
                .probeInterval(Duration.ofMillis(100))
                .build();

        assertThat(config.serviceType()).isEqualTo("_nats._tcp");
        assertThat(config.instanceName()).isEqualTo("nats-node-1");
        assertThat(config.port()).isEqualTo(4222);
        assertThat(config.txtAttributes()).containsEntry("node_id", "abc-123");
        assertThat(config.txtAttributes()).containsEntry("version", "0.2.0");
        assertThat(config.ttl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.probeCount()).isEqualTo(5);
        assertThat(config.probeInterval()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void builder_txtAttributes_immutable() {
        DnsSdConfig config = DnsSdConfig.builder()
                .serviceType("_http._tcp")
                .instanceName("srv")
                .port(80)
                .addTxtAttribute("key", "value")
                .build();

        assertThatThrownBy(() -> config.txtAttributes().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullServiceType_throws() {
        assertThatThrownBy(() -> new DnsSdConfig(null, "local", "name", 80,
                java.util.Map.of(), Duration.ofSeconds(60), null, 3, Duration.ofMillis(250)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankServiceType_throws() {
        assertThatThrownBy(() -> DnsSdConfig.defaultsFor("  ", "name", 80))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullInstanceName_throws() {
        assertThatThrownBy(() -> DnsSdConfig.defaultsFor("_http._tcp", null, 80))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void negativePort_throws() {
        assertThatThrownBy(() -> DnsSdConfig.defaultsFor("_http._tcp", "name", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void portExceedsRange_throws() {
        assertThatThrownBy(() -> DnsSdConfig.defaultsFor("_http._tcp", "name", 65536))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeTtl_throws() {
        assertThatThrownBy(() -> DnsSdConfig.builder()
                .serviceType("_http._tcp")
                .instanceName("name")
                .port(80)
                .ttl(Duration.ofSeconds(-1))
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroTtl_throws() {
        assertThatThrownBy(() -> DnsSdConfig.builder()
                .serviceType("_http._tcp")
                .instanceName("name")
                .port(80)
                .ttl(Duration.ZERO)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroProbeCount_throws() {
        assertThatThrownBy(() -> DnsSdConfig.builder()
                .serviceType("_http._tcp")
                .instanceName("name")
                .port(80)
                .probeCount(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
