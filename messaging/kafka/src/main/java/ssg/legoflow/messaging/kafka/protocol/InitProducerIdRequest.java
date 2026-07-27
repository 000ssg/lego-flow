package ssg.legoflow.messaging.kafka.protocol;

/**
 * InitProducerId request (API key 22).
 *
 * @param transactionalId    the transactional ID (nullable for idempotent-only)
 * @param transactionTimeoutMs the transaction timeout
 * @since 1.0.0
 */
public record InitProducerIdRequest(String transactionalId, int transactionTimeoutMs) {
}
