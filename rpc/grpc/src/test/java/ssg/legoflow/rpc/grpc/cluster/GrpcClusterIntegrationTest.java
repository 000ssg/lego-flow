package ssg.legoflow.rpc.grpc.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterRole;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests combining AddressSource, GrpcHealthChecker, and load balancers.
 */
class GrpcClusterIntegrationTest {

    private final ClusterNode node1 = ClusterNode.builder()
            .id("n1").host("127.0.0.1").port(9001).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();
    private final ClusterNode node2 = ClusterNode.builder()
            .id("n2").host("127.0.0.2").port(9002).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();
    private final ClusterNode node3 = ClusterNode.builder()
            .id("n3").host("127.0.0.3").port(9003).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();

    private final List<ClusterNode> nodes = List.of(node1, node2, node3);

    @Test
    void round_robin_with_health_checking() throws Exception {
        Map<String, Boolean> healthState = new ConcurrentHashMap<>(Map.of(
                "n1", true, "n2", true, "n3", true
        ));

        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> healthState.getOrDefault(nodeId, false),
                i -> {}
        );
        nodes.forEach(n -> checker.register(n.id()));
        checker.start();

        try {
            GrpcLoadBalancer balancer = GrpcLoadBalancer.roundRobin();
            balancer.updateChannels(buildChannels(healthState, checker));

            // All healthy — round robin
            List<String> selected = IntStream.range(0, 6)
                    .mapToObj(i -> {
                        var channels = buildChannels(healthState, checker);
                        return balancer.select(channels, null).get().node().id();
                    })
                    .collect(Collectors.toList());
            assertThat(selected).containsOnly("n1", "n2", "n3");

            // Simulate n2 failure
            healthState.put("n2", false);
            Thread.sleep(150);

            var channels = buildChannels(healthState, checker);
            Optional<ClusterSubchannel> result = balancer.select(channels, null);
            assertThat(result.get().node().id()).isNotEqualTo("n2");
        } finally {
            checker.close();
        }
    }

    @Test
    void consistent_hash_with_health_checking() throws Exception {
        Map<String, Boolean> healthState = new ConcurrentHashMap<>(Map.of(
                "n1", true, "n2", true, "n3", true
        ));

        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> healthState.getOrDefault(nodeId, false),
                i -> {}
        );
        nodes.forEach(n -> checker.register(n.id()));
        checker.start();

        try {
            GrpcLoadBalancer balancer = GrpcLoadBalancer.consistentHash();

            var channels = buildChannels(healthState, checker);
            balancer.updateChannels(channels);

            // Same key → same node
            String nodeId1 = balancer.select(channels, "user-1").get().node().id();
            String nodeId2 = balancer.select(channels, "user-1").get().node().id();
            assertThat(nodeId1).isEqualTo(nodeId2);

            // Simulate n1 failure
            healthState.put("n1", false);
            Thread.sleep(150);

            channels = buildChannels(healthState, checker);
            balancer.updateChannels(channels);
            Optional<ClusterSubchannel> result = balancer.select(channels, "user-1");
            assertThat(result).isPresent();
            assertThat(result.get().node().id()).isNotEqualTo("n1");
        } finally {
            checker.close();
        }
    }

    @Test
    void least_request_with_address_source() {
        AddressSource source = AddressSource.staticSource(nodes);
        GrpcLoadBalancer balancer = GrpcLoadBalancer.leastRequest();

        List<ClusterNode> resolved = source.resolve("my-service");
        assertThat(resolved).hasSize(3);

        List<ClusterSubchannel> channels = resolved.stream()
                .map(ClusterSubchannel::of)
                .collect(Collectors.toList());
        balancer.updateChannels(channels);

        Optional<ClusterSubchannel> selected = balancer.select(channels, null);
        assertThat(selected).isPresent();
    }

    @Test
    void failover_scenario() throws Exception {
        Map<String, Boolean> healthState = new ConcurrentHashMap<>(Map.of(
                "n1", true, "n2", true, "n3", true
        ));

        GrpcHealthChecker checker = new GrpcHealthChecker(
                Duration.ofMillis(50), 2,
                nodeId -> healthState.getOrDefault(nodeId, false),
                i -> {}
        );
        nodes.forEach(n -> checker.register(n.id()));
        checker.start();

        try {
            GrpcLoadBalancer balancer = GrpcLoadBalancer.roundRobin();

            // Phase 1: all healthy
            var ch1 = buildChannels(healthState, checker);
            balancer.updateChannels(ch1);
            assertThat(balancer.select(ch1, null)).isPresent();

            // Phase 2: n1 and n3 fail
            healthState.put("n1", false);
            healthState.put("n3", false);
            Thread.sleep(200);

            var ch2 = buildChannels(healthState, checker);
            balancer.updateChannels(ch2);
            Optional<ClusterSubchannel> result = balancer.select(ch2, null);
            assertThat(result).isPresent();
            assertThat(result.get().node().id()).isEqualTo("n2"); // only healthy one

            // Phase 3: all fail
            healthState.put("n2", false);
            Thread.sleep(200);

            var ch3 = buildChannels(healthState, checker);
            balancer.updateChannels(ch3);
            assertThat(balancer.select(ch3, null)).isEmpty();

            // Phase 4: n2 recovers
            healthState.put("n2", true);
            Thread.sleep(200);

            var ch4 = buildChannels(healthState, checker);
            balancer.updateChannels(ch4);
            result = balancer.select(ch4, null);
            assertThat(result).isPresent();
            assertThat(result.get().node().id()).isEqualTo("n2");
        } finally {
            checker.close();
        }
    }

    private List<ClusterSubchannel> buildChannels(Map<String, Boolean> healthState,
                                                    GrpcHealthChecker checker) {
        return nodes.stream()
                .map(n -> {
                    HealthStatus health = checker.status(n.id());
                    ClusterSubchannel ch = ClusterSubchannel.of(n).withHealth(health);
                    return ch;
                })
                .collect(Collectors.toList());
    }
}
