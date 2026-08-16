package ssg.legoflow.rpc.grpc.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterRole;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcLoadBalancerTest {

    private final ClusterNode node1 = ClusterNode.builder()
            .id("n1").host("127.0.0.1").port(9001).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();
    private final ClusterNode node2 = ClusterNode.builder()
            .id("n2").host("127.0.0.1").port(9002).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();
    private final ClusterNode node3 = ClusterNode.builder()
            .id("n3").host("127.0.0.1").port(9003).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();

    private final ClusterSubchannel ch1 = ClusterSubchannel.of(node1);
    private final ClusterSubchannel ch2 = ClusterSubchannel.of(node2);
    private final ClusterSubchannel ch3 = ClusterSubchannel.of(node3);

    private final List<ClusterSubchannel> channels = List.of(ch1, ch2, ch3);

    @Test
    void roundRobin_returns_balancer() {
        GrpcLoadBalancer balancer = GrpcLoadBalancer.roundRobin();
        assertThat(balancer).isInstanceOf(RoundRobinBalancer.class);
        assertThat(balancer.name()).isEqualTo("round_robin");
    }

    @Test
    void leastRequest_returns_balancer() {
        GrpcLoadBalancer balancer = GrpcLoadBalancer.leastRequest();
        assertThat(balancer).isInstanceOf(LeastRequestBalancer.class);
        assertThat(balancer.name()).isEqualTo("least_request");
    }

    @Test
    void consistentHash_returns_balancer() {
        GrpcLoadBalancer balancer = GrpcLoadBalancer.consistentHash();
        assertThat(balancer).isInstanceOf(ConsistentHashBalancer.class);
        assertThat(balancer.name()).isEqualTo("consistent_hashing");
    }

    @Test
    void all_balancers_select_from_channels() {
        List<GrpcLoadBalancer> balancers = List.of(
                GrpcLoadBalancer.roundRobin(),
                GrpcLoadBalancer.leastRequest(),
                GrpcLoadBalancer.consistentHash()
        );

        for (GrpcLoadBalancer balancer : balancers) {
            balancer.updateChannels(channels);
            Optional<ClusterSubchannel> selected = balancer.select(channels, null);
            assertThat(selected).isPresent();
            assertThat(selected.get().node().id()).isIn("n1", "n2", "n3");
        }
    }

    @Test
    void all_balancers_return_empty_for_no_channels() {
        List<GrpcLoadBalancer> balancers = List.of(
                GrpcLoadBalancer.roundRobin(),
                GrpcLoadBalancer.leastRequest(),
                GrpcLoadBalancer.consistentHash()
        );

        for (GrpcLoadBalancer balancer : balancers) {
            balancer.updateChannels(List.of());
            assertThat(balancer.select(List.of(), null)).isEmpty();
        }
    }

    @Test
    void sealed_interface_permits_only_known_implementations() {
        // Verifies the sealed hierarchy via factory methods
        assertThat(GrpcLoadBalancer.roundRobin()).isInstanceOf(RoundRobinBalancer.class);
        assertThat(GrpcLoadBalancer.leastRequest()).isInstanceOf(LeastRequestBalancer.class);
        assertThat(GrpcLoadBalancer.consistentHash()).isInstanceOf(ConsistentHashBalancer.class);
    }
}
