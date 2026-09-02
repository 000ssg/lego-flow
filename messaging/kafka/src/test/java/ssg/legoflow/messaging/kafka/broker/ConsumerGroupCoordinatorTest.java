package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.KafkaErrors;
import ssg.legoflow.messaging.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class ConsumerGroupCoordinatorTest {

    private ConsumerGroupCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new ConsumerGroupCoordinator();
    }

    @Test
    void testJoinGroupNewMember() {
        var result = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");

        assertThat(result.errorCode()).isEqualTo(KafkaErrors.NONE.code());
        assertThat(result.memberId()).isNotEmpty();
        assertThat(result.generationId()).isGreaterThan(0);
        assertThat(result.leader()).isEqualTo(result.memberId()); // first member is leader
    }

    @Test
    void testJoinGroupMultipleMembers() {
        var r1 = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");
        var r2 = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-2");

        assertThat(r1.errorCode()).isEqualTo(KafkaErrors.NONE.code());
        assertThat(r2.errorCode()).isEqualTo(KafkaErrors.NONE.code());
        assertThat(r1.memberId()).isNotEqualTo(r2.memberId());
    }

    @Test
    void testJoinGroupUnknownMember() {
        var result = coordinator.joinGroup("group1", "unknown-member-id", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");

        assertThat(result.errorCode()).isEqualTo(KafkaErrors.UNKNOWN_MEMBER_ID.code());
    }

    @Test
    void testSyncGroupLeader() {
        var join = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");

        var assignment = new byte[]{1, 2, 3};
        var result = coordinator.syncGroup("group1", join.generationId(), join.memberId(),
                Map.of(join.memberId(), assignment));

        assertThat(result.getKey()).isEqualTo(KafkaErrors.NONE.code());
        assertThat(result.getValue()).isEqualTo(assignment);
    }

    @Test
    void testSyncGroupUnknownMember() {
        var result = coordinator.syncGroup("group1", 1, "unknown", Map.of());
        assertThat(result.getKey()).isEqualTo(KafkaErrors.UNKNOWN_MEMBER_ID.code());
    }

    @Test
    void testSyncGroupWrongGeneration() {
        var join = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");

        var result = coordinator.syncGroup("group1", join.generationId() + 1, join.memberId(), Map.of());
        assertThat(result.getKey()).isEqualTo(KafkaErrors.ILLEGAL_GENERATION.code());
    }

    @Test
    void testHeartbeat() {
        var join = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");
        coordinator.syncGroup("group1", join.generationId(), join.memberId(), Map.of());

        short error = coordinator.heartbeat("group1", join.generationId(), join.memberId());
        assertThat(error).isEqualTo(KafkaErrors.NONE.code());
    }

    @Test
    void testHeartbeatUnknownMember() {
        short error = coordinator.heartbeat("group1", 1, "unknown");
        assertThat(error).isEqualTo(KafkaErrors.UNKNOWN_MEMBER_ID.code());
    }

    @Test
    void testHeartbeatWrongGeneration() {
        var join = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");
        coordinator.syncGroup("group1", join.generationId(), join.memberId(), Map.of());

        short error = coordinator.heartbeat("group1", join.generationId() + 1, join.memberId());
        assertThat(error).isEqualTo(KafkaErrors.ILLEGAL_GENERATION.code());
    }

    @Test
    void testLeaveGroup() {
        var join = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");

        short error = coordinator.leaveGroup("group1", join.memberId());
        assertThat(error).isEqualTo(KafkaErrors.NONE.code());
    }

    @Test
    void testLeaveGroupUnknownMember() {
        short error = coordinator.leaveGroup("group1", "unknown");
        assertThat(error).isEqualTo(KafkaErrors.UNKNOWN_MEMBER_ID.code());
    }

    @Test
    void testLeaveGroupLastMember() {
        var join = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");

        coordinator.leaveGroup("group1", join.memberId());
        var group = coordinator.describeGroup("group1");
        assertThat(group.state).isEqualTo(ConsumerGroupCoordinator.GroupState.EMPTY);
    }

    @Test
    void testLeaveGroupLeaderTriggersRebalance() {
        var j1 = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");
        coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-2");

        // Leave the leader
        coordinator.leaveGroup("group1", j1.memberId());
        var group = coordinator.describeGroup("group1");
        assertThat(group.state).isEqualTo(ConsumerGroupCoordinator.GroupState.PREPARING_REBALANCE);
    }

    @Test
    void testCommitAndFetchOffsets() {
        var tp = new TopicPartition("test", 0);
        coordinator.commitOffsets("group1", Map.of(tp, 42L));

        var fetched = coordinator.fetchOffsets("group1", List.of(tp));
        assertThat(fetched.get(tp)).isEqualTo(42L);
    }

    @Test
    void testFetchOffsetsNotCommitted() {
        var tp = new TopicPartition("test", 0);
        var fetched = coordinator.fetchOffsets("group1", List.of(tp));
        assertThat(fetched.get(tp)).isEqualTo(-1L);
    }

    @Test
    void testCommitOffsetsMultiplePartitions() {
        var tp0 = new TopicPartition("test", 0);
        var tp1 = new TopicPartition("test", 1);
        coordinator.commitOffsets("group1", Map.of(tp0, 10L, tp1, 20L));

        var fetched = coordinator.fetchOffsets("group1", List.of(tp0, tp1));
        assertThat(fetched.get(tp0)).isEqualTo(10L);
        assertThat(fetched.get(tp1)).isEqualTo(20L);
    }

    @Test
    void testDescribeGroupNotFound() {
        assertThat(coordinator.describeGroup("nonexistent")).isNull();
    }

    @Test
    void testDescribeGroupExists() {
        coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");
        var group = coordinator.describeGroup("group1");
        assertThat(group).isNotNull();
        assertThat(group.groupId).isEqualTo("group1");
        assertThat(group.members).hasSize(1);
    }

    @Test
    void testCheckExpiredMembers() {
        var join = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");
        coordinator.syncGroup("group1", join.generationId(), join.memberId(), Map.of());

        // Simulate expired heartbeat — wait 2ms then check with 1ms timeout
        try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        coordinator.checkExpiredMembers(1);
        var group = coordinator.describeGroup("group1");
        assertThat(group.state).isEqualTo(ConsumerGroupCoordinator.GroupState.EMPTY);
    }

    @Test
    void testEncodeDecodeAssignment() {
        var partitions = List.of(
                new TopicPartition("topic1", 0),
                new TopicPartition("topic1", 1),
                new TopicPartition("topic2", 0));

        byte[] encoded = ConsumerGroupCoordinator.encodeAssignment(partitions);
        var decoded = ConsumerGroupCoordinator.decodeAssignment(encoded);

        assertThat(decoded).hasSize(3);
        assertThat(decoded).containsExactlyInAnyOrderElementsOf(partitions);
    }

    @Test
    void testDecodeAssignmentEmpty() {
        assertThat(ConsumerGroupCoordinator.decodeAssignment(null)).isEmpty();
        assertThat(ConsumerGroupCoordinator.decodeAssignment(new byte[0])).isEmpty();
    }

    @Test
    void testEncodeDecodeSubscription() {
        var topics = List.of("topic1", "topic2", "topic3");
        byte[] encoded = ConsumerGroupCoordinator.encodeSubscription(topics);
        var decoded = ConsumerGroupCoordinator.decodeSubscription(encoded);
        assertThat(decoded).containsExactlyElementsOf(topics);
    }

    @Test
    void testDecodeSubscriptionEmpty() {
        assertThat(ConsumerGroupCoordinator.decodeSubscription(null)).isEmpty();
        assertThat(ConsumerGroupCoordinator.decodeSubscription(new byte[0])).isEmpty();
    }

    @Test
    void testListGroupsEmpty() {
        assertThat(coordinator.listGroups()).isEmpty();
    }

    @Test
    void testListGroupsWithGroups() {
        coordinator.joinGroup("group-a", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");
        coordinator.joinGroup("group-b", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-2");

        var groups = coordinator.listGroups();
        assertThat(groups).hasSize(2);
        assertThat(groups.stream().map(ConsumerGroupCoordinator.GroupInfo::groupId).toList())
                .containsExactlyInAnyOrder("group-a", "group-b");
    }

    @Test
    void testDeleteGroupEmpty() {
        var join = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");
        // Leave so group becomes EMPTY
        coordinator.leaveGroup("group1", join.memberId());

        short error = coordinator.deleteGroup("group1");
        assertThat(error).isEqualTo(KafkaErrors.NONE.code());
        assertThat(coordinator.describeGroup("group1")).isNull();
    }

    @Test
    void testDeleteGroupNonEmpty() {
        coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");

        short error = coordinator.deleteGroup("group1");
        assertThat(error).isEqualTo(KafkaErrors.NON_EMPTY_GROUP.code());
    }

    @Test
    void testDeleteGroupNotFound() {
        short error = coordinator.deleteGroup("nonexistent");
        assertThat(error).isEqualTo(KafkaErrors.GROUP_ID_NOT_FOUND.code());
    }

    @Test
    void testDeleteOffsets() {
        var tp0 = new TopicPartition("test", 0);
        var tp1 = new TopicPartition("test", 1);
        coordinator.commitOffsets("group1", Map.of(tp0, 10L, tp1, 20L));

        var results = coordinator.deleteOffsets("group1", List.of(tp0));
        assertThat(results.get(tp0)).isEqualTo(KafkaErrors.NONE.code());

        // Verify tp0 offset is gone but tp1 remains
        var fetched = coordinator.fetchOffsets("group1", List.of(tp0, tp1));
        assertThat(fetched.get(tp0)).isEqualTo(-1L);
        assertThat(fetched.get(tp1)).isEqualTo(20L);
    }

    @Test
    void testDeleteOffsetsGroupNotFound() {
        var tp = new TopicPartition("test", 0);
        var results = coordinator.deleteOffsets("nonexistent", List.of(tp));
        assertThat(results.get(tp)).isEqualTo(KafkaErrors.GROUP_ID_NOT_FOUND.code());
    }

    @Test
    void testJoinGroupWithStickyProtocol() {
        var result = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("sticky", new byte[0])), "client-1");

        assertThat(result.errorCode()).isEqualTo(KafkaErrors.NONE.code());
        assertThat(result.protocolName()).isEqualTo("sticky");

        // Verify assigner is StickyAssigner
        var assigner = coordinator.getGroupAssigner("group1");
        assertThat(assigner).isInstanceOf(StickyAssigner.class);
        assertThat(assigner.name()).isEqualTo("sticky");
    }

    @Test
    void testJoinGroupWithCooperativeStickyProtocol() {
        var result = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("cooperative-sticky", new byte[0])), "client-1");

        assertThat(result.errorCode()).isEqualTo(KafkaErrors.NONE.code());
        assertThat(result.protocolName()).isEqualTo("cooperative-sticky");
        assertThat(coordinator.getGroupProtocol("group1")).isEqualTo("cooperative-sticky");

        var assigner = coordinator.getGroupAssigner("group1");
        assertThat(assigner).isInstanceOf(StickyAssigner.class);
    }

    @Test
    void testJoinGroupDefaultRangeAssigner() {
        var result = coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");

        assertThat(result.protocolName()).isEqualTo("range");
        var assigner = coordinator.getGroupAssigner("group1");
        assertThat(assigner).isInstanceOf(RangeAssigner.class);
        assertThat(assigner.name()).isEqualTo("range");
    }

    @Test
    void testGetGroupProtocolNotFound() {
        assertThat(coordinator.getGroupProtocol("nonexistent")).isEmpty();
    }

    @Test
    void testStorePreviousAssignment() {
        coordinator.joinGroup("group1", "", "consumer", 10000,
                List.of(Map.entry("cooperative-sticky", new byte[0])), "client-1");

        var tp0 = new TopicPartition("test", 0);
        var tp1 = new TopicPartition("test", 1);
        var assignment = Map.of("member-1", List.of(tp0), "member-2", List.of(tp1));
        coordinator.storePreviousAssignment("group1", assignment);

        var previous = coordinator.getPreviousAssignment("group1");
        assertThat(previous).hasSize(2);
        assertThat(previous.get("member-1")).containsExactly(tp0);
        assertThat(previous.get("member-2")).containsExactly(tp1);
    }

    @Test
    void testMultipleGroupsIndependent() {
        var j1 = coordinator.joinGroup("group-a", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");
        var j2 = coordinator.joinGroup("group-b", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-2");

        assertThat(coordinator.describeGroup("group-a").members).hasSize(1);
        assertThat(coordinator.describeGroup("group-b").members).hasSize(1);

        coordinator.leaveGroup("group-a", j1.memberId());
        assertThat(coordinator.describeGroup("group-a").state)
                .isEqualTo(ConsumerGroupCoordinator.GroupState.EMPTY);
        assertThat(coordinator.describeGroup("group-b").members).hasSize(1);
    }
}
