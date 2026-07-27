package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.TopicPartition;

import java.util.*;

/**
 * Range partition assigner.
 *
 * <p>Distributes partitions by sorting them by topic and partition index, then
 * assigning them round-robin across sorted members. This is the default Kafka
 * assignment strategy.
 *
 * @since 1.0.0
 */
public final class RangeAssigner implements PartitionAssigner {

    @Override
    public String name() {
        return "range";
    }

    @Override
    public Map<String, List<TopicPartition>> assign(List<String> members, List<TopicPartition> partitions,
                                                     Map<String, List<TopicPartition>> currentAssignment) {
        Map<String, List<TopicPartition>> result = new LinkedHashMap<>();
        List<String> sortedMembers = members.stream().sorted().toList();
        for (String member : sortedMembers) {
            result.put(member, new ArrayList<>());
        }

        // Sort partitions deterministically
        List<TopicPartition> sorted = partitions.stream()
                .sorted(Comparator.comparing(TopicPartition::topic)
                        .thenComparingInt(TopicPartition::partition))
                .toList();

        int memberCount = sortedMembers.size();
        for (int i = 0; i < sorted.size(); i++) {
            String targetMember = sortedMembers.get(i % memberCount);
            result.get(targetMember).add(sorted.get(i));
        }

        return result;
    }
}
