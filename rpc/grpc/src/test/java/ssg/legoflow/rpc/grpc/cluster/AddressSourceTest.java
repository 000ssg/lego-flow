package ssg.legoflow.rpc.grpc.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterRole;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
class AddressSourceTest {

    private final ClusterNode node1 = ClusterNode.builder()
            .id("n1").host("127.0.0.1").port(9001).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();
    private final ClusterNode node2 = ClusterNode.builder()
            .id("n2").host("127.0.0.2").port(9002).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();

    @Test
    void static_source_resolves_fixed_nodes() {
        List<ClusterNode> nodes = List.of(node1, node2);
        AddressSource source = AddressSource.staticSource(nodes);

        List<ClusterNode> result = source.resolve("my-service");
        assertThat(result).containsExactly(node1, node2);
    }

    @Test
    void static_source_delivers_initial_list_to_listener() {
        List<ClusterNode> nodes = List.of(node1, node2);
        AddressSource source = AddressSource.staticSource(nodes);

        List<ClusterNode> received = new ArrayList<>();
        AtomicInteger callCount = new AtomicInteger(0);
        source.onAddressesChanged("my-service", list -> {
            received.addAll(list);
            callCount.incrementAndGet();
        });

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(received).containsExactly(node1, node2);
    }

    @Test
    void addressOf_returns_socket_address() {
        InetSocketAddress addr = AddressSource.addressOf(node1);
        assertThat(addr.getHostString()).isEqualTo("127.0.0.1");
        assertThat(addr.getPort()).isEqualTo(9001);
    }

    @Test
    void static_source_ignores_service_name() {
        List<ClusterNode> nodes = List.of(node1);
        AddressSource source = AddressSource.staticSource(nodes);

        assertThat(source.resolve("svc-a")).isEqualTo(nodes);
        assertThat(source.resolve("svc-b")).isEqualTo(nodes);
    }

    @Test
    void static_source_returns_same_list_instance() {
        List<ClusterNode> nodes = List.of(node1, node2);
        AddressSource source = AddressSource.staticSource(nodes);

        // Each resolve returns a copy (List.copyOf)
        List<ClusterNode> result1 = source.resolve("svc");
        List<ClusterNode> result2 = source.resolve("svc");
        assertThat(result1).isEqualTo(result2);
    }
}
