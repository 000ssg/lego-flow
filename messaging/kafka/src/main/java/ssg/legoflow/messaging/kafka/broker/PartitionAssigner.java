package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.TopicPartition;

import java.util.List;
import java.util.Map;

/**
 * Strategy for assigning partitions to consumer group members.
 *
 * <p>Implementations define how topic-partitions are distributed across the members
 * of a consumer group during a rebalance. The coordinator selects an assigner based
 * on the protocol name agreed upon during the JoinGroup phase.
 *
 * @since 0.1.0
 */
public interface PartitionAssigner {

    /**
     * Returns the name of this assignment strategy.
     *
     * @return the strategy name (e.g., "range", "sticky")
     */
    String name();

    /**
     * Assigns partitions to consumer group members.
     *
     * @param members           the sorted list of member IDs
     * @param partitions        the partitions to assign
     * @param currentAssignment the current assignment (may be empty for first rebalance)
     * @return the new assignment: member ID to list of assigned partitions
     */
    Map<String, List<TopicPartition>> assign(List<String> members, List<TopicPartition> partitions,
                                              Map<String, List<TopicPartition>> currentAssignment);
}
