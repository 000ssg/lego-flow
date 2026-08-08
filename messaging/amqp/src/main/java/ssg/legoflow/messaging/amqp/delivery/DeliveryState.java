package ssg.legoflow.messaging.amqp.delivery;

import ssg.legoflow.messaging.amqp.types.AmqpType;
import ssg.legoflow.messaging.amqp.types.Descriptors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sealed interface for AMQP 1.0 delivery states (section 3.4).
 *
 * <p>Delivery states describe the current status of a message delivery.
 * Terminal states ({@link Accepted}, {@link Rejected}, {@link Released},
 * {@link Modified}) are also outcomes that determine the final disposition
 * of a message.
 *
 * @since 0.1.0
 */
public sealed interface DeliveryState
        permits DeliveryState.Received, DeliveryState.Accepted, DeliveryState.Rejected,
                DeliveryState.Released, DeliveryState.Modified, DeliveryState.TransactionalState {

    /**
     * Indicates partial reception — not a terminal state.
     *
     * @param sectionNumber the section number received up to
     * @param sectionOffset the byte offset within the section
     */
    record Received(long sectionNumber, long sectionOffset) implements DeliveryState {}

    /**
     * Terminal outcome: message was successfully processed.
     */
    record Accepted() implements DeliveryState {}

    /**
     * Terminal outcome: message was rejected with an error.
     *
     * @param errorCondition  the error condition symbol
     * @param errorDescription the error description
     */
    record Rejected(String errorCondition, String errorDescription) implements DeliveryState {
        /** Creates a Rejected with just an error condition. */
        public Rejected(String errorCondition) {
            this(errorCondition, null);
        }
    }

    /**
     * Terminal outcome: message was not processed but can be redelivered.
     */
    record Released() implements DeliveryState {}

    /**
     * Terminal outcome: message was not processed and annotations were modified.
     *
     * @param deliveryFailed   whether the delivery should be considered failed
     * @param undeliverableHere whether the message can be redelivered to this link
     * @param messageAnnotations modified annotations
     */
    record Modified(
            boolean deliveryFailed,
            boolean undeliverableHere,
            Map<String, Object> messageAnnotations
    ) implements DeliveryState {}

    /**
     * Transactional delivery state wrapping an outcome within a transaction.
     *
     * @param txnId   the transaction identifier
     * @param outcome the outcome within the transaction
     */
    record TransactionalState(byte[] txnId, DeliveryState outcome) implements DeliveryState {}

    /**
     * Encodes this delivery state as a described AMQP type.
     *
     * @return the encoded described type
     */
    default AmqpType.Described encode() {
        return DeliveryStateCodec.encode(this);
    }

    /**
     * Decodes a described AMQP type into a delivery state.
     *
     * @param described the described type
     * @return the decoded delivery state
     */
    static DeliveryState decode(AmqpType.Described described) {
        return DeliveryStateCodec.decode(described);
    }
}
