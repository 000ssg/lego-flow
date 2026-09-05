package ssg.legoflow.messaging.stomp.core;

import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter for STOMP broker data.
 *
 * <p>Provides durable storage for sessions, messages, and subscriptions
 * to survive broker restarts. Similar to the MQTT {@code MqttPersistenceAdapter}.</p>
 *
 * @since 0.2.0
 */
public interface StompPersistenceAdapter {

    /** Subscription data for persistence. */
    record StompSubscriptionData(String subscriptionId, String destination, String ackMode) {}

    /** Session data for persistence. */
    record StompSessionData(
            String sessionId,
            String login,
            String negotiatedVersion,
            List<StompSubscriptionData> subscriptions
    ) {}

    /**
     * Saves a session and its subscriptions for later recovery.
     */
    void saveSession(StompSessionData data);

    /**
     * Loads a session by session ID.
     */
    Optional<StompSessionData> loadSession(String sessionId);

    /**
     * Loads all persisted sessions.
     */
    List<StompSessionData> loadAllSessions();

    /**
     * Removes a session from persistence.
     */
    void removeSession(String sessionId);

    /**
     * Queues a message for a destination (for client-individual ack re-delivery).
     */
    void queueMessage(String destination, StompFrame frame);

    /**
     * Loads queued messages for a destination.
     */
    List<StompFrame> loadQueuedMessages(String destination);

    /**
     * Removes a message from the queue.
     */
    void removeFromQueue(String messageId);

    /**
     * Closes the persistence adapter.
     */
    void close();
}
