package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link BrokerCluster}.
 */
class BrokerClusterTest {

    @Test
    void testClusterCreation() throws IOException {
        try (var cluster = new BrokerCluster(3, "localhost")) {
            assertThat(cluster.size()).isEqualTo(3);
            for (int i = 0; i < 3; i++) {
                assertThat(cluster.getBroker(i)).isNotNull();
                assertThat(cluster.getBroker(i).port()).isGreaterThan(0);
            }
        }
    }

    @Test
    void testInvalidBrokerCount() {
        assertThatThrownBy(() -> new BrokerCluster(0, "localhost"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testLeaderElection() throws IOException {
        try (var cluster = new BrokerCluster(3, "localhost")) {
            var tp = new TopicPartition("test", 0);
            cluster.electLeader(tp, 1);

            assertThat(cluster.leaderFor(tp)).isEqualTo(1);
            assertThat(cluster.isrFor(tp)).contains(1);

            // All brokers should know about the leader
            for (int i = 0; i < 3; i++) {
                var state = cluster.getBroker(i).replicaManager().getReplicaState(tp);
                assertThat(state).isNotNull();
                assertThat(state.leaderBrokerId()).isEqualTo(1);
            }
        }
    }

    @Test
    void testReassignment() throws IOException {
        try (var cluster = new BrokerCluster(3, "localhost")) {
            var tp = new TopicPartition("test", 0);
            cluster.reassignPartition(tp, List.of(0, 2));

            var reassignments = cluster.listReassignments();
            assertThat(reassignments).containsKey(tp);
            assertThat(reassignments.get(tp)).containsExactly(0, 2);
        }
    }

    @Test
    void testControlledShutdown() throws IOException {
        try (var cluster = new BrokerCluster(3, "localhost")) {
            var tp0 = new TopicPartition("test", 0);
            var tp1 = new TopicPartition("test", 1);

            // Elect broker 1 as leader for both partitions
            cluster.electLeader(tp0, 1);
            cluster.electLeader(tp1, 1);

            assertThat(cluster.leaderFor(tp0)).isEqualTo(1);
            assertThat(cluster.leaderFor(tp1)).isEqualTo(1);

            // Controlled shutdown of broker 1 should migrate leadership
            cluster.controlledShutdown(1);

            assertThat(cluster.leaderFor(tp0)).isNotEqualTo(1);
            assertThat(cluster.leaderFor(tp1)).isNotEqualTo(1);
        }
    }

    @Test
    void testGetInvalidBroker() throws IOException {
        try (var cluster = new BrokerCluster(2, "localhost")) {
            assertThatThrownBy(() -> cluster.getBroker(-1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> cluster.getBroker(5))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void testLeaderElectionIncrementsEpoch() throws IOException {
        try (var cluster = new BrokerCluster(3, "localhost")) {
            var tp = new TopicPartition("test", 0);
            cluster.electLeader(tp, 0);
            int epoch1 = cluster.getBroker(0).replicaManager().leaderEpoch(tp);

            cluster.electLeader(tp, 1);
            int epoch2 = cluster.getBroker(0).replicaManager().leaderEpoch(tp);

            assertThat(epoch2).isGreaterThan(epoch1);
        }
    }
}
