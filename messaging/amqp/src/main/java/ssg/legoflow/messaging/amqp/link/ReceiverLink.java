package ssg.legoflow.messaging.amqp.link;

import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.common.AmqpConstants;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.delivery.DeliveryState;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.transport.Performative;
import ssg.legoflow.messaging.amqp.transport.PerformativeCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
/**
 * An AMQP 1.0 receiver link — receives messages from the remote sender.
 *
 * <p>Credit-based flow control: the receiver grants credit to the sender
 * to allow message transfers. Messages are queued internally and can be
 * consumed via {@link #receive()} or {@link #receive(long, TimeUnit)}.
 *
 * @since 0.1.0
 */
public final class ReceiverLink {

    private static final Logger LOG = LoggerFactory.getLogger(ReceiverLink.class);

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
    private final AtomicLong grantedCredit = new AtomicLong(0);

    private final BlockingQueue<Delivery> receivedMessages = new LinkedBlockingQueue<>();
    private final Map<Long, Delivery> unsettledDeliveries = new ConcurrentHashMap<>();
    private volatile AmqpSession session;
    private volatile AmqpClient client;
    private volatile MessageHandler messageHandler;

    /** Sets the client reference so {@link #receive(long, TimeUnit)} can poll frames. */
    public void client(AmqpClient client) { this.client = client; }

    /**
     * Callback for message reception.
     */
    @FunctionalInterface
    public interface MessageHandler {
        /**
         * Called when a message is received on this link.
         *
         * @param delivery the delivery containing the message
         */
        void onMessage(Delivery delivery);
    }

    /**
     * Creates a new receiver link.
     *
     * @param name          the link name
     * @param handle        the link handle
     * @param sourceAddress the source address
     * @param targetAddress the target address
     */
    public ReceiverLink(String name, long handle, String sourceAddress, String targetAddress) {
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

    /** Returns unsettled deliveries. */
    public Map<Long, Delivery> unsettledDeliveries() { return unsettledDeliveries; }

    /** Sets the session. */
    public void session(AmqpSession session) { this.session = session; }

    /** Returns the session. */
    public AmqpSession session() { return session; }

    /** Sets the message handler. */
    public void messageHandler(MessageHandler handler) { this.messageHandler = handler; }

    /**
     * Creates the attach performative for this receiver link.
     *
     * @return the attach performative
     */
    public Performative.Attach createAttach() {
        return new Performative.Attach(
                name, handle, true, // role=true means receiver
                PerformativeCodec.encodeSource(sourceAddress),
                PerformativeCodec.encodeTarget(targetAddress)
        );
    }

    /**
     * Issues credit to the remote sender, allowing it to send messages.
     *
     * <p>Per AMQP 1.0 (ISO/IEC 19464-1): when {@code handle} and
     * {@code link-credit} are set, {@code next-incoming-id} and
     * {@code incoming-window} MUST be null (per-link flow control).
     *
     * @param credit the number of messages to allow
     */
    public void issueCredit(long credit) {
        this.linkCredit.addAndGet(credit);
        this.grantedCredit.addAndGet(credit);
        if (session != null) {
            var flow = new Performative.Flow(
                    session.nextIncomingId(),          // next-incoming-id
                    session.incomingWindow(),           // incoming-window
                    null, null,                         // next-outgoing-id, outgoing-window
                    handle,                             // handle
                    deliveryCount.get(),                // delivery-count
                    grantedCredit.get(),                // link-credit
                    null, false, false, Map.of()
            );
            session.send(flow);
        }
        LOG.debug("Issued {} credit on receiver link '{}', total granted={}", credit, name, grantedCredit.get());
    }

    /**
     * Handles an incoming transfer, delivering the message.
     *
     * @param deliveryId the delivery-id
     * @param tag        the delivery-tag
     * @param message    the decoded message
     * @param settled    whether pre-settled
     */
    public void handleTransfer(long deliveryId, byte[] tag, AmqpMessage message, boolean settled) {
        deliveryCount.incrementAndGet();
        linkCredit.decrementAndGet();

        var delivery = new Delivery(deliveryId, tag, message, settled);
        if (!settled) {
            unsettledDeliveries.put(deliveryId, delivery);
        }

        if (messageHandler != null) {
            messageHandler.onMessage(delivery);
        }
        receivedMessages.offer(delivery);

        LOG.debug("Received message on link '{}': deliveryId={}, settled={}", name, deliveryId, settled);

        // Auto-replenish credit when running low
        if (linkCredit.get() <= AmqpConstants.DEFAULT_LINK_CREDIT / 4) {
            issueCredit(AmqpConstants.DEFAULT_LINK_CREDIT);
        }
    }

    /**
     * Receives the next message, blocking until one is available.
     * Reads frames from transport via the client and processes them
     * through the state machine until a TRANSFER for this link arrives.
     *
     * @return the delivery
     * @throws InterruptedException if interrupted while waiting
     */
    public Delivery receive() throws InterruptedException {
        // Check queue first — message may have arrived while we were doing something else
        if (!receivedMessages.isEmpty()) {
            return receivedMessages.poll();
        }
        // Otherwise poll frames from transport until one arrives
        return pollFromTransport(Long.MAX_VALUE);
    }

    /**
     * Receives the next message with a timeout.
     * Reads frames from transport via the client and processes them
     * through the state machine until a TRANSFER for this link arrives
     * or the timeout elapses.
     *
     * @param timeout the timeout value
     * @param unit    the timeout unit
     * @return the delivery, or null if timeout elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    public Delivery receive(long timeout, TimeUnit unit) throws InterruptedException {
        if (client == null) {
            // Fallback: direct queue poll (for non-service usage)
            return receivedMessages.poll(timeout, unit);
        }
        return pollFromTransport(unit.toMillis(timeout));
    }

    /**
     * Polls frames from the transport until a message for this link arrives or timeout.
     * This is the single-reader path: only one caller reads frames at a time.
     */
    private Delivery pollFromTransport(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (true) {
            if (!receivedMessages.isEmpty()) {
                return receivedMessages.poll();
            }

            long remainingMs = deadline - System.currentTimeMillis();
            if (remainingMs <= 0) {
                return null;
            }

            try {
                boolean gotFrame = client.pollFrame(remainingMs, TimeUnit.MILLISECONDS);
                if (!gotFrame) {
                    return null;
                }
            } catch (IOException e) {
                throw new RuntimeException("Error polling frames: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Settles a delivery with the given outcome.
     *
     * @param deliveryId the delivery-id to settle
     * @param outcome    the outcome (accepted/rejected/released/modified)
     */
    public void settle(long deliveryId, DeliveryState outcome) {
        Delivery delivery = unsettledDeliveries.remove(deliveryId);
        if (delivery != null) {
            delivery.settle(outcome);
            if (session != null) {
                var disposition = new Performative.Disposition(
                        true, // role = receiver
                        deliveryId,
                        null, // last = same as first
                        true, // settled
                        outcome.encode(),
                        false
                );
                session.send(disposition);
            }
        }
    }

    /**
     * Accepts a delivery (convenience method).
     *
     * @param deliveryId the delivery-id to accept
     */
    public void accept(long deliveryId) {
        settle(deliveryId, new DeliveryState.Accepted());
    }

    /**
     * Rejects a delivery with an error condition (convenience method).
     *
     * @param deliveryId the delivery-id to reject
     * @param condition  the error condition
     */
    public void reject(long deliveryId, String condition) {
        settle(deliveryId, new DeliveryState.Rejected(condition));
    }

    /**
     * Releases a delivery for redelivery (convenience method).
     *
     * @param deliveryId the delivery-id to release
     */
    public void release(long deliveryId) {
        settle(deliveryId, new DeliveryState.Released());
    }

    /**
     * Returns the number of messages available in the receive queue.
     *
     * @return the queue size
     */
    public int available() {
        return receivedMessages.size();
    }
}
