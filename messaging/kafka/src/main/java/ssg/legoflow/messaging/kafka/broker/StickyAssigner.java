package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.TopicPartition;
import java.util.*;
/**
 * Sticky partition assigner (KIP-429).
 *
 * <p>Minimizes partition movement during rebalance by retaining existing valid
 * assignments and distributing unassigned partitions to the least-loaded members.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Keep existing assignments where both the member and partition are still valid</li>
 *   <li>Collect unassigned partitions</li>
 *   <li>Sort unassigned partitions deterministically</li>
 *   <li>Assign each unassigned partition to the member with fewest current partitions</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class StickyAssigner implements PartitionAssigner {

    @Override
    public String name() {
        return "sticky";
    }

    @Override
    public Map<String, List<TopicPartition>> assign(List<String> members, List<TopicPartition> partitions,
                                                     Map<String, List<TopicPartition>> currentAssignment) {
        Set<String> memberSet = new HashSet<>(members);
        Set<TopicPartition> allPartitions = new HashSet<>(partitions);

        // Initialize result with empty lists for all members
        Map<String, List<TopicPartition>> result = new LinkedHashMap<>();
        for (String member : members.stream().sorted().toList()) {
            result.put(member, new ArrayList<>());
        }

        // Step 1: Retain valid existing assignments
        Set<TopicPartition> assigned = new HashSet<>();
        if (currentAssignment != null) {
            for (var entry : currentAssignment.entrySet()) {
                String member = entry.getKey();
                if (!memberSet.contains(member)) continue;
                for (TopicPartition tp : entry.getValue()) {
                    if (allPartitions.contains(tp)) {
                        result.get(member).add(tp);
                        assigned.add(tp);
                    }
                }
            }
        }

        // Step 2: Collect unassigned partitions
        List<TopicPartition> unassigned = partitions.stream()
                .filter(tp -> !assigned.contains(tp))
                .sorted(Comparator.comparing(TopicPartition::topic)
                        .thenComparingInt(TopicPartition::partition))
                .toList();

        // Step 3: Assign to least-loaded members
        for (TopicPartition tp : unassigned) {
            String leastLoaded = result.entrySet().stream()
                    .min(Comparator.comparingInt(e -> e.getValue().size()))
                    .map(Map.Entry::getKey)
                    .orElseThrow();
            result.get(leastLoaded).add(tp);
        }

        return result;
    }
}
