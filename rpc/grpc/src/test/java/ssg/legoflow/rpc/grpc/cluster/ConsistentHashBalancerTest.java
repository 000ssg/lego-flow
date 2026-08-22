package ssg.legoflow.rpc.grpc.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;
class ConsistentHashBalancerTest {

    private final ClusterNode node1 = ClusterNode.builder()
            .id("n1").host("127.0.0.1").port(9001).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();
    private final ClusterNode node2 = ClusterNode.builder()
            .id("n2").host("127.0.0.1").port(9002).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();
    private final ClusterNode node3 = ClusterNode.builder()
            .id("n3").host("127.0.0.1").port(9003).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();

    private final List<ClusterSubchannel> channels = List.of(
            ClusterSubchannel.of(node1),
            ClusterSubchannel.of(node2),
            ClusterSubchannel.of(node3)
    );

    @Test
    void same_key_always_routes_to_same_backend() {
        ConsistentHashBalancer balancer = new ConsistentHashBalancer();
        balancer.updateChannels(channels);

        String key = "user-42";
        String first = balancer.select(channels, key).get().node().id();
        String second = balancer.select(channels, key).get().node().id();
        String third = balancer.select(channels, key).get().node().id();

        assertThat(first).isEqualTo(second).isEqualTo(third);
    }

    @Test
    void different_keys_distribute_across_backends() {
        ConsistentHashBalancer balancer = new ConsistentHashBalancer();
        balancer.updateChannels(channels);

        List<String> keys = List.of("key-a", "key-b", "key-c",
                "key-d", "key-e", "key-f", "key-g", "key-h");

        Map<String, Long> distribution = keys.stream()
                .map(k -> balancer.select(channels, k).get().node().id())
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()));

        // With consistent hashing, keys should spread across multiple backends
        assertThat(distribution.size()).isGreaterThan(1);
    }

    @Test
    void falls_back_to_first_healthy_when_no_key() {
        ConsistentHashBalancer balancer = new ConsistentHashBalancer();
        balancer.updateChannels(channels);

        Optional<ClusterSubchannel> selected = balancer.select(channels, null);
        assertThat(selected).isPresent();
        assertThat(selected.get().node().id()).isIn("n1", "n2", "n3");

        selected = balancer.select(channels, "");
        assertThat(selected).isPresent();
    }

    @Test
    void rebalances_on_node_removal() {
        ConsistentHashBalancer balancer = new ConsistentHashBalancer();
        balancer.updateChannels(channels);

        String key = "test-key";
        String before = balancer.select(channels, key).get().node().id();

        // Remove node2
        List<ClusterSubchannel> reduced = List.of(
                ClusterSubchannel.of(node1),
                ClusterSubchannel.of(node3)
        );
        balancer.updateChannels(reduced);

        Optional<ClusterSubchannel> after = balancer.select(reduced, key);
        assertThat(after).isPresent();
        assertThat(after.get().node().id()).isNotEqualTo("n2");
    }

    @Test
    void handles_unhealthy_nodes_in_ring() {
        ConsistentHashBalancer balancer = new ConsistentHashBalancer();
        balancer.updateChannels(channels);

        String key = "sticky-key";
        String original = balancer.select(channels, key).get().node().id();

        // Make n1 unhealthy
        List<ClusterSubchannel> withUnhealthy = List.of(
                ClusterSubchannel.of(node1).withHealth(HealthStatus.NOT_SERVING),
                ClusterSubchannel.of(node2),
                ClusterSubchannel.of(node3)
        );
        balancer.updateChannels(withUnhealthy);

        Optional<ClusterSubchannel> result = balancer.select(withUnhealthy, key);
        // Should fall back to a healthy node
        assertThat(result).isPresent();
        assertThat(result.get().node().id()).isNotEqualTo("n1");
    }

    @Test
    void returns_empty_for_no_channels() {
        ConsistentHashBalancer balancer = new ConsistentHashBalancer();
        balancer.updateChannels(List.of());
        assertThat(balancer.select(List.of(), "key")).isEmpty();
    }

    @Test
    void onCompleted_is_noop() {
        ConsistentHashBalancer balancer = new ConsistentHashBalancer();
        balancer.updateChannels(channels);
        // Should not throw
        balancer.onCompleted(ClusterSubchannel.of(node1));
    }

    @Test
    void name_is_consistent_hashing() {
        assertThat(new ConsistentHashBalancer().name()).isEqualTo("consistent_hashing");
    }
}
