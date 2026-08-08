package ssg.legoflow.messaging.kafka.protocol;

/**
 * EndTxn request (API key 26).
 *
 * @param transactionalId the transactional ID
 * @param producerId      the producer ID
 * @param producerEpoch   the producer epoch
 * @param committed       true to commit, false to abort
 * @since 0.1.0
 */
public record EndTxnRequest(String transactionalId, long producerId, short producerEpoch,
                            boolean committed) {
}
