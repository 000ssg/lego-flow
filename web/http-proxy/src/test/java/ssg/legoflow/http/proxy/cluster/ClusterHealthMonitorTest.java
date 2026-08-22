package ssg.legoflow.http.proxy.cluster;

import ssg.legoflow.http.proxy.reverse.BackendServer;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link ClusterHealthMonitor}.
 * Verifies spec: health tracking, threshold-based transitions,
 * flapping prevention, and event delivery.
 */
class ClusterHealthMonitorTest {

    // ── Constructor tests ──

    @Test
    void constructor_null_config_throws() {
        assertThatThrownBy(() -> new ClusterHealthMonitor(null, e -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config");
    }

    @Test
    void constructor_null_listener_throws() {
        var config = ProxyClusterConfig.builder().build();
        assertThatThrownBy(() -> new ClusterHealthMonitor(config, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventListener");
    }

    @Test
    void constructor_registers_initial_backends() {
        var backends = List.of(
                new BackendServer("host1", 8080),
                new BackendServer("host2", 8081)
        );
        var events = new CopyOnWriteArrayList<ClusterHealthMonitor.HealthEvent>();
        var config = ProxyClusterConfig.builder()
                .backends(backends)
                .build();
        var monitor = new ClusterHealthMonitor(config, events::add);

        assertThat(monitor.getAllBackends()).hasSize(2);
        assertThat(monitor.getHealthyBackends()).hasSize(2);
        assertThat(events).extracting(ClusterHealthMonitor.HealthEvent::event)
                .containsOnly(ClusterHealthMonitor.HealthEvent.EventType.ADDED);
    }

    // ── start/stop tests ──

    @Test
    void start_and_stop() {
        var config = ProxyClusterConfig.builder().build();
        var monitor = new ClusterHealthMonitor(config, e -> {});

        assertThat(monitor.isRunning()).isFalse();
        monitor.start();
        assertThat(monitor.isRunning()).isTrue();
        monitor.stop();
        assertThat(monitor.isRunning()).isFalse();
    }

    @Test
    void start_idempotent() {
        var config = ProxyClusterConfig.builder().build();
        var monitor = new ClusterHealthMonitor(config, e -> {});

        monitor.start();
        monitor.start(); // Should not throw
        assertThat(monitor.isRunning()).isTrue();
        monitor.close();
    }

    @Test
    void close_stops_monitor() {
        var config = ProxyClusterConfig.builder().build();
        var monitor = new ClusterHealthMonitor(config, e -> {});

        monitor.start();
        monitor.close();
        assertThat(monitor.isRunning()).isFalse();
    }

    // ── add/remove backend tests ──

    @Test
    void addBackend() {
        var config = ProxyClusterConfig.builder().build();
        var events = new CopyOnWriteArrayList<ClusterHealthMonitor.HealthEvent>();
        var monitor = new ClusterHealthMonitor(config, events::add);

        var backend = new BackendServer("host3", 8082);
        monitor.addBackend(backend);

        assertThat(monitor.getAllBackends()).hasSize(1);
        assertThat(monitor.getHealthyBackends()).hasSize(1);
        assertThat(backend.isHealthy()).isTrue();
        assertThat(events).extracting(ClusterHealthMonitor.HealthEvent::event)
                .contains(ClusterHealthMonitor.HealthEvent.EventType.ADDED);
    }

    @Test
    void addBackend_null_throws() {
        var config = ProxyClusterConfig.builder().build();
        var monitor = new ClusterHealthMonitor(config, e -> {});
        assertThatThrownBy(() -> monitor.addBackend(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void removeBackend() {
        var backends = List.of(new BackendServer("host1", 8080));
        var events = new CopyOnWriteArrayList<ClusterHealthMonitor.HealthEvent>();
        var config = ProxyClusterConfig.builder().backends(backends).build();
        var monitor = new ClusterHealthMonitor(config, events::add);

        monitor.removeBackend("host1:8080");

        assertThat(monitor.getAllBackends()).isEmpty();
        var removeEvents = events.stream()
                .filter(e -> e.event() == ClusterHealthMonitor.HealthEvent.EventType.REMOVED)
                .toList();
        assertThat(removeEvents).hasSize(1);
        assertThat(removeEvents.get(0).backendId()).isEqualTo("host1:8080");
    }

    // ── Health tracking with thresholds ──

    @Test
    void healthy_backend_stays_healthy_on_single_failure() {
        var config = ProxyClusterConfig.builder()
                .unhealthyThreshold(3)
                .build();
        var events = new CopyOnWriteArrayList<ClusterHealthMonitor.HealthEvent>();
        var monitor = new ClusterHealthMonitor(config, events::add);

        var backend = new BackendServer("host1", 8080);
        monitor.addBackend(backend);

        // Single failure should NOT mark unhealthy
        monitor.recordCheck(backend, false);
        assertThat(backend.isHealthy()).isTrue();
        assertThat(events).extracting(ClusterHealthMonitor.HealthEvent::event)
                .doesNotContain(ClusterHealthMonitor.HealthEvent.EventType.UNHEALTHY);
    }

    @Test
    void backend_becomes_unhealthy_after_threshold_failures() {
        var config = ProxyClusterConfig.builder()
                .unhealthyThreshold(3)
                .build();
        var events = new CopyOnWriteArrayList<ClusterHealthMonitor.HealthEvent>();
        var monitor = new ClusterHealthMonitor(config, events::add);

        var backend = new BackendServer("host1", 8080);
        monitor.addBackend(backend);

        // 2 failures → still healthy
        monitor.recordCheck(backend, false);
        monitor.recordCheck(backend, false);
        assertThat(backend.isHealthy()).isTrue();
        assertThat(monitor.getHealthyBackends()).contains(backend);

        // 3rd failure → unhealthy
        monitor.recordCheck(backend, false);
        assertThat(backend.isHealthy()).isFalse();
        assertThat(monitor.getHealthyBackends()).doesNotContain(backend);
        assertThat(events).extracting(ClusterHealthMonitor.HealthEvent::event)
                .contains(ClusterHealthMonitor.HealthEvent.EventType.UNHEALTHY);
    }

    @Test
    void backend_recovers_after_threshold_successes() {
        var config = ProxyClusterConfig.builder()
                .unhealthyThreshold(3)
                .recoveryThreshold(2)
                .build();
        var events = new CopyOnWriteArrayList<ClusterHealthMonitor.HealthEvent>();
        var monitor = new ClusterHealthMonitor(config, events::add);

        var backend = new BackendServer("host1", 8080);
        monitor.addBackend(backend);

        // Mark unhealthy
        monitor.recordCheck(backend, false);
        monitor.recordCheck(backend, false);
        monitor.recordCheck(backend, false);
        assertThat(backend.isHealthy()).isFalse();

        // 1 success → still unhealthy
        monitor.recordCheck(backend, true);
        assertThat(backend.isHealthy()).isFalse();

        // 2nd success → recovered
        monitor.recordCheck(backend, true);
        assertThat(backend.isHealthy()).isTrue();
        assertThat(events).extracting(ClusterHealthMonitor.HealthEvent::event)
                .contains(ClusterHealthMonitor.HealthEvent.EventType.RECOVERED);
    }

    @Test
    void failures_reset_success_counter() {
        var config = ProxyClusterConfig.builder()
                .unhealthyThreshold(3)
                .recoveryThreshold(2)
                .build();
        var events = new CopyOnWriteArrayList<ClusterHealthMonitor.HealthEvent>();
        var monitor = new ClusterHealthMonitor(config, events::add);

        var backend = new BackendServer("host1", 8080);
        monitor.addBackend(backend);

        // Mark unhealthy
        for (int i = 0; i < 3; i++) monitor.recordCheck(backend, false);
        assertThat(backend.isHealthy()).isFalse();

        // 1 success, then failure → still unhealthy
        monitor.recordCheck(backend, true);
        monitor.recordCheck(backend, false);

        // Need 2 fresh successes
        monitor.recordCheck(backend, true);
        assertThat(backend.isHealthy()).isFalse();
        monitor.recordCheck(backend, true);
        assertThat(backend.isHealthy()).isTrue();
    }

    @Test
    void successes_reset_failure_counter() {
        var config = ProxyClusterConfig.builder()
                .unhealthyThreshold(3)
                .build();
        var events = new CopyOnWriteArrayList<ClusterHealthMonitor.HealthEvent>();
        var monitor = new ClusterHealthMonitor(config, events::add);

        var backend = new BackendServer("host1", 8080);
        monitor.addBackend(backend);

        // 2 failures, then success resets
        monitor.recordCheck(backend, false);
        monitor.recordCheck(backend, false);
        monitor.recordCheck(backend, true);

        // Now need 3 fresh failures to go unhealthy
        monitor.recordCheck(backend, false);
        assertThat(backend.isHealthy()).isTrue();
        monitor.recordCheck(backend, false);
        assertThat(backend.isHealthy()).isTrue();
        monitor.recordCheck(backend, false);
        assertThat(backend.isHealthy()).isFalse();
    }

    @Test
    void getHealthyBackends_only_healthy() {
        var config = ProxyClusterConfig.builder()
                .unhealthyThreshold(1)
                .build();
        var monitor = new ClusterHealthMonitor(config, e -> {});

        var healthy = new BackendServer("host1", 8080);
        var unhealthy = new BackendServer("host2", 8081);
        monitor.addBackend(healthy);
        monitor.addBackend(unhealthy);

        // Mark one unhealthy
        monitor.recordCheck(unhealthy, false);

        assertThat(monitor.getHealthyBackends()).containsExactly(healthy);
        assertThat(monitor.getAllBackends()).containsExactly(healthy, unhealthy);
    }

    // ── Event delivery ──

    @Test
    void event_delivery_catches_exceptions() {
        var config = ProxyClusterConfig.builder().build();
        var monitor = new ClusterHealthMonitor(config, e -> {
            throw new RuntimeException("listener error");
        });

        var backend = new BackendServer("host1", 8080);
        // Should not throw even though listener throws
        monitor.addBackend(backend);
    }

    @Test
    void event_contains_correct_backend_id() {
        var config = ProxyClusterConfig.builder().build();
        var events = new CopyOnWriteArrayList<ClusterHealthMonitor.HealthEvent>();
        var monitor = new ClusterHealthMonitor(config, events::add);

        var backend = new BackendServer("myhost", 9090);
        monitor.addBackend(backend);

        var addedEvent = events.stream()
                .filter(e -> e.event() == ClusterHealthMonitor.HealthEvent.EventType.ADDED)
                .findFirst().orElseThrow();
        assertThat(addedEvent.backendId()).isEqualTo("myhost:9090");
    }

    // ── ProxyClusterConfig tests ──

    @Test
    void proxy_config_defaults() {
        var config = ProxyClusterConfig.builder().build();
        assertThat(config.backends()).isEmpty();
        assertThat(config.healthCheckPath()).isEqualTo("/health");
        assertThat(config.healthInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.unhealthyThreshold()).isEqualTo(3);
        assertThat(config.recoveryThreshold()).isEqualTo(2);
    }

    @Test
    void proxy_config_custom_values() {
        var config = ProxyClusterConfig.builder()
                .healthCheckPath("/ping")
                .healthInterval(Duration.ofSeconds(5))
                .unhealthyThreshold(5)
                .recoveryThreshold(3)
                .build();

        assertThat(config.healthCheckPath()).isEqualTo("/ping");
        assertThat(config.healthInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.unhealthyThreshold()).isEqualTo(5);
        assertThat(config.recoveryThreshold()).isEqualTo(3);
    }

    @Test
    void proxy_config_unhealthy_threshold_below_1_throws() {
        assertThatThrownBy(() -> ProxyClusterConfig.builder().unhealthyThreshold(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    void proxy_config_recovery_threshold_below_1_throws() {
        assertThatThrownBy(() -> ProxyClusterConfig.builder().recoveryThreshold(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    void proxy_config_null_backends_throws() {
        assertThatThrownBy(() -> ProxyClusterConfig.builder().backends(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void proxy_config_null_healthCheckPath_throws() {
        assertThatThrownBy(() -> ProxyClusterConfig.builder().healthCheckPath(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void proxy_config_null_healthInterval_throws() {
        assertThatThrownBy(() -> ProxyClusterConfig.builder().healthInterval(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void proxy_config_record_equality() {
        var b1 = List.of(new BackendServer("h", 8080));
        var c1 = ProxyClusterConfig.builder()
                .backends(b1)
                .healthCheckPath("/ping")
                .unhealthyThreshold(2)
                .build();
        var c2 = ProxyClusterConfig.builder()
                .backends(b1)
                .healthCheckPath("/ping")
                .unhealthyThreshold(2)
                .build();
        assertThat(c1).isEqualTo(c2);
    }
}
