package ssg.legoflow.rpc.grpc.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterRole;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LeastRequestBalancerTest {

    private final ClusterNode node1 = ClusterNode.builder()
            .id("n1").host("127.0.0.1").port(9001).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();
    private final ClusterNode node2 = ClusterNode.builder()
            .id("n2").host("127.0.0.1").port(9002).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();
    private final ClusterNode node3 = ClusterNode.builder()
            .id("n3").host("127.0.0.1").port(9003).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();

    @Test
    void selects_backend_with_fewest_in_flight() {
        LeastRequestBalancer balancer = new LeastRequestBalancer();
        List<ClusterSubchannel> channels = List.of(
                ClusterSubchannel.of(node1).inFlightInc().inFlightInc().inFlightInc(),
                ClusterSubchannel.of(node2),
                ClusterSubchannel.of(node3).inFlightInc()
        );
        balancer.updateChannels(channels);

        Optional<ClusterSubchannel> selected = balancer.select(channels, null);
        assertThat(selected).isPresent();
        assertThat(selected.get().node().id()).isEqualTo("n2");
    }

    @Test
    void skips_unhealthy_backends_even_with_zero_in_flight() {
        LeastRequestBalancer balancer = new LeastRequestBalancer();
        List<ClusterSubchannel> channels = List.of(
                ClusterSubchannel.of(node1).inFlightInc().inFlightInc(),
                ClusterSubchannel.of(node2).withHealth(HealthStatus.NOT_SERVING),
                ClusterSubchannel.of(node3).inFlightInc()
        );
        balancer.updateChannels(channels);

        Optional<ClusterSubchannel> selected = balancer.select(channels, null);
        assertThat(selected).isPresent();
        assertThat(selected.get().node().id()).isEqualTo("n3");
    }

    @Test
    void returns_empty_when_no_healthy_backends() {
        LeastRequestBalancer balancer = new LeastRequestBalancer();
        List<ClusterSubchannel> channels = List.of(
                ClusterSubchannel.of(node1).withHealth(HealthStatus.UNREACHABLE),
                ClusterSubchannel.of(node2).withHealth(HealthStatus.NOT_SERVING)
        );
        balancer.updateChannels(channels);

        assertThat(balancer.select(channels, null)).isEmpty();
    }

    @Test
    void handles_equal_in_flight_counts() {
        LeastRequestBalancer balancer = new LeastRequestBalancer();
        List<ClusterSubchannel> channels = List.of(
                ClusterSubchannel.of(node1).inFlightInc().inFlightInc(),
                ClusterSubchannel.of(node2).inFlightInc().inFlightInc()
        );
        balancer.updateChannels(channels);

        Optional<ClusterSubchannel> selected = balancer.select(channels, null);
        assertThat(selected).isPresent();
        assertThat(selected.get().node().id()).isIn("n1", "n2");
    }

    @Test
    void name_is_least_request() {
        assertThat(new LeastRequestBalancer().name()).isEqualTo("least_request");
    }
}
