package ssg.legoflow.messaging.kafka.protocol;

/**
 * SyncGroup response (API key 14).
 *
 * @param errorCode  the error code
 * @param assignment the serialized partition assignment
 * @since 1.0.0
 */
public record SyncGroupResponse(short errorCode, byte[] assignment) {
}
