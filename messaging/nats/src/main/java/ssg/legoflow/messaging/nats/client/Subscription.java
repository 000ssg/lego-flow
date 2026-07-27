package ssg.legoflow.messaging.nats.client;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Client-side subscription with message handler and auto-unsubscribe support.
 *
 * <p>Each subscription has a unique string ID (sid), a subject pattern,
 * an optional queue group, and a message handler callback. Auto-unsubscribe
 * allows the subscription to automatically unsubscribe after receiving a
 * specified number of messages.
 *
 * @since 1.0.0
 */
public final class Subscription {

    private final String sid;
    private final String subject;
    private final String queueGroup;
    private final Consumer<NatsMessage> handler;
    private final AtomicInteger receivedCount = new AtomicInteger(0);
    private volatile int maxMessages = -1;
    private volatile boolean active = true;

    /**
     * Creates a new subscription.
     *
     * @param sid        the subscription ID
     * @param subject    the subject pattern
     * @param queueGroup the queue group, or null
     * @param handler    the message handler
     */
    public Subscription(String sid, String subject, String queueGroup, Consumer<NatsMessage> handler) {
        this.sid = Objects.requireNonNull(sid);
        this.subject = Objects.requireNonNull(subject);
        this.queueGroup = queueGroup;
        this.handler = Objects.requireNonNull(handler);
    }

    /**
     * Returns the subscription ID.
     *
     * @return the sid
     */
    public String sid() {
        return sid;
    }

    /**
     * Returns the subscribed subject.
     *
     * @return the subject
     */
    public String subject() {
        return subject;
    }

    /**
     * Returns the queue group, or null.
     *
     * @return the queue group
     */
    public String queueGroup() {
        return queueGroup;
    }

    /**
     * Returns the message handler.
     *
     * @return the handler
     */
    public Consumer<NatsMessage> handler() {
        return handler;
    }

    /**
     * Returns whether this subscription is active.
     *
     * @return true if active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets the auto-unsubscribe limit.
     *
     * @param maxMessages the max messages, or -1 for unlimited
     */
    public void setAutoUnsubscribe(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    /**
     * Returns the auto-unsubscribe limit.
     *
     * @return the max messages, or -1 for unlimited
     */
    public int maxMessages() {
        return maxMessages;
    }

    /**
     * Delivers a message to this subscription's handler.
     *
     * @param message the message to deliver
     * @return true if the subscription is still active after delivery
     */
    public boolean deliver(NatsMessage message) {
        if (!active) return false;
        handler.accept(message);
        int count = receivedCount.incrementAndGet();
        if (maxMessages > 0 && count >= maxMessages) {
            active = false;
            return false;
        }
        return true;
    }

    /**
     * Returns the number of messages received.
     *
     * @return the count
     */
    public int receivedCount() {
        return receivedCount.get();
    }

    /**
     * Deactivates this subscription.
     */
    public void unsubscribe() {
        active = false;
    }
}
