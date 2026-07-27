package ssg.legoflow.messaging.kafka.protocol;

/**
 * InitProducerId response (API key 22).
 *
 * @param errorCode     the error code
 * @param producerId    the assigned producer ID
 * @param producerEpoch the assigned producer epoch
 * @since 1.0.0
 */
public record InitProducerIdResponse(short errorCode, long producerId, short producerEpoch) {
}
