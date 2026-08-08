package ssg.legoflow.messaging.kafka.protocol;

/**
 * Kafka response header sent by brokers.
 *
 * @param correlationId the correlation ID matching the request
 * @since 0.1.0
 */
public record ResponseHeader(int correlationId) {
}
