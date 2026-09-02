package ssg.legoflow.mqtt.broker;

import ssg.legoflow.mqtt.protocol.PublishPacket;
import ssg.legoflow.mqtt.protocol.TopicSubscription;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Represents an MQTT client session on the broker.
 *
 * <p>Manages subscriptions, queued messages for offline clients, and
 * in-flight message tracking. Persistent sessions survive client reconnection
 * when {@code cleanSession} is {@code false}.
 *
 * @since 0.1.0
 */
public final class MqttSession {

    private final String clientId;
    private final boolean cleanSession;
    private final Map<String, TopicSubscription> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<PublishPacket> queuedMessages = new ConcurrentLinkedQueue<>();
    private final Map<Integer, PublishPacket> inflightMessages = new ConcurrentHashMap<>();
    private final AtomicInteger packetIdGenerator = new AtomicInteger(1);
    private final int maxQueuedMessages;
    private final Instant createdAt;
    private volatile long sessionExpiryInterval; // seconds, 0 = no expiry
    private volatile Instant disconnectedAt;
    private volatile boolean connected;

    /**
     * Creates a new session.
     *
     * @param clientId          the client identifier
     * @param cleanSession      whether this is a clean session
     * @param maxQueuedMessages the maximum number of queued messages
     */
    public MqttSession(String clientId, boolean cleanSession, int maxQueuedMessages) {
        this(clientId, cleanSession, maxQueuedMessages, 0);
    }

    /**
     * Creates a new session with a session expiry interval.
     *
     * @param clientId              the client identifier
     * @param cleanSession          whether this is a clean session
     * @param maxQueuedMessages     the maximum number of queued messages
     * @param sessionExpiryInterval the session expiry interval in seconds (0 = no expiry)
     */
    public MqttSession(String clientId, boolean cleanSession, int maxQueuedMessages,
                        long sessionExpiryInterval) {
        this.clientId = clientId;
        this.cleanSession = cleanSession;
        this.maxQueuedMessages = maxQueuedMessages;
        this.createdAt = Instant.now();
        this.sessionExpiryInterval = sessionExpiryInterval;
        this.connected = false;
    }

    /** Returns the client identifier. */
    public String clientId() { return clientId; }

    /** Returns whether this is a clean session. */
    public boolean isCleanSession() { return cleanSession; }

    /** Returns whether the client is currently connected. */
    public boolean isConnected() { return connected; }

    /** Sets the connected state and tracks disconnect time. */
    public void setConnected(boolean connected) {
        this.connected = connected;
        if (!connected) {
            this.disconnectedAt = Instant.now();
        }
    }

    /** Returns the session creation time. */
    public Instant createdAt() { return createdAt; }

    /** Returns the session expiry interval in seconds. */
    public long sessionExpiryInterval() { return sessionExpiryInterval; }

    /** Sets the session expiry interval in seconds. */
    public void setSessionExpiryInterval(long seconds) { this.sessionExpiryInterval = seconds; }

    /** Returns the time when the client disconnected, or {@code null} if connected. */
    public Instant disconnectedAt() { return disconnectedAt; }

    /**
     * Returns whether this session has expired based on the session expiry interval.
     *
     * <p>A session is expired when it is disconnected, has a non-zero expiry interval,
     * and the time since disconnect exceeds the interval.
     *
     * @return {@code true} if the session has expired
     */
    public boolean isExpired() {
        if (connected || sessionExpiryInterval <= 0 || disconnectedAt == null) {
            return false;
        }
        return Instant.now().isAfter(disconnectedAt.plusSeconds(sessionExpiryInterval));
    }

    /**
     * Adds a subscription to this session.
     *
     * @param subscription the topic subscription
     */
    public void addSubscription(TopicSubscription subscription) {
        subscriptions.put(subscription.topicFilter(), subscription);
    }

    /**
     * Removes a subscription from this session.
     *
     * @param topicFilter the topic filter to unsubscribe
     */
    public void removeSubscription(String topicFilter) {
        subscriptions.remove(topicFilter);
    }

    /**
     * Returns all subscriptions as an unmodifiable map.
     *
     * @return the subscriptions
     */
    public Map<String, TopicSubscription> getSubscriptions() {
        return Collections.unmodifiableMap(subscriptions);
    }

    /**
     * Queues a message for offline delivery.
     *
     * @param packet the PUBLISH packet to queue
     */
    public void queueMessage(PublishPacket packet) {
        if (queuedMessages.size() < maxQueuedMessages) {
            queuedMessages.offer(packet);
        }
    }

    /**
     * Drains all queued messages.
     *
     * @return the list of queued messages
     */
    public List<PublishPacket> drainQueuedMessages() {
        List<PublishPacket> messages = new ArrayList<>();
        PublishPacket packet;
        while ((packet = queuedMessages.poll()) != null) {
            messages.add(packet);
        }
        return messages;
    }

    /**
     * Returns the number of queued messages.
     *
     * @return the queue size
     */
    public int queuedMessageCount() {
        return queuedMessages.size();
    }

    /**
     * Tracks an in-flight message.
     *
     * @param packetId the packet identifier
     * @param packet   the PUBLISH packet
     */
    public void addInflightMessage(int packetId, PublishPacket packet) {
        inflightMessages.put(packetId, packet);
    }

    /**
     * Removes a tracked in-flight message.
     *
     * @param packetId the packet identifier
     * @return the removed packet, or {@code null}
     */
    public PublishPacket removeInflightMessage(int packetId) {
        return inflightMessages.remove(packetId);
    }

    /**
     * Returns the number of in-flight messages.
     *
     * @return the in-flight count
     */
    public int inflightCount() {
        return inflightMessages.size();
    }

    /**
     * Generates the next packet identifier for this session.
     *
     * @return the next packet ID (1-65535)
     */
    public int nextPacketId() {
        return packetIdGenerator.getAndUpdate(id -> id >= 65535 ? 1 : id + 1);
    }

    /**
     * Clears all session state (subscriptions, queued messages, in-flight).
     */
    public void clear() {
        subscriptions.clear();
        queuedMessages.clear();
        inflightMessages.clear();
    }
}
