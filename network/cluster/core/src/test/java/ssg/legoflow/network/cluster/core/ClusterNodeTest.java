package ssg.legoflow.network.cluster.core;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class ClusterNodeTest {

    @Test
    void builderCreatesNode() {
        var node = ClusterNode.builder()
                .id("node-1")
                .host("192.168.1.1")
                .port(8080)
                .role(ClusterRole.SERVER)
                .addMetadata("region", "us-east")
                .build();

        assertThat(node.id()).isEqualTo("node-1");
        assertThat(node.host()).isEqualTo("192.168.1.1");
        assertThat(node.port()).isEqualTo(8080);
        assertThat(node.role()).isEqualTo(ClusterRole.SERVER);
        assertThat(node.status()).isEqualTo(ClusterNodeStatus.ACTIVE);
        assertThat(node.metadata()).containsEntry("region", "us-east");
    }

    @Test
    void fromAddressCreatesNode() throws Exception {
        var addr = InetAddress.getByName("127.0.0.1");
        var node = ClusterNode.fromAddress(addr, 9090, ClusterRole.CLIENT);

        assertThat(node.host()).isEqualTo("127.0.0.1");
        assertThat(node.port()).isEqualTo(9090);
        assertThat(node.role()).isEqualTo(ClusterRole.CLIENT);
        assertThat(node.id()).isNotNull();
        assertThat(node.id()).isNotEmpty();
    }

    @Test
    void equalityBasedOnId() {
        var node1 = ClusterNode.builder().id("a").build();
        var node2 = ClusterNode.builder()
                .id("a")
                .host("different")
                .port(9999)
                .build();
        var node3 = ClusterNode.builder().id("b").build();

        assertThat(node1).isEqualTo(node2);
        assertThat(node1).isNotEqualTo(node3);
        assertThat(node1.hashCode()).isEqualTo(node2.hashCode());
    }

    @Test
    void immutabilityOfMetadata() {
        var node = ClusterNode.builder()
                .addMetadata("key", "value")
                .build();

        assertThatThrownBy(() -> node.metadata().put("new", "data"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withStatusValidTransition() {
        var node = ClusterNode.builder().id("x").build();
        var suspect = node.withStatus(ClusterNodeStatus.SUSPECT);

        assertThat(suspect.id()).isEqualTo("x");
        assertThat(suspect.status()).isEqualTo(ClusterNodeStatus.SUSPECT);
        assertThat(node.status()).isEqualTo(ClusterNodeStatus.ACTIVE); // original unchanged
    }

    @Test
    void withStatusInvalidTransition() {
        var node = ClusterNode.builder()
                .id("x")
                .status(ClusterNodeStatus.FAILED)
                .build();

        assertThatThrownBy(() -> node.withStatus(ClusterNodeStatus.SUSPECT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid transition");
    }

    @Test
    void withStatusChain() {
        var node = ClusterNode.builder().id("x").build();
        var suspect = node.withStatus(ClusterNodeStatus.SUSPECT);
        var failed = suspect.withStatus(ClusterNodeStatus.FAILED);

        assertThat(failed.status()).isEqualTo(ClusterNodeStatus.FAILED);
    }

    @Test
    void withStatusRecovery() {
        var node = ClusterNode.builder()
                .id("x")
                .status(ClusterNodeStatus.FAILED)
                .build();

        var recovered = node.withStatus(ClusterNodeStatus.ACTIVE);
        assertThat(recovered.status()).isEqualTo(ClusterNodeStatus.ACTIVE);
    }

    @Test
    void portValidation() {
        assertThatThrownBy(() -> ClusterNode.builder().port(-1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ClusterNode.builder().port(65536))
                .isInstanceOf(IllegalArgumentException.class);

        var node = ClusterNode.builder().port(0).build();
        assertThat(node.port()).isZero();

        node = ClusterNode.builder().port(65535).build();
        assertThat(node.port()).isEqualTo(65535);
    }

    @Test
    void nullFieldRejection() {
        assertThatThrownBy(() -> ClusterNode.builder().id(null).build())
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> ClusterNode.builder().host((String)null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void toStringContainsKeyInfo() {
        var node = ClusterNode.builder()
                .id("node-1")
                .host((String)"10.0.0.1")
                .port(8080)
                .role(ClusterRole.SERVER)
                .build();

        var str = node.toString();
        assertThat(str).contains("node-1");
        assertThat(str).contains("10.0.0.1");
        assertThat(str).contains("8080");
        assertThat(str).contains("SERVER");
    }
}
