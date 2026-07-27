package ssg.legoflow.messaging.kafka.protocol;

/**
 * Heartbeat request (API key 12).
 *
 * @param groupId      the consumer group ID
 * @param generationId the group generation ID
 * @param memberId     the member ID
 * @since 1.0.0
 */
public record HeartbeatRequest(String groupId, int generationId, String memberId) {
}
