package ssg.legoflow.messaging.kafka.protocol;

/**
 * EndTxn response (API key 26).
 *
 * @param errorCode the error code
 * @since 0.1.0
 */
public record EndTxnResponse(short errorCode) {
}
