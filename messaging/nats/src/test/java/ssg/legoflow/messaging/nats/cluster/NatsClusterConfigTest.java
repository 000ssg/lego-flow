package ssg.legoflow.messaging.nats.cluster;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class NatsClusterConfigTest {

    @Test
    void builder_with_defaults() {
        NatsClusterConfig config = NatsClusterConfig.builder().build();
        assertThat(config.serverUrl()).isEqualTo("nats://localhost:4222");
        assertThat(config.clusterId()).isEqualTo("default");
        assertThat(config.heartbeatInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.maxPayloadBytes()).isEqualTo(1024 * 1024);
        assertThat(config.nodeId()).isNotBlank();
    }

    @Test
    void builder_with_custom_values() {
        NatsClusterConfig config = NatsClusterConfig.builder()
                .serverUrl("nats://myhost:5000")
                .clusterId("prod-cluster")
                .nodeId("node-42")
                .heartbeatInterval(Duration.ofSeconds(1))
                .requestTimeout(Duration.ofSeconds(3))
                .maxPayloadBytes(512 * 1024)
                .build();

        assertThat(config.serverUrl()).isEqualTo("nats://myhost:5000");
        assertThat(config.clusterId()).isEqualTo("prod-cluster");
        assertThat(config.nodeId()).isEqualTo("node-42");
        assertThat(config.heartbeatInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(config.maxPayloadBytes()).isEqualTo(512 * 1024);
    }

    @Test
    void builder_rejects_null_values() {
        assertThatThrownBy(() -> NatsClusterConfig.builder().serverUrl(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> NatsClusterConfig.builder().clusterId(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> NatsClusterConfig.builder().nodeId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builder_rejects_invalid_max_payload() {
        assertThatThrownBy(() -> NatsClusterConfig.builder().maxPayloadBytes(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NatsClusterConfig.builder().maxPayloadBytes(-100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void record_equals_and_hashCode() {
        NatsClusterConfig c1 = NatsClusterConfig.builder()
                .serverUrl("nats://a:4222").clusterId("c1").nodeId("n1").build();
        NatsClusterConfig c2 = NatsClusterConfig.builder()
                .serverUrl("nats://a:4222").clusterId("c1").nodeId("n1").build();

        assertThat(c1).isEqualTo(c2);
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
    }
}
