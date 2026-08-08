package ssg.legoflow.messaging.nats.server;

import ssg.legoflow.messaging.nats.protocol.NatsCodec;
import ssg.legoflow.messaging.nats.protocol.NatsHeaders;
import ssg.legoflow.messaging.nats.subject.SubscriptionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes published messages to matching subscriptions.
 *
 * <p>Handles exact matches, wildcard patterns, queue groups (round-robin),
 * and echo suppression. Messages are delivered by writing to the client
 * connection's output stream.
 *
 * @since 0.1.0
 */
public final class MessageRouter {

    private static final Logger LOG = LoggerFactory.getLogger(MessageRouter.class);

    private final SubscriptionRegistry<SubscriptionEntry> registry = new SubscriptionRegistry<>();
    private final Map<String, QueueGroup> queueGroups = new ConcurrentHashMap<>();

    /**
     * Registers a subscription.
     *
     * @param entry the subscription entry
     */
    public void addSubscription(SubscriptionEntry entry) {
        registry.subscribe(entry.subject(), entry);

        if (entry.isQueued()) {
            String key = entry.subject() + ":" + entry.queueGroup();
            queueGroups.computeIfAbsent(key, k -> new QueueGroup(entry.queueGroup()))
                    .addMember(entry);
        }
    }

    /**
     * Removes a subscription.
     *
     * @param entry the subscription entry
     */
    public void removeSubscription(SubscriptionEntry entry) {
        registry.unsubscribe(entry.subject(), entry);

        if (entry.isQueued()) {
            String key = entry.subject() + ":" + entry.queueGroup();
            var group = queueGroups.get(key);
            if (group != null) {
                group.removeMember(entry);
                if (group.isEmpty()) {
                    queueGroups.remove(key);
                }
            }
        }
    }

    /**
     * Routes a published message to all matching subscriptions.
     *
     * @param subject   the published subject
     * @param replyTo   the reply-to subject, or null
     * @param headers   the message headers, or null
     * @param payload   the message payload
     * @param publisher the publishing client (for echo suppression), or null
     * @param echoEnabled whether to echo messages back to publisher
     */
    public void route(String subject, String replyTo, NatsHeaders headers,
                      byte[] payload, ClientConnection publisher, boolean echoEnabled) {
        var matches = registry.match(subject);
        if (matches.isEmpty()) return;

        // Separate queue group and non-queue subscriptions
        var direct = new ArrayList<SubscriptionEntry>();
        var queueGroupKeys = new HashSet<String>();

        for (var entry : matches) {
            // Echo suppression: skip if published by this client and echo not enabled
            if (!echoEnabled && publisher != null && entry.clientConnection() == publisher) {
                continue;
            }

            if (entry.isQueued()) {
                queueGroupKeys.add(entry.subject() + ":" + entry.queueGroup());
            } else {
                direct.add(entry);
            }
        }

        // Deliver to all non-queue subscribers
        for (var entry : direct) {
            deliverToEntry(entry, subject, replyTo, headers, payload);
        }

        // Deliver to one member per queue group (round-robin)
        for (var key : queueGroupKeys) {
            var group = queueGroups.get(key);
            if (group != null) {
                var chosen = group.nextMember();
                if (chosen != null) {
                    // Echo suppression for queue group
                    if (!echoEnabled && publisher != null && chosen.clientConnection() == publisher) {
                        // Try next member
                        for (int i = 0; i < group.size() - 1; i++) {
                            chosen = group.nextMember();
                            if (chosen.clientConnection() != publisher) break;
                        }
                        if (chosen.clientConnection() == publisher) continue;
                    }
                    deliverToEntry(chosen, subject, replyTo, headers, payload);
                }
            }
        }
    }

    private void deliverToEntry(SubscriptionEntry entry, String subject,
                                 String replyTo, NatsHeaders headers, byte[] payload) {
        try {
            String encoded;
            if (headers != null) {
                encoded = NatsCodec.encodeHmsg(subject, entry.sid(), replyTo, headers, payload);
            } else {
                encoded = NatsCodec.encodeMsg(subject, entry.sid(), replyTo, payload);
            }
            entry.clientConnection().send(encoded);
        } catch (Exception e) {
            LOG.debug("Failed to deliver message to client {}: {}",
                    entry.clientConnection().id(), e.getMessage());
        }
    }

    /**
     * Returns the total number of registered subscriptions.
     *
     * @return the count
     */
    public int subscriptionCount() {
        return registry.size();
    }

    /**
     * Returns the number of queue groups.
     *
     * @return the count
     */
    public int queueGroupCount() {
        return queueGroups.size();
    }

    /**
     * Removes all subscriptions for a client.
     *
     * @param entries the subscriptions to remove
     */
    public void removeAll(Collection<SubscriptionEntry> entries) {
        for (var entry : entries) {
            removeSubscription(entry);
        }
    }
}
