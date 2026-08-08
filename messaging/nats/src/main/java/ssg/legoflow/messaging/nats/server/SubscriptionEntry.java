package ssg.legoflow.messaging.nats.server;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side subscription entry tracking a client's subscription.
 *
 * @param clientConnection the owning client connection
 * @param sid              the subscription ID
 * @param subject          the subscribed subject
 * @param queueGroup       the queue group, or null
 * @since 0.1.0
 */
public record SubscriptionEntry(
        ClientConnection clientConnection,
        String sid,
        String subject,
        String queueGroup
) {

    private static final AtomicInteger ID_GEN = new AtomicInteger(0);

    /**
     * Returns whether this subscription belongs to a queue group.
     *
     * @return true if queue group is set
     */
    public boolean isQueued() {
        return queueGroup != null;
    }
}
