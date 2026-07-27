package ssg.legoflow.messaging.kafka.protocol;

/**
 * EndTxn response (API key 26).
 *
 * @param errorCode the error code
 * @since 1.0.0
 */
public record EndTxnResponse(short errorCode) {
}
