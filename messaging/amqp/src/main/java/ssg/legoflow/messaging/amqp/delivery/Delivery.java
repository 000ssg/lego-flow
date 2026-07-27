package ssg.legoflow.messaging.amqp.delivery;

import ssg.legoflow.messaging.amqp.message.AmqpMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks the state of a message delivery through its lifecycle.
 *
 * <p>A delivery is created when a transfer is sent or received, and tracks
 * the delivery-id, delivery-tag, settlement state, and outcome until the
 * delivery is fully settled.
 *
 * @since 1.0.0
 */
public final class Delivery {

    private final long deliveryId;
    private final byte[] deliveryTag;
    private final AmqpMessage message;
    private final boolean senderSettled;
    private final AtomicReference<DeliveryState> state = new AtomicReference<>();
    private final CompletableFuture<DeliveryState> settlement = new CompletableFuture<>();
    private volatile boolean settled;

    /**
     * Creates a new delivery.
     *
     * @param deliveryId    the delivery-id
     * @param deliveryTag   the delivery-tag
     * @param message       the message being delivered
     * @param senderSettled whether the sender pre-settled this delivery
     */
    public Delivery(long deliveryId, byte[] deliveryTag, AmqpMessage message, boolean senderSettled) {
        this.deliveryId = deliveryId;
        this.deliveryTag = deliveryTag;
        this.message = message;
        this.senderSettled = senderSettled;
        this.settled = senderSettled;
    }

    /** Returns the delivery-id. */
    public long deliveryId() { return deliveryId; }

    /** Returns the delivery-tag. */
    public byte[] deliveryTag() { return deliveryTag; }

    /** Returns the message. */
    public AmqpMessage message() { return message; }

    /** Returns whether the sender pre-settled this delivery. */
    public boolean isSenderSettled() { return senderSettled; }

    /** Returns whether this delivery is settled. */
    public boolean isSettled() { return settled; }

    /** Returns the current delivery state. */
    public DeliveryState state() { return state.get(); }

    /**
     * Settles this delivery with the given state.
     *
     * @param outcome the terminal delivery state
     */
    public void settle(DeliveryState outcome) {
        this.state.set(outcome);
        this.settled = true;
        this.settlement.complete(outcome);
    }

    /**
     * Returns a future that completes when this delivery is settled.
     *
     * @return the settlement future
     */
    public CompletableFuture<DeliveryState> onSettlement() {
        return settlement;
    }

    /**
     * Updates the delivery state without settling.
     *
     * @param newState the new state
     */
    public void updateState(DeliveryState newState) {
        this.state.set(newState);
    }
}
