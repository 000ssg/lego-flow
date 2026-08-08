package ssg.legoflow.messaging.kafka.protocol;

/**
 * LeaveGroup response (API key 13).
 *
 * @param errorCode the error code
 * @since 0.1.0
 */
public record LeaveGroupResponse(short errorCode) {
}
