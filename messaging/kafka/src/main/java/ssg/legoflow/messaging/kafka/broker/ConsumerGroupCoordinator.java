package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.KafkaErrors;
import ssg.legoflow.messaging.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages consumer group membership, rebalancing, and offset storage.
 *
 * <p>Supports the full consumer group protocol: join, sync, heartbeat, leave.
 * Implements range and round-robin partition assignment strategies.
 *
 * @since 1.0.0
 */
public final class ConsumerGroupCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumerGroupCoordinator.class);

    private final Map<String, ConsumerGroup> groups = new ConcurrentHashMap<>();
    private final Map<String, Map<TopicPartition, Long>> committedOffsets = new ConcurrentHashMap<>();

    /**
     * Consumer group state.
     */
    public enum GroupState {
        EMPTY, PREPARING_REBALANCE, COMPLETING_REBALANCE, STABLE, DEAD
    }

    /**
     * Internal representation of a consumer group.
     */
    static final class ConsumerGroup {
        final String groupId;
        volatile GroupState state = GroupState.EMPTY;
        volatile int generationId = 0;
        volatile String protocolType = "";
        volatile String protocol = "";
        volatile String leaderId = null;
        volatile PartitionAssigner assigner = new RangeAssigner();
        final Map<String, MemberMetadata> members = new ConcurrentHashMap<>();
        final Map<String, byte[]> assignments = new ConcurrentHashMap<>();
        final Map<String, Long> lastHeartbeat = new ConcurrentHashMap<>();
        final Map<String, List<TopicPartition>> previousAssignment = new ConcurrentHashMap<>();
        final AtomicInteger memberIdCounter = new AtomicInteger(0);

        ConsumerGroup(String groupId) {
            this.groupId = groupId;
        }
    }

    /**
     * Metadata about a consumer group member.
     */
    record MemberMetadata(String memberId, String clientId, List<String> protocols, byte[] metadata) {
    }

    /**
     * Result of a JoinGroup operation.
     */
    public record JoinResult(short errorCode, int generationId, String protocolName,
                             String leader, String memberId,
                             Map<String, byte[]> memberMetadata) {
    }

    /**
     * Handles a JoinGroup request.
     *
     * @param groupId          the group ID
     * @param memberId         the member ID (empty for first join)
     * @param protocolType     the protocol type
     * @param sessionTimeoutMs the session timeout
     * @param protocols        the supported protocols
     * @param clientId         the client ID
     * @return the join result
     */
    public JoinResult joinGroup(String groupId, String memberId, String protocolType,
                                int sessionTimeoutMs,
                                List<Map.Entry<String, byte[]>> protocols, String clientId) {
        ConsumerGroup group = groups.computeIfAbsent(groupId, ConsumerGroup::new);

        synchronized (group) {
            // Assign member ID if new
            String assignedMemberId = memberId;
            if (memberId == null || memberId.isEmpty()) {
                assignedMemberId = clientId + "-" + UUID.randomUUID();
            }

            // Check if member already exists
            if (!memberId.isEmpty() && !group.members.containsKey(memberId)) {
                // Unknown member
                return new JoinResult(KafkaErrors.UNKNOWN_MEMBER_ID.code(), -1, "", "", memberId, Map.of());
            }

            // Store member
            List<String> protocolNames = protocols.stream().map(Map.Entry::getKey).toList();
            byte[] metadata = protocols.isEmpty() ? new byte[0] : protocols.getFirst().getValue();
            group.members.put(assignedMemberId,
                    new MemberMetadata(assignedMemberId, clientId, protocolNames, metadata));
            group.lastHeartbeat.put(assignedMemberId, System.currentTimeMillis());

            // Initiate rebalance
            group.generationId++;
            group.state = GroupState.COMPLETING_REBALANCE;
            group.protocolType = protocolType;

            // Select protocol (first common protocol)
            group.protocol = protocolNames.isEmpty() ? "" : protocolNames.getFirst();

            // Select assigner based on protocol name
            group.assigner = switch (group.protocol) {
                case "sticky", "cooperative-sticky" -> new StickyAssigner();
                default -> new RangeAssigner();
            };

            // First member becomes leader
            if (group.leaderId == null || !group.members.containsKey(group.leaderId)) {
                group.leaderId = assignedMemberId;
            }

            // Build member metadata map (only sent to leader)
            Map<String, byte[]> memberMetadata = new LinkedHashMap<>();
            if (assignedMemberId.equals(group.leaderId)) {
                for (var entry : group.members.entrySet()) {
                    memberMetadata.put(entry.getKey(), entry.getValue().metadata);
                }
            }

            return new JoinResult(KafkaErrors.NONE.code(), group.generationId,
                    group.protocol, group.leaderId, assignedMemberId, memberMetadata);
        }
    }

    /**
     * Handles a SyncGroup request.
     *
     * @param groupId      the group ID
     * @param generationId the generation ID
     * @param memberId     the member ID
     * @param assignments  the assignments (from leader)
     * @return the assignment for this member, or error
     */
    public Map.Entry<Short, byte[]> syncGroup(String groupId, int generationId, String memberId,
                                               Map<String, byte[]> assignments) {
        ConsumerGroup group = groups.get(groupId);
        if (group == null) {
            return Map.entry(KafkaErrors.UNKNOWN_MEMBER_ID.code(), new byte[0]);
        }

        synchronized (group) {
            if (!group.members.containsKey(memberId)) {
                return Map.entry(KafkaErrors.UNKNOWN_MEMBER_ID.code(), new byte[0]);
            }
            if (group.generationId != generationId) {
                return Map.entry(KafkaErrors.ILLEGAL_GENERATION.code(), new byte[0]);
            }

            // Leader provides assignments
            if (assignments != null && !assignments.isEmpty()) {
                group.assignments.clear();
                group.assignments.putAll(assignments);
            }

            group.state = GroupState.STABLE;

            byte[] memberAssignment = group.assignments.getOrDefault(memberId, new byte[0]);
            return Map.entry(KafkaErrors.NONE.code(), memberAssignment);
        }
    }

    /**
     * Handles a Heartbeat request.
     *
     * @param groupId      the group ID
     * @param generationId the generation ID
     * @param memberId     the member ID
     * @return the error code
     */
    public short heartbeat(String groupId, int generationId, String memberId) {
        ConsumerGroup group = groups.get(groupId);
        if (group == null) return KafkaErrors.UNKNOWN_MEMBER_ID.code();

        synchronized (group) {
            if (!group.members.containsKey(memberId)) {
                return KafkaErrors.UNKNOWN_MEMBER_ID.code();
            }
            if (group.generationId != generationId) {
                return KafkaErrors.ILLEGAL_GENERATION.code();
            }
            if (group.state == GroupState.PREPARING_REBALANCE ||
                    group.state == GroupState.COMPLETING_REBALANCE) {
                return KafkaErrors.REBALANCE_IN_PROGRESS.code();
            }
            group.lastHeartbeat.put(memberId, System.currentTimeMillis());
            return KafkaErrors.NONE.code();
        }
    }

    /**
     * Handles a LeaveGroup request.
     *
     * @param groupId  the group ID
     * @param memberId the member ID
     * @return the error code
     */
    public short leaveGroup(String groupId, String memberId) {
        ConsumerGroup group = groups.get(groupId);
        if (group == null) return KafkaErrors.UNKNOWN_MEMBER_ID.code();

        synchronized (group) {
            if (group.members.remove(memberId) == null) {
                return KafkaErrors.UNKNOWN_MEMBER_ID.code();
            }
            group.lastHeartbeat.remove(memberId);
            group.assignments.remove(memberId);

            if (group.members.isEmpty()) {
                group.state = GroupState.EMPTY;
                group.leaderId = null;
                group.generationId = 0;
            } else if (memberId.equals(group.leaderId)) {
                // Leader left — trigger rebalance
                group.leaderId = group.members.keySet().iterator().next();
                group.state = GroupState.PREPARING_REBALANCE;
            }

            LOG.debug("Member {} left group {}, remaining members: {}",
                    memberId, groupId, group.members.size());
            return KafkaErrors.NONE.code();
        }
    }

    /**
     * Commits offsets for a consumer group.
     *
     * @param groupId    the group ID
     * @param offsets    the offsets to commit
     */
    public void commitOffsets(String groupId, Map<TopicPartition, Long> offsets) {
        committedOffsets.computeIfAbsent(groupId, k -> new ConcurrentHashMap<>()).putAll(offsets);
    }

    /**
     * Fetches committed offsets for a consumer group.
     *
     * @param groupId    the group ID
     * @param partitions the partitions to query
     * @return the committed offsets (-1 if not committed)
     */
    public Map<TopicPartition, Long> fetchOffsets(String groupId, Collection<TopicPartition> partitions) {
        Map<TopicPartition, Long> stored = committedOffsets.getOrDefault(groupId, Map.of());
        Map<TopicPartition, Long> result = new LinkedHashMap<>();
        for (TopicPartition tp : partitions) {
            result.put(tp, stored.getOrDefault(tp, -1L));
        }
        return result;
    }

    /**
     * Describes a consumer group.
     *
     * @param groupId the group ID
     * @return the group description, or null if not found
     */
    public ConsumerGroup describeGroup(String groupId) {
        return groups.get(groupId);
    }

    /**
     * Lists all consumer groups with their metadata.
     *
     * @return list of group info entries (groupId, protocolType, state)
     */
    public List<GroupInfo> listGroups() {
        List<GroupInfo> result = new ArrayList<>();
        for (ConsumerGroup group : groups.values()) {
            synchronized (group) {
                result.add(new GroupInfo(group.groupId, group.protocolType, group.state.name()));
            }
        }
        return result;
    }

    /**
     * Deletes a consumer group if it is EMPTY or DEAD.
     *
     * @param groupId the group ID to delete
     * @return the error code
     */
    public short deleteGroup(String groupId) {
        ConsumerGroup group = groups.get(groupId);
        if (group == null) {
            return KafkaErrors.GROUP_ID_NOT_FOUND.code();
        }
        synchronized (group) {
            if (group.state != GroupState.EMPTY && group.state != GroupState.DEAD) {
                return KafkaErrors.NON_EMPTY_GROUP.code();
            }
            groups.remove(groupId);
            committedOffsets.remove(groupId);
            LOG.info("Deleted consumer group '{}'", groupId);
            return KafkaErrors.NONE.code();
        }
    }

    /**
     * Deletes committed offsets for specific partitions in a consumer group.
     *
     * @param groupId    the group ID
     * @param partitions the partitions whose offsets to delete
     * @return per-partition error codes
     */
    public Map<TopicPartition, Short> deleteOffsets(String groupId, Collection<TopicPartition> partitions) {
        Map<TopicPartition, Short> result = new LinkedHashMap<>();
        Map<TopicPartition, Long> stored = committedOffsets.get(groupId);
        if (stored == null) {
            for (TopicPartition tp : partitions) {
                result.put(tp, KafkaErrors.GROUP_ID_NOT_FOUND.code());
            }
            return result;
        }
        for (TopicPartition tp : partitions) {
            if (stored.remove(tp) != null) {
                result.put(tp, KafkaErrors.NONE.code());
            } else {
                result.put(tp, KafkaErrors.NONE.code());
            }
        }
        return result;
    }

    /**
     * Information about a consumer group.
     *
     * @param groupId      the group ID
     * @param protocolType the protocol type
     * @param state        the group state
     */
    public record GroupInfo(String groupId, String protocolType, String state) {
    }

    /**
     * Checks for expired members and triggers rebalance if needed.
     *
     * @param sessionTimeoutMs the session timeout
     */
    public void checkExpiredMembers(long sessionTimeoutMs) {
        long now = System.currentTimeMillis();
        for (ConsumerGroup group : groups.values()) {
            synchronized (group) {
                List<String> expired = new ArrayList<>();
                for (var entry : group.lastHeartbeat.entrySet()) {
                    if (now - entry.getValue() > sessionTimeoutMs) {
                        expired.add(entry.getKey());
                    }
                }
                for (String memberId : expired) {
                    LOG.info("Member {} expired from group {}", memberId, group.groupId);
                    group.members.remove(memberId);
                    group.lastHeartbeat.remove(memberId);
                    group.assignments.remove(memberId);
                }
                if (!expired.isEmpty()) {
                    if (group.members.isEmpty()) {
                        group.state = GroupState.EMPTY;
                        group.leaderId = null;
                    } else {
                        group.state = GroupState.PREPARING_REBALANCE;
                        if (!group.members.containsKey(group.leaderId)) {
                            group.leaderId = group.members.keySet().iterator().next();
                        }
                    }
                }
            }
        }
    }

    /**
     * Encodes a partition assignment to bytes (for SyncGroup).
     *
     * @param partitions the assigned partitions
     * @return the encoded assignment
     */
    public static byte[] encodeAssignment(List<TopicPartition> partitions) {
        // Group by topic
        Map<String, List<Integer>> byTopic = new LinkedHashMap<>();
        for (TopicPartition tp : partitions) {
            byTopic.computeIfAbsent(tp.topic(), k -> new ArrayList<>()).add(tp.partition());
        }

        ByteBuffer buf = ByteBuffer.allocate(4096);
        buf.putShort((short) 0); // version
        buf.putInt(byTopic.size());
        for (var entry : byTopic.entrySet()) {
            byte[] topicBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
            buf.putShort((short) topicBytes.length);
            buf.put(topicBytes);
            buf.putInt(entry.getValue().size());
            for (int p : entry.getValue()) buf.putInt(p);
        }
        buf.putInt(0); // user data length
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Decodes a partition assignment from bytes.
     *
     * @param data the encoded assignment
     * @return the assigned partitions
     */
    public static List<TopicPartition> decodeAssignment(byte[] data) {
        if (data == null || data.length == 0) return List.of();
        ByteBuffer buf = ByteBuffer.wrap(data);
        short version = buf.getShort();
        int topicCount = buf.getInt();
        List<TopicPartition> result = new ArrayList<>();
        for (int i = 0; i < topicCount; i++) {
            short topicLen = buf.getShort();
            byte[] topicBytes = new byte[topicLen];
            buf.get(topicBytes);
            String topic = new String(topicBytes, StandardCharsets.UTF_8);
            int partCount = buf.getInt();
            for (int j = 0; j < partCount; j++) {
                result.add(new TopicPartition(topic, buf.getInt()));
            }
        }
        return result;
    }

    /**
     * Encodes consumer subscription metadata.
     *
     * @param topics the subscribed topics
     * @return the encoded metadata
     */
    public static byte[] encodeSubscription(List<String> topics) {
        ByteBuffer buf = ByteBuffer.allocate(4096);
        buf.putShort((short) 0); // version
        buf.putInt(topics.size());
        for (String topic : topics) {
            byte[] bytes = topic.getBytes(StandardCharsets.UTF_8);
            buf.putShort((short) bytes.length);
            buf.put(bytes);
        }
        buf.putInt(0); // user data length
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Returns the protocol name for a consumer group.
     *
     * @param groupId the group ID
     * @return the protocol name, or empty string if the group does not exist
     */
    public String getGroupProtocol(String groupId) {
        ConsumerGroup group = groups.get(groupId);
        if (group == null) return "";
        synchronized (group) {
            return group.protocol;
        }
    }

    /**
     * Returns the partition assigner for a consumer group.
     *
     * @param groupId the group ID
     * @return the assigner, or null if the group does not exist
     */
    public PartitionAssigner getGroupAssigner(String groupId) {
        ConsumerGroup group = groups.get(groupId);
        if (group == null) return null;
        synchronized (group) {
            return group.assigner;
        }
    }

    /**
     * Returns the previous partition assignment for a consumer group.
     *
     * @param groupId the group ID
     * @return the previous assignment map, or empty map if none
     */
    public Map<String, List<TopicPartition>> getPreviousAssignment(String groupId) {
        ConsumerGroup group = groups.get(groupId);
        if (group == null) return Map.of();
        synchronized (group) {
            return new LinkedHashMap<>(group.previousAssignment);
        }
    }

    /**
     * Stores the current assignment as the previous assignment for cooperative rebalance.
     *
     * @param groupId    the group ID
     * @param assignment the assignment to store
     */
    public void storePreviousAssignment(String groupId, Map<String, List<TopicPartition>> assignment) {
        ConsumerGroup group = groups.get(groupId);
        if (group == null) return;
        synchronized (group) {
            group.previousAssignment.clear();
            group.previousAssignment.putAll(assignment);
        }
    }

    /**
     * Decodes consumer subscription metadata.
     *
     * @param data the encoded metadata
     * @return the subscribed topics
     */
    public static List<String> decodeSubscription(byte[] data) {
        if (data == null || data.length == 0) return List.of();
        ByteBuffer buf = ByteBuffer.wrap(data);
        short version = buf.getShort();
        int topicCount = buf.getInt();
        List<String> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            short len = buf.getShort();
            byte[] bytes = new byte[len];
            buf.get(bytes);
            topics.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return topics;
    }
}
