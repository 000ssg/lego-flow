package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * SyncGroup request (API key 14).
 *
 * @param groupId      the consumer group ID
 * @param generationId the group generation ID
 * @param memberId     the member ID
 * @param assignments  the partition assignments (only from leader)
 * @since 1.0.0
 */
public record SyncGroupRequest(String groupId, int generationId, String memberId,
                               List<Assignment> assignments) {

    /**
     * A partition assignment for a member.
     *
     * @param memberId   the member ID
     * @param assignment the serialized assignment bytes
     */
    public record Assignment(String memberId, byte[] assignment) {
    }
}
