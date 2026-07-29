package ssg.legoflow.messaging.amqp.demo;

import ssg.legoflow.messaging.amqp.delivery.DeliveryState;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.message.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transaction demo illustrating transactional delivery state.
 *
 * <p>Shows how messages can be sent or received within a local transaction
 * using {@link DeliveryState.TransactionalState}. The transaction coordinator
 * is local only — no distributed transactions.
 *
 * @since 1.0.0
 */
public final class TransactionDemo {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionDemo.class);

    private TransactionDemo() {}

    /**
     * Creates a sample transactional delivery state for commit.
     *
     * @return a transactional state with accepted outcome
     */
    public static DeliveryState.TransactionalState createCommitState() {
        byte[] txnId = "txn-001".getBytes();
        return new DeliveryState.TransactionalState(txnId, new DeliveryState.Accepted());
    }

    /**
     * Creates a sample transactional delivery state for rollback.
     *
     * @return a transactional state with released outcome
     */
    public static DeliveryState.TransactionalState createRollbackState() {
        byte[] txnId = "txn-002".getBytes();
        return new DeliveryState.TransactionalState(txnId, new DeliveryState.Released());
    }

    /**
     * Creates a sample message for transactional send.
     *
     * @param text the message body
     * @return the message with properties
     */
    public static AmqpMessage createTransactionalMessage(String text) {
        return new AmqpMessage()
                .properties(Properties.builder()
                        .messageId("txn-msg-1")
                        .groupId("txn-group")
                        .build())
                .bodyString(text);
    }
}
