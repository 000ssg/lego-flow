package ssg.legoflow.messaging.amqp.link;

import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.delivery.DeliveryState;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.message.MessageCodec;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.transport.Performative;
import ssg.legoflow.messaging.amqp.transport.PerformativeCodec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An AMQP 1.0 sender link — transfers messages to the remote receiver.
 *
 * <p>Credit-based flow control: the sender can only transfer messages when
 * the remote receiver has granted link credit via flow performatives.
 *
 * @since 1.0.0
 */
public final class SenderLink {

    private static final Logger LOG = LoggerFactory.getLogger(SenderLink.class);

    /**
     * Link lifecycle states.
     */
    public enum State {
        /** Link not yet attached. */
        DETACHED,
        /** Attach sent, waiting for response. */
        ATTACH_SENT,
        /** Attach received, own attach not sent. */
        ATTACH_RCVD,
        /** Both peers have exchanged attach — link active. */
        ATTACHED,
        /** Detach sent, waiting for response. */
        DETACH_SENT,
        /** Detach received, own detach not sent. */
        DETACH_RCVD
    }

    private final String name;
    private final long handle;
    private final String sourceAddress;
    private final String targetAddress;
    private volatile State state = State.DETACHED;

    private final AtomicLong deliveryCount = new AtomicLong(0);
    private final AtomicLong linkCredit = new AtomicLong(0);
    private final AtomicLong deliveryTagCounter = new AtomicLong(0);

    private final Map<Long, Delivery> unsettledDeliveries = new ConcurrentHashMap<>();
    private volatile AmqpSession session;

    /**
     * Creates a new sender link.
     *
     * @param name          the link name (unique within session)
     * @param handle        the link handle
     * @param sourceAddress the source address
     * @param targetAddress the target address
     */
    public SenderLink(String name, long handle, String sourceAddress, String targetAddress) {
        this.name = name;
        this.handle = handle;
        this.sourceAddress = sourceAddress;
        this.targetAddress = targetAddress;
    }

    /** Returns the link name. */
    public String name() { return name; }

    /** Returns the link handle. */
    public long handle() { return handle; }

    /** Returns the source address. */
    public String sourceAddress() { return sourceAddress; }

    /** Returns the target address. */
    public String targetAddress() { return targetAddress; }

    /** Returns the link state. */
    public State state() { return state; }

    /** Sets the link state. */
    public void state(State state) { this.state = state; }

    /** Returns the delivery count. */
    public long deliveryCount() { return deliveryCount.get(); }

    /** Returns the current link credit. */
    public long linkCredit() { return linkCredit.get(); }

    /** Returns unsettled deliveries. */
    public Map<Long, Delivery> unsettledDeliveries() { return unsettledDeliveries; }

    /** Sets the session. */
    public void session(AmqpSession session) { this.session = session; }

    /** Returns the session. */
    public AmqpSession session() { return session; }

    /**
     * Creates the attach performative for this sender link.
     *
     * @return the attach performative
     */
    public Performative.Attach createAttach() {
        return new Performative.Attach(
                name, handle, false, // role=false means sender
                PerformativeCodec.encodeSource(sourceAddress),
                PerformativeCodec.encodeTarget(targetAddress)
        );
    }

    /**
     * Grants link credit (called when a flow from the receiver is received).
     *
     * @param deliveryCount the receiver's delivery count
     * @param credit        the credit granted
     */
    public void grantCredit(long deliveryCount, long credit) {
        // Credit calculation: available = deliveryCount + linkCredit - senderDeliveryCount
        this.linkCredit.set(deliveryCount + credit - this.deliveryCount.get());
        LOG.debug("Sender link '{}' credit updated: available={}", name, this.linkCredit.get());
    }

    /**
     * Checks whether this sender has credit to send a message.
     *
     * @return true if credit is available
     */
    public boolean hasCredit() {
        return linkCredit.get() > 0;
    }

    /**
     * Sends a message through this link.
     *
     * @param message  the message to send
     * @param settled  whether to pre-settle (at-most-once)
     * @return the delivery, or null if no credit available
     */
    public Delivery send(AmqpMessage message, boolean settled) {
        if (!hasCredit()) {
            LOG.debug("No credit available on sender link '{}'", name);
            return null;
        }
        if (session == null) {
            throw new IllegalStateException("Sender link not attached to a session");
        }

        long deliveryId = session.allocateDeliveryId();
        if (deliveryId < 0) {
            LOG.debug("Session window exhausted for sender link '{}'", name);
            return null;
        }

        byte[] tag = generateDeliveryTag();
        ByteBuffer payload = MessageCodec.encode(message);

        var delivery = new Delivery(deliveryId, tag, message, settled);
        if (!settled) {
            unsettledDeliveries.put(deliveryId, delivery);
        }

        var transfer = new Performative.Transfer(handle, deliveryId, tag, settled);
        session.send(transfer, payload);

        linkCredit.decrementAndGet();
        deliveryCount.incrementAndGet();

        LOG.debug("Sent message on link '{}': deliveryId={}, settled={}", name, deliveryId, settled);
        return delivery;
    }

    /**
     * Handles a disposition for deliveries on this link.
     *
     * @param first   the first delivery-id
     * @param last    the last delivery-id (or null for single)
     * @param settled whether settled
     * @param state   the delivery state
     */
    public void handleDisposition(long first, Long last, boolean settled, DeliveryState state) {
        long end = last != null ? last : first;
        for (long id = first; id <= end; id++) {
            Delivery delivery = unsettledDeliveries.get(id);
            if (delivery != null) {
                if (settled) {
                    delivery.settle(state);
                    unsettledDeliveries.remove(id);
                } else {
                    delivery.updateState(state);
                }
            }
        }
    }

    private byte[] generateDeliveryTag() {
        long tagVal = deliveryTagCounter.getAndIncrement();
        byte[] tag = new byte[8];
        for (int i = 7; i >= 0; i--) {
            tag[i] = (byte) (tagVal & 0xFF);
            tagVal >>= 8;
        }
        return tag;
    }
}
