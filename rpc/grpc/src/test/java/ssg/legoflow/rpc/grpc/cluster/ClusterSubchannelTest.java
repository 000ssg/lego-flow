package ssg.legoflow.rpc.grpc.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterRole;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterSubchannelTest {

    private final ClusterNode node = ClusterNode.builder()
            .id("n1").host("127.0.0.1").port(9001).role(ClusterRole.BOTH)
            .status(ClusterNodeStatus.ACTIVE).build();

    @Test
    void of_creates_serving_channel() {
        ClusterSubchannel ch = ClusterSubchannel.of(node);
        assertThat(ch.node()).isEqualTo(node);
        assertThat(ch.health()).isEqualTo(HealthStatus.SERVING);
        assertThat(ch.inFlight()).isZero();
        assertThat(ch.isHealthy()).isTrue();
    }

    @Test
    void withHealth_updates_status() {
        ClusterSubchannel ch = ClusterSubchannel.of(node);
        ClusterSubchannel updated = ch.withHealth(HealthStatus.NOT_SERVING);
        assertThat(updated.health()).isEqualTo(HealthStatus.NOT_SERVING);
        assertThat(updated.isHealthy()).isFalse();
        assertThat(ch.isHealthy()).isTrue(); // original unchanged (immutable)
    }

    @Test
    void inFlightInc_increments_count() {
        ClusterSubchannel ch = ClusterSubchannel.of(node);
        ClusterSubchannel inc1 = ch.inFlightInc();
        assertThat(inc1.inFlight()).isEqualTo(1);
        assertThat(ch.inFlight()).isZero(); // original unchanged

        ClusterSubchannel inc2 = inc1.inFlightInc();
        assertThat(inc2.inFlight()).isEqualTo(2);
    }

    @Test
    void inFlightDec_decrements_count() {
        ClusterSubchannel ch = ClusterSubchannel.of(node).inFlightInc().inFlightInc();
        assertThat(ch.inFlight()).isEqualTo(2);

        ClusterSubchannel dec = ch.inFlightDec();
        assertThat(dec.inFlight()).isEqualTo(1);
        assertThat(ch.inFlight()).isEqualTo(2); // original unchanged
    }

    @Test
    void inFlightDec_does_not_go_negative() {
        ClusterSubchannel ch = ClusterSubchannel.of(node);
        assertThat(ch.inFlight()).isZero();
        ClusterSubchannel dec = ch.inFlightDec();
        assertThat(dec.inFlight()).isZero();
    }

    @Test
    void health_status_enum_values() {
        assertThat(HealthStatus.values()).containsExactly(
                HealthStatus.SERVING,
                HealthStatus.NOT_SERVING,
                HealthStatus.SERVICE_UNKNOWN,
                HealthStatus.UNREACHABLE
        );
    }
}
