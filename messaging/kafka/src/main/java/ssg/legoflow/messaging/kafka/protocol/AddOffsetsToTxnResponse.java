package ssg.legoflow.messaging.kafka.protocol;

/**
 * AddOffsetsToTxn response (API key 25).
 *
 * @param errorCode the error code
 * @since 1.0.0
 */
public record AddOffsetsToTxnResponse(short errorCode) {
}
