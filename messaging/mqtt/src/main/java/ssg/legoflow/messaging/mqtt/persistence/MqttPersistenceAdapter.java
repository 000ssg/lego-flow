package ssg.legoflow.messaging.mqtt.persistence;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter for MQTT broker data.
 *
 * <p>Provides durable storage for sessions, retained messages, and queued
 * messages so the broker can survive restarts without losing state.</p>
 *
 * <p>Implementations are expected to be thread-safe. The broker calls these
 * methods from the connection management threads.</p>
 *
 * @since 0.2.0
 */
public interface MqttPersistenceAdapter extends AutoCloseable {

    /**
     * Saves a session's state for later recovery.
     *
     * @param clientId      the client identifier
     * @param cleanSession  whether the session uses clean session mode
     * @param expiryInterval the session expiry interval in seconds (0 = infinite)
     * @param subscriptions the session's topic subscriptions
     * @param willTopic     the last will topic, or {@code null} if no will
     * @param willPayload   the last will payload, or {@code null} if no will
     * @param willQos       the last will QoS level, or 0 if no will
     * @param willRetain    whether the last will is retained, or {@code false} if no will
     */
    void saveSession(String clientId, boolean cleanSession, long expiryInterval,
                     List<MqttSubscriptionData> subscriptions,
                     String willTopic, byte[] willPayload, int willQos, boolean willRetain);

    /**
     * Loads a session's state for recovery.
     *
     * @param clientId the client identifier
     * @return the saved session data, or empty if the session was not found
     */
    Optional<MqttSessionData> loadSession(String clientId);

    /**
     * Removes a session from persistent storage.
     *
     * @param clientId the client identifier
     */
    void removeSession(String clientId);

    /**
     * Queues a message for a disconnected session.
     *
     * @param clientId the client identifier
     * @param topic    the message topic
     * @param payload  the message payload
     * @param qos      the message QoS level
     * @param retain   whether the message has the retain flag set
     */
    void queueMessage(String clientId, String topic, ByteBuffer payload, int qos, boolean retain);

    /**
     * Drains all queued messages for a session.
     *
     * @param clientId the client identifier
     * @return the list of queued messages (consumed from storage)
     */
    List<MqttQueuedMessage> drainMessages(String clientId);

    /**
     * Saves a retained message for a topic.
     *
     * @param topic   the topic
     * @param payload the message payload (empty array removes the retained message)
     * @param qos     the QoS level
     */
    void saveRetained(String topic, byte[] payload, int qos);

    /**
     * Loads retained messages matching a topic.
     *
     * @param topic the exact topic name
     * @return the retained payload, or empty if none
     */
    Optional<byte[]> loadRetained(String topic);

    /**
     * Loads all retained messages.
     *
     * @return the list of all retained messages
     */
    List<MqttRetainedMessage> loadAllRetained();

    /**
     * Removes a retained message for a topic.
     *
     * @param topic the topic
     */
    void removeRetained(String topic);

    /**
     * Removes all persisted data.
     */
    void clearAll();

    @Override
    void close();

    /**
     * Data for a topic subscription.
     */
    record MqttSubscriptionData(String topicFilter, int qos) {}

    /**
     * A queued message for a disconnected session.
     */
    record MqttQueuedMessage(String topic, ByteBuffer payload, int qos, boolean retain) {}

    /**
     * A retained message for a topic.
     */
    record MqttRetainedMessage(String topic, byte[] payload, int qos) {}

    /**
     * Saved session data for recovery.
     */
    record MqttSessionData(
            boolean cleanSession,
            long expiryInterval,
            List<MqttSubscriptionData> subscriptions,
            String willTopic,
            byte[] willPayload,
            int willQos,
            boolean willRetain
    ) {}
}
