package ssg.legoflow.service.cluster.coordination;

import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class EtcdConfigTest {

    @Test
    void defaultBuilder() {
        EtcdConfig config = EtcdConfig.builder().build();
        assertThat(config.endpoints()).hasSize(1);
        assertThat(config.dialTimeout()).isEqualTo(EtcdConfig.DEFAULT_DIAL_TIMEOUT);
        assertThat(config.requestTimeout()).isEqualTo(EtcdConfig.DEFAULT_REQUEST_TIMEOUT);
        assertThat(config.username()).isNull();
        assertThat(config.password()).isNull();
    }

    @Test
    void customEndpoints() {
        List<InetSocketAddress> endpoints = List.of(
                new InetSocketAddress("10.0.0.1", 2379),
                new InetSocketAddress("10.0.0.2", 2379));

        EtcdConfig config = EtcdConfig.builder()
                .endpoints(endpoints)
                .build();

        assertThat(config.endpoints()).hasSize(2);
    }

    @Test
    void customTimeouts() {
        EtcdConfig config = EtcdConfig.builder()
                .dialTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(20))
                .build();

        assertThat(config.dialTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void withAuth() {
        EtcdConfig config = EtcdConfig.builder()
                .username("admin")
                .password("secret")
                .build();

        assertThat(config.username()).isEqualTo("admin");
        assertThat(config.password()).isEqualTo("secret");
    }

    @Test
    void emptyEndpoints_throws() {
        assertThatThrownBy(() -> EtcdConfig.builder()
                .endpoints(List.of())
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullEndpoints_throws() {
        assertThatThrownBy(() -> EtcdConfig.builder()
                .endpoints(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullDialTimeout_throws() {
        assertThatThrownBy(() -> EtcdConfig.builder()
                .dialTimeout(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullRequestTimeout_throws() {
        assertThatThrownBy(() -> EtcdConfig.builder()
                .requestTimeout(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defaultValues() {
        assertThat(EtcdConfig.DEFAULT_DIAL_TIMEOUT).isEqualTo(Duration.ofSeconds(5));
        assertThat(EtcdConfig.DEFAULT_REQUEST_TIMEOUT).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void fullConfig() {
        List<InetSocketAddress> endpoints = List.of(
                new InetSocketAddress("etcd-1", 2379),
                new InetSocketAddress("etcd-2", 2379));

        EtcdConfig config = EtcdConfig.builder()
                .endpoints(endpoints)
                .dialTimeout(Duration.ofSeconds(3))
                .requestTimeout(Duration.ofSeconds(15))
                .username("root")
                .password("pass123")
                .build();

        assertThat(config.endpoints()).hasSize(2);
        assertThat(config.dialTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(config.username()).isEqualTo("root");
        assertThat(config.password()).isEqualTo("pass123");
    }
}
