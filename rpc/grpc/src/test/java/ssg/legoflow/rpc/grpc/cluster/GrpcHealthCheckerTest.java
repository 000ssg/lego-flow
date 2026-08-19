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

        var n1Changed = new CountDownLatch(1);
        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> healthState.getOrDefault(nodeId, false),
                (nodeId, failures) -> {
                    if ("n1".equals(nodeId) && failures >= 2) n1Changed.countDown();
                }
        );

        checker.register("n1");
        checker.register("n2");
        checker.start();

        try {
            assertThat(checker.status("n1")).isEqualTo(HealthStatus.SERVING);
            assertThat(checker.status("n2")).isEqualTo(HealthStatus.SERVING);

            // Simulate failure — wait for transition via latch (no Thread.sleep)
            healthState.put("n1", false);
            assertThat(n1Changed.await(2, TimeUnit.SECONDS))
                    .as("n1 status should transition to NOT_SERVING")
                    .isTrue();
            assertThat(checker.status("n1")).isEqualTo(HealthStatus.NOT_SERVING);
        } finally {
            checker.close();
        }
    }

    @Test
    void marks_unreachable_on_exception() throws Exception {
        var n1Changed = new CountDownLatch(1);
        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> { throw new RuntimeException("connection refused"); },
                (nodeId, failures) -> {
                    if ("n1".equals(nodeId) && failures >= 2) n1Changed.countDown();
                }
        );

        checker.register("n1");
        checker.start();

        try {
            assertThat(n1Changed.await(2, TimeUnit.SECONDS))
                    .as("n1 should become UNREACHABLE")
                    .isTrue();
            assertThat(checker.status("n1")).isEqualTo(HealthStatus.UNREACHABLE);
        } finally {
            checker.close();
        }
    }

    @Test
    void recovers_from_failure() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean healthy =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        var failureLatch = new CountDownLatch(1);
        var recoveryLatch = new CountDownLatch(1);
        boolean[] recoveryPhase = {false};

        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> healthy.get(),
                (nodeId, failures) -> {
                    if ("n1".equals(nodeId)) {
                        if (failures >= 2 && !recoveryPhase[0]) {
                            failureLatch.countDown();
                        } else if (failures == 0 && recoveryPhase[0]) {
                            recoveryLatch.countDown();
                        }
                    }
                }
        );

        checker.register("n1");
        checker.start();

        try {
            // Wait for initial NOT_SERVING (healthy=false from start)
            assertThat(failureLatch.await(2, TimeUnit.SECONDS))
                    .as("n1 should become NOT_SERVING")
                    .isTrue();
            assertThat(checker.status("n1")).isEqualTo(HealthStatus.NOT_SERVING);

            // Recover
            recoveryPhase[0] = true;
            healthy.set(true);
            assertThat(recoveryLatch.await(2, TimeUnit.SECONDS))
                    .as("n1 should recover to SERVING")
                    .isTrue();
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
                (nodeId, failures) -> {}
        );
        assertThat(checker.status("unknown")).isEqualTo(HealthStatus.SERVING);
        checker.close();
    }

    @Test
    void unregister_removes_backend() throws Exception {
        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> true,
                (nodeId, failures) -> {}
        );
        checker.register("n1");
        assertThat(checker.status("n1")).isEqualTo(HealthStatus.SERVING);

        checker.unregister("n1");
        assertThat(checker.status("n1")).isEqualTo(HealthStatus.SERVING);
        checker.close();
    }

    @Test
    void start_is_idempotent() throws Exception {
        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> true,
                (nodeId, failures) -> {}
        );
        checker.start();
        checker.start();
        checker.close();
    }

    @Test
    void close_shuts_down_scheduler() throws Exception {
        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> true,
                (nodeId, failures) -> {}
        );
        checker.start();
        checker.close();
        checker.close();
    }
}
