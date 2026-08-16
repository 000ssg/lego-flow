package ssg.legoflow.rpc.grpc.cluster;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcHealthCheckerTest {

    @Test
    void starts_and_checks_backends() throws Exception {
        Map<String, Boolean> healthState = new ConcurrentHashMap<>();
        healthState.put("n1", true);
        healthState.put("n2", true);

        CountDownLatch latch = new CountDownLatch(1);
        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> healthState.getOrDefault(nodeId, false),
                i -> latch.countDown()
        );

        checker.register("n1");
        checker.register("n2");
        checker.start();

        try {
            assertThat(checker.status("n1")).isEqualTo(HealthStatus.SERVING);
            assertThat(checker.status("n2")).isEqualTo(HealthStatus.SERVING);

            // Simulate failure
            healthState.put("n1", false);
            // Wait for two consecutive failures
            Thread.sleep(200);
            assertThat(checker.status("n1")).isEqualTo(HealthStatus.NOT_SERVING);
        } finally {
            checker.close();
        }
    }

    @Test
    void marks_unreachable_on_exception() throws Exception {
        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> { throw new RuntimeException("connection refused"); },
                i -> {}
        );

        checker.register("n1");
        checker.start();

        try {
            Thread.sleep(200);
            assertThat(checker.status("n1")).isEqualTo(HealthStatus.UNREACHABLE);
        } finally {
            checker.close();
        }
    }

    @Test
    void recovers_from_failure() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean healthy = new java.util.concurrent.atomic.AtomicBoolean(false);

        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> healthy.get(),
                i -> {}
        );

        checker.register("n1");
        checker.start();

        try {
            Thread.sleep(150);
            assertThat(checker.status("n1")).isEqualTo(HealthStatus.NOT_SERVING);

            // Recover
            healthy.set(true);
            Thread.sleep(150);
            assertThat(checker.status("n1")).isEqualTo(HealthStatus.SERVING);
        } finally {
            checker.close();
        }
    }

    @Test
    void unknown_node_defaults_to_serving() {
        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> true,
                i -> {}
        );
        assertThat(checker.status("unknown")).isEqualTo(HealthStatus.SERVING);
        checker.close();
    }

    @Test
    void unregister_removes_backend() throws Exception {
        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> true,
                i -> {}
        );
        checker.register("n1");
        assertThat(checker.status("n1")).isEqualTo(HealthStatus.SERVING);

        checker.unregister("n1");
        assertThat(checker.status("n1")).isEqualTo(HealthStatus.SERVING); // defaults to SERVING
        checker.close();
    }

    @Test
    void start_is_idempotent() throws Exception {
        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> true,
                i -> {}
        );
        checker.start();
        // Second start should not throw
        checker.start();
        checker.close();
    }

    @Test
    void close_shuts_down_scheduler() throws Exception {
        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> true,
                i -> {}
        );
        checker.start();
        checker.close();
        // Second close should not throw
        checker.close();
    }
}
