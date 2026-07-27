package ssg.legoflow.messaging.kafka.protocol;

/**
 * LeaveGroup request (API key 13).
 *
 * @param groupId  the consumer group ID
 * @param memberId the member ID
 * @since 1.0.0
 */
public record LeaveGroupRequest(String groupId, String memberId) {
}
