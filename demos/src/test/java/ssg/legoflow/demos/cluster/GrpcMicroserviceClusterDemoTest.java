package ssg.legoflow.demos.cluster;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link GrpcMicroserviceClusterDemo}.
 *
 * Verifies gRPC cluster load balancing:
 * - Round-robin distributes evenly across backends
 * - Least-request balances by active connections
 * - Consistent hashing routes same key to same backend
 * - Health monitoring detects failures and recoveries
 * - Failed backends are excluded from routing
 */
class GrpcMicroserviceClusterDemoTest {

    @Test
    void testDemoRunsSuccessfully() throws Exception {
        var demo = new GrpcMicroserviceClusterDemo();
        var result = demo.run();

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
    }

    @Test
    void testRoundRobinDistribution() throws Exception {
        var demo = new GrpcMicroserviceClusterDemo();
        var result = demo.run();

        @SuppressWarnings("unchecked")
        Map<String, Integer> dist = (Map<String, Integer>) result.get("round_robin_distribution");
        assertThat(dist).isNotNull();
        assertThat(dist).hasSizeBetween(3, 5);

        // 20 requests should be roughly evenly distributed
        int total = dist.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(20);
    }

    @Test
    void testRoundRobinEvenDistribution() throws Exception {
        var demo = new GrpcMicroserviceClusterDemo();
        var result = demo.run();

        boolean even = (boolean) result.get("round_robin_even");
        assertThat(even).isTrue();
    }

    @Test
    void testLeastRequestDistribution() throws Exception {
        var demo = new GrpcMicroserviceClusterDemo();
        var result = demo.run();

        @SuppressWarnings("unchecked")
        Map<String, Integer> dist = (Map<String, Integer>) result.get("least_request_distribution");
        assertThat(dist).isNotNull();
        assertThat(dist).isNotEmpty();

        int total = dist.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(15);
    }

    @Test
    void testConsistentHashRouting() throws Exception {
        var demo = new GrpcMicroserviceClusterDemo();
        var result = demo.run();

        @SuppressWarnings("unchecked")
        Map<String, String> routes = (Map<String, String>) result.get("consistent_hash_routes");
        assertThat(routes).isNotNull();
        assertThat(routes).hasSize(5);

        // Each user should be routed to exactly one backend
        for (String user : routes.keySet()) {
            assertThat(routes.get(user)).isNotBlank();
        }
    }

    @Test
    void testHashConsistency() throws Exception {
        var demo = new GrpcMicroserviceClusterDemo();
        var result = demo.run();

        boolean consistent = (boolean) result.get("hash_consistency");
        assertThat(consistent).isTrue();
    }

    @Test
    void testHealthyBackendsAfterFailure() throws Exception {
        var demo = new GrpcMicroserviceClusterDemo();
        var result = demo.run();

        int healthyCount = (int) result.get("healthy_after_failure_count");
        assertThat(healthyCount).isEqualTo(4);
    }

    @Test
    void testBackendExcludedAfterFailure() throws Exception {
        var demo = new GrpcMicroserviceClusterDemo();
        var result = demo.run();

        boolean excluded = (boolean) result.get("backend3_excluded");
        assertThat(excluded).isTrue();
    }

    @Test
    void testBackendRecovery() throws Exception {
        var demo = new GrpcMicroserviceClusterDemo();
        var result = demo.run();

        int healthyAfterRecovery = (int) result.get("healthy_after_recovery_count");
        boolean allAvailable = (boolean) result.get("all_backends_available_again");
        assertThat(healthyAfterRecovery).isEqualTo(5);
        assertThat(allAvailable).isTrue();
    }

    @Test
    void testHealthEventsRecorded() throws Exception {
        var demo = new GrpcMicroserviceClusterDemo();
        var result = demo.run();

        @SuppressWarnings("unchecked")
        Map<String, Integer> events = (Map<String, Integer>) result.get("health_events");
        assertThat(events).isNotNull();
        assertThat(events).isNotEmpty();
    }
}
