package ssg.legoflow.network.cluster.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClusterConfigTest {

    @Test
    void defaultsForCreatesValidConfig() {
        var config = ClusterConfig.defaultsFor("test-cluster");

        assertThat(config.name()).isEqualTo("test-cluster");
        assertThat(config.heartbeatInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(config.heartbeatFailureThreshold()).isEqualTo(3);
        assertThat(config.joinTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.leaveTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void customValues() {
        var config = ClusterConfig.builder()
                .name("custom")
                .heartbeatInterval(Duration.ofSeconds(5))
                .heartbeatFailureThreshold(5)
                .joinTimeout(Duration.ofSeconds(10))
                .leaveTimeout(Duration.ofSeconds(5))
                .build();

        assertThat(config.name()).isEqualTo("custom");
        assertThat(config.heartbeatInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.heartbeatFailureThreshold()).isEqualTo(5);
        assertThat(config.joinTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.leaveTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void failureTimeoutDerived() {
        var config = ClusterConfig.builder()
                .name("test")
                .heartbeatInterval(Duration.ofSeconds(2))
                .heartbeatFailureThreshold(3)
                .build();

        assertThat(config.failureTimeout()).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void negativeHeartbeatIntervalRejected() {
        assertThatThrownBy(() -> ClusterConfig.builder()
                .name("test")
                .heartbeatInterval(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroHeartbeatIntervalRejected() {
        assertThatThrownBy(() -> ClusterConfig.builder()
                .name("test")
                .heartbeatInterval(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thresholdLessThanOneRejected() {
        assertThatThrownBy(() -> ClusterConfig.builder()
                .name("test")
                .heartbeatFailureThreshold(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeTimeoutRejected() {
        assertThatThrownBy(() -> ClusterConfig.builder()
                .name("test")
                .joinTimeout(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullNameRejected() {
        assertThatThrownBy(() -> ClusterConfig.builder().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalityAndHashCode() {
        var c1 = ClusterConfig.builder()
                .name("a")
                .heartbeatInterval(Duration.ofSeconds(2))
                .build();
        var c2 = ClusterConfig.builder()
                .name("a")
                .heartbeatInterval(Duration.ofSeconds(2))
                .build();
        var c3 = ClusterConfig.builder().name("b").build();

        assertThat(c1).isEqualTo(c2);
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
        assertThat(c1).isNotEqualTo(c3);
    }

    @Test
    void toStringContainsName() {
        var config = ClusterConfig.defaultsFor("my-cluster");
        assertThat(config.toString()).contains("my-cluster");
    }
}
