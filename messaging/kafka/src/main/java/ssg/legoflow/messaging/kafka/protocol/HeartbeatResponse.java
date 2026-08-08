package ssg.legoflow.messaging.kafka.protocol;

/**
 * Heartbeat response (API key 12).
 *
 * @param errorCode the error code
 * @since 0.1.0
 */
public record HeartbeatResponse(short errorCode) {
}
