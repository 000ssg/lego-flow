package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ReplicaManager}.
 */
class ReplicaManagerTest {

    private final TopicPartition tp0 = new TopicPartition("test", 0);
    private final TopicPartition tp1 = new TopicPartition("test", 1);

    @Test
    void testUpdateLeaderAndIsr() {
        var rm = new ReplicaManager(0);
        rm.updateLeaderAndIsr(tp0, 0, 1, List.of(0, 1, 2));

        var state = rm.getReplicaState(tp0);
        assertThat(state).isNotNull();
        assertThat(state.leaderBrokerId()).isZero();
        assertThat(state.leaderEpoch()).isEqualTo(1);
        assertThat(state.isr()).containsExactly(0, 1, 2);
    }

    @Test
    void testIsLeader() {
        var rm = new ReplicaManager(0);
        rm.updateLeaderAndIsr(tp0, 0, 1, List.of(0, 1));
        rm.updateLeaderAndIsr(tp1, 1, 1, List.of(0, 1));

        assertThat(rm.isLeader(tp0)).isTrue();
        assertThat(rm.isLeader(tp1)).isFalse();
    }

    @Test
    void testLeaderEpoch() {
        var rm = new ReplicaManager(0);
        rm.updateLeaderAndIsr(tp0, 0, 5, List.of(0));

        assertThat(rm.leaderEpoch(tp0)).isEqualTo(5);
        assertThat(rm.leaderEpoch(tp1)).isEqualTo(-1);
    }

    @Test
    void testStopReplicaWithDelete() {
        var rm = new ReplicaManager(0);
        rm.updateLeaderAndIsr(tp0, 0, 1, List.of(0));

        rm.stopReplica(tp0, true);
        assertThat(rm.getReplicaState(tp0)).isNull();
    }

    @Test
    void testStopReplicaWithoutDelete() {
        var rm = new ReplicaManager(0);
        rm.updateLeaderAndIsr(tp0, 0, 1, List.of(0));

        rm.stopReplica(tp0, false);
        // State is still there when not deleting
        assertThat(rm.getReplicaState(tp0)).isNotNull();
    }

    @Test
    void testAllReplicas() {
        var rm = new ReplicaManager(0);
        rm.updateLeaderAndIsr(tp0, 0, 1, List.of(0));
        rm.updateLeaderAndIsr(tp1, 1, 1, List.of(0, 1));

        assertThat(rm.allReplicas()).hasSize(2);
        assertThat(rm.allReplicas()).containsKey(tp0);
        assertThat(rm.allReplicas()).containsKey(tp1);
    }

    @Test
    void testOffsetForLeaderEpoch() {
        var rm = new ReplicaManager(0);
        rm.updateLeaderAndIsr(tp0, 0, 3, List.of(0));
        rm.updateOffsets(tp0, 100, 90);

        // Requesting epoch <= current epoch returns the log end offset
        assertThat(rm.offsetForLeaderEpoch(tp0, 3)).isEqualTo(100);
        assertThat(rm.offsetForLeaderEpoch(tp0, 1)).isEqualTo(100);
        // Requesting epoch > current returns -1
        assertThat(rm.offsetForLeaderEpoch(tp0, 10)).isEqualTo(-1);
        // Unknown partition returns -1
        assertThat(rm.offsetForLeaderEpoch(tp1, 1)).isEqualTo(-1);
    }

    @Test
    void testUpdateOffsets() {
        var rm = new ReplicaManager(0);
        rm.updateLeaderAndIsr(tp0, 0, 1, List.of(0));
        rm.updateOffsets(tp0, 50, 40);

        var state = rm.getReplicaState(tp0);
        assertThat(state.logEndOffset()).isEqualTo(50);
        assertThat(state.highWatermark()).isEqualTo(40);
        // Leader and ISR are preserved
        assertThat(state.leaderBrokerId()).isZero();
        assertThat(state.leaderEpoch()).isEqualTo(1);
    }
}
