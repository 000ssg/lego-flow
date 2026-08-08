package ssg.legoflow.messaging.kafka.protocol;

/**
 * AddOffsetsToTxn request (API key 25).
 *
 * <p>Registers a consumer group's {@code __consumer_offsets} partition with an ongoing transaction,
 * so that transactional offset commits can be performed via {@link TxnOffsetCommitRequest}.
 *
 * @param transactionalId the transactional ID
 * @param producerId      the producer ID
 * @param producerEpoch   the producer epoch
 * @param groupId         the consumer group ID whose offsets will be committed in this transaction
 * @since 0.1.0
 */
public record AddOffsetsToTxnRequest(String transactionalId, long producerId,
                                     short producerEpoch, String groupId) {
}
