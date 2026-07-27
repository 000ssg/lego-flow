package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RebalanceListenerTest {

    @Test
    void testDefaultOnPartitionsLostDelegatesToRevoked() {
        List<TopicPartition> revokedPartitions = new ArrayList<>();

        RebalanceListener listener = new RebalanceListener() {
            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                // no-op
            }

            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                revokedPartitions.addAll(partitions);
            }
        };

        var partitions = List.of(
                new TopicPartition("topic1", 0),
                new TopicPartition("topic1", 1));

        // Default onPartitionsLost should delegate to onPartitionsRevoked
        listener.onPartitionsLost(partitions);

        assertThat(revokedPartitions).containsExactlyInAnyOrderElementsOf(partitions);
    }

    @Test
    void testCustomOnPartitionsLostOverride() {
        List<TopicPartition> lostPartitions = new ArrayList<>();
        List<TopicPartition> revokedPartitions = new ArrayList<>();

        RebalanceListener listener = new RebalanceListener() {
            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                // no-op
            }

            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                revokedPartitions.addAll(partitions);
            }

            @Override
            public void onPartitionsLost(Collection<TopicPartition> partitions) {
                // Custom behavior — does NOT delegate to onPartitionsRevoked
                lostPartitions.addAll(partitions);
            }
        };

        var partitions = List.of(
                new TopicPartition("topic1", 0),
                new TopicPartition("topic1", 1));

        listener.onPartitionsLost(partitions);

        assertThat(lostPartitions).containsExactlyInAnyOrderElementsOf(partitions);
        assertThat(revokedPartitions).isEmpty(); // should NOT have been called
    }
}
