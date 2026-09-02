package ssg.legoflow.rpc.grpc.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterRole;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
class RoundRobinBalancerTest {

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
    void distributes_requests_round_robin() {
        RoundRobinBalancer balancer = new RoundRobinBalancer();
        balancer.updateChannels(channels);

        // Select 9 requests; each node should get exactly 3 in round-robin order
        List<String> selected = IntStream.range(0, 9)
                .mapToObj(i -> balancer.select(channels, null).get().node().id())
                .collect(Collectors.toList());

        assertThat(selected).containsExactly("n1", "n2", "n3", "n1", "n2", "n3", "n1", "n2", "n3");
    }

    @Test
    void skips_unhealthy_backends() {
        RoundRobinBalancer balancer = new RoundRobinBalancer();

        List<ClusterSubchannel> channels = List.of(
                ClusterSubchannel.of(node1),
                ClusterSubchannel.of(node2).withHealth(HealthStatus.NOT_SERVING),
                ClusterSubchannel.of(node3)
        );
        balancer.updateChannels(channels);

        List<String> selected = IntStream.range(0, 4)
                .mapToObj(i -> balancer.select(channels, null).get().node().id())
                .collect(Collectors.toList());

        assertThat(selected).doesNotContain("n2");
        assertThat(selected).containsOnly("n1", "n3");
    }

    @Test
    void returns_empty_when_all_unhealthy() {
        RoundRobinBalancer balancer = new RoundRobinBalancer();
        List<ClusterSubchannel> unhealthy = List.of(
                ClusterSubchannel.of(node1).withHealth(HealthStatus.NOT_SERVING),
                ClusterSubchannel.of(node2).withHealth(HealthStatus.NOT_SERVING)
        );
        balancer.updateChannels(unhealthy);

        Optional<ClusterSubchannel> result = balancer.select(unhealthy, null);
        assertThat(result).isEmpty();
    }

    @Test
    void handles_single_backend() {
        RoundRobinBalancer balancer = new RoundRobinBalancer();
        List<ClusterSubchannel> single = List.of(ClusterSubchannel.of(node1));
        balancer.updateChannels(single);

        for (int i = 0; i < 5; i++) {
            assertThat(balancer.select(single, null)).hasValueSatisfying(ch ->
                    assertThat(ch.node().id()).isEqualTo("n1"));
        }
    }

    @Test
    void handles_channel_updates() {
        RoundRobinBalancer balancer = new RoundRobinBalancer();
        balancer.updateChannels(List.of(ClusterSubchannel.of(node1)));
        assertThat(balancer.select(List.of(), null)).isNotEmpty();

        ClusterNode node4 = ClusterNode.builder()
                .id("n4").host("127.0.0.1").port(9004).role(ClusterRole.BOTH)
                .status(ClusterNodeStatus.ACTIVE).build();
        balancer.updateChannels(List.of(ClusterSubchannel.of(node1), ClusterSubchannel.of(node4)));

        List<String> ids = IntStream.range(0, 4)
                .mapToObj(i -> balancer.select(channels, null).get().node().id())
                .collect(Collectors.toList());
        assertThat(ids).containsOnly("n1", "n4");
    }

    @Test
    void name_is_round_robin() {
        assertThat(new RoundRobinBalancer().name()).isEqualTo("round_robin");
    }

    @Test
    void handles_empty_initial_channels() {
        RoundRobinBalancer balancer = new RoundRobinBalancer();
        assertThat(balancer.select(List.of(), null)).isEmpty();
    }
}
