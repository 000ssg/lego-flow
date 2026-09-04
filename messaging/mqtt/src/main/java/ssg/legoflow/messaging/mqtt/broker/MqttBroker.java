package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.codec.MqttCodec;
import ssg.legoflow.messaging.mqtt.protocol.*;
import ssg.legoflow.messaging.mqtt.transport.MqttTransport;
import ssg.legoflow.messaging.mqtt.topic.TopicTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight MQTT message broker.
 *
 * <p>Transport-agnostic: accepts {@link MqttTransport} instances via {@link #handleConnection(MqttTransport)}.
 * TLS, TCP, WebSocket — all handled by transport wrappers. The broker only does protocol logic.</p>
 *
 * <p>Manages sessions (clean and persistent), routes PUBLISH messages to matching subscribers
 * via a {@link TopicTree}, supports QoS 0/1/2 delivery, retained messages, will message delivery,
 * pluggable authentication, session expiry, keep-alive timeout enforcement, and QoS downgrade.</p>
 *
 * @since 0.1.0
 */
public final class MqttBroker implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MqttBroker.class);

    private final MqttBrokerConfig config;
    private final TopicTree<ClientConnection> topicTree = new TopicTree<>();
    private final RetainStore retainStore = new RetainStore();
    private final Map<String, MqttSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ClientConnection> connectedClients = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Protocol flow listener — set to null for no-ops. */
    private volatile MqttEventListener listener = null;

    private volatile ScheduledExecutorService sessionExpiryScheduler;

    /**
     * Sets the protocol event listener.
     *
     * @param listener the listener, or {@code null} to disable
     */
    public void setListener(MqttEventListener listener) {
        this.listener = listener;
    }

    /**
     * Returns the current protocol event listener, or {@link MqttEventListener#noOp()} if none.
     */
    public MqttEventListener getListener() {
        return listener != null ? listener : MqttEventListener.noOp();
    }

    /**
     * Creates a new MQTT broker with the given configuration.
     *
     * @param config the broker configuration
     */
    public MqttBroker(MqttBrokerConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Initializes the broker and starts session expiry sweep.
     * Called by {@code MqttBrokerService.doConnect()}.
     */
    public void start() {
        running.set(true);
        startSessionExpirySweep();
        LOG.info("MQTT broker initialized");
    }

    /**
     * Stops the broker and disconnects all clients.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                if (sessionExpiryScheduler != null) {
                    sessionExpiryScheduler.shutdownNow();
                    sessionExpiryScheduler = null;
                }
                for (var conn : connectedClients.values()) {
                    conn.close();
                }
                connectedClients.clear();
                executor.shutdown();
                LOG.info("MQTT broker stopped");
            } catch (Exception e) {
                LOG.error("Error stopping broker", e);
            }
        }
    }

    /** Returns the set of currently connected client IDs. */
    public Set<String> getConnectedClients() {
        return Collections.unmodifiableSet(connectedClients.keySet());
    }

    /** Returns the retain store. */
    public RetainStore getRetainStore() {
        return retainStore;
    }

    /** Returns the session store (for testing/inspection). */
    public Map<String, MqttSession> getSessions() {
        return Collections.unmodifiableMap(sessions);
    }

    @Override
    public void close() {
        stop();
    }

    // --- Connection handling (transport-agnostic) ---

    /**
     * Handles a new client connection via the given transport.
     * Spawns a virtual thread for the connection lifecycle.
     *
     * @param transport the transport for this connection (TLS already applied if needed)
     */
    public void handleConnection(MqttTransport transport) {
        executor.submit(() -> handleClient(transport));
    }

    private void handleClient(MqttTransport transport) {
        ClientConnection conn = null;
        try {
            ByteBuffer buf = ByteBuffer.allocate(65536);

            // Read CONNECT packet
            int bytesRead = transport.receive(buf);
            if (bytesRead <= 0) {
                transport.close();
                return;
            }
            buf.flip();

            // Detect version from CONNECT packet
            MqttVersion version = detectVersion(buf);
            // Per-connection codec — stateful, holds an internal accumulator for partial packet reassembly
            MqttCodec codec = new MqttCodec(version);
            MqttPacket packet = codec.decode(buf);

            if (!(packet instanceof ConnectPacket connectPacket)) {
                transport.close();
                return;
            }

            String clientId = connectPacket.clientId();
            conn = new ClientConnection(clientId, transport, codec, version,
                    connectPacket.username());

            // Authentication check
            if (config.authenticator() != null) {
                if (!config.authenticator().authenticate(connectPacket.username(), connectPacket.password())) {
                    var connAck = new ConnAckPacket(false, ConnectReturnCode.BAD_CREDENTIALS,
                            new MqttProperties());
                    sendPacket(conn, connAck);
                    LOG.info("Client authentication failed: {}", clientId);
                    transport.close();
                    return;
                }
            }

            // Handle existing session — Clean Start (v5.0) / Clean Session (v3.1.1)
            MqttSession existingSession = sessions.get(clientId);
            boolean sessionPresent = false;

            if (existingSession != null) {
                if (connectPacket.cleanSession()) {
                    existingSession.clear();
                    sessions.remove(clientId);
                } else {
                    sessionPresent = true;
                }
                // Disconnect existing client with same ID
                var existingConn = connectedClients.remove(clientId);
                if (existingConn != null) {
                    existingConn.close();
                }
            }

            // Create or reuse session
            MqttSession session;
            if (connectPacket.cleanSession() || existingSession == null) {
                long expiryInterval = connectPacket.properties().getSessionExpiryInterval()
                        .orElse(config.sessionExpiryInterval());
                session = new MqttSession(clientId, connectPacket.cleanSession(),
                        config.maxQueuedMessages(), expiryInterval);
                sessions.put(clientId, session);
            } else {
                session = existingSession;
                connectPacket.properties().getSessionExpiryInterval()
                        .ifPresent(session::setSessionExpiryInterval);
            }
            session.setConnected(true);
            conn.setSession(session);
            conn.setKeepAlive(connectPacket.keepAlive());

            // Store will message
            if (connectPacket.will() != null) {
                conn.setWillMessage(connectPacket.will());
            }

            connectedClients.put(clientId, conn);

            // Send CONNACK
            var connAck = new ConnAckPacket(sessionPresent, ConnectReturnCode.ACCEPTED,
                    new MqttProperties());
            sendPacket(conn, connAck);
            LOG.info("Client connected: {}", clientId);

            // Fire protocol events
            MqttEventListener ev = listener;
            if (ev != null) {
                ev.onEvent(MqttEventListener.EventType.CLIENT_CONNECTED, clientId, null);
                ev.onEvent(sessionPresent
                        ? MqttEventListener.EventType.SESSION_RESUMED
                        : MqttEventListener.EventType.SESSION_CREATED, clientId, null);
            }

            // Re-subscribe persistent subscriptions
            if (sessionPresent) {
                for (var sub : session.getSubscriptions().values()) {
                    topicTree.subscribe(sub.topicFilter(), conn);
                }
            }

            // Deliver queued messages
            for (var queued : session.drainQueuedMessages()) {
                sendPacket(conn, queued);
            }

            // Start keep-alive timeout monitoring
            startKeepAliveMonitor(conn);

            // Main read loop
            while (running.get() && conn.isOpen()) {
                buf.clear();
                bytesRead = transport.receive(buf);
                if (bytesRead == -1) break;
                if (bytesRead > 0) {
                    buf.flip();
                    conn.updateLastActivity();
                    List<MqttPacket> packets = codec.decodeAll(buf);
                    for (var p : packets) {
                        handlePacket(conn, session, p, connectPacket);
                    }
                }
            }

        } catch (IOException e) {
            if (running.get()) {
                LOG.debug("Client connection error: {}", e.getMessage());
            }
        } finally {
            if (conn != null) {
                disconnectClient(conn);
            }
        }
    }

    private void handlePacket(ClientConnection conn, MqttSession session,
                              MqttPacket packet, ConnectPacket connectPacket) throws IOException {
        switch (packet) {
            case PublishPacket pub -> handlePublish(conn, session, pub);
            case PubAckPacket ack -> session.removeInflightMessage(ack.packetId());
            case PubRecPacket rec -> sendPacket(conn, new PubRelPacket(rec.packetId(),
                    ReasonCode.SUCCESS, new MqttProperties()));
            case PubRelPacket rel -> {
                session.removeInflightMessage(rel.packetId());
                sendPacket(conn, new PubCompPacket(rel.packetId(), ReasonCode.SUCCESS,
                        new MqttProperties()));
            }
            case PubCompPacket comp -> session.removeInflightMessage(comp.packetId());
            case SubscribePacket sub -> handleSubscribe(conn, session, sub);
            case UnsubscribePacket unsub -> handleUnsubscribe(conn, session, unsub);
            case PingReqPacket ignored -> sendPacket(conn, new PingRespPacket());
            case DisconnectPacket disconnect -> handleDisconnect(conn, disconnect);
            default -> LOG.debug("Unhandled packet from {}: {}", conn.clientId(), packet.type());
        }
    }

    private void handleDisconnect(ClientConnection conn, DisconnectPacket disconnect) {
        ReasonCode reason = disconnect.reasonCode();
        if (reason == ReasonCode.DISCONNECT_WITH_WILL) {
            conn.setCleanDisconnect(false);
        } else {
            conn.setCleanDisconnect(true);
        }
        LOG.debug("Client {} sent DISCONNECT with reason: {}", conn.clientId(), reason);
        conn.close();
    }

    private void handlePublish(ClientConnection conn, MqttSession session,
                               PublishPacket pub) throws IOException {
        // ACL check — deny publish if not allowed
        if (config.aclChecker() != null && !config.aclChecker().check(conn.username(), pub.topic(), "pub")) {
            LOG.info("ACL denied publish from {} on {}", conn.clientId(), pub.topic());
            sendPacket(conn, new PubAckPacket(pub.packetId(), ReasonCode.NOT_AUTHORIZED,
                    new MqttProperties()));
            return;
        }
        // Retain handling
        if (pub.retain()) {
            retainStore.put(pub.topic(), pub.payload());
        }

        // QoS acknowledgements
        if (pub.qos() == QoS.AT_LEAST_ONCE) {
            sendPacket(conn, new PubAckPacket(pub.packetId(), ReasonCode.SUCCESS,
                    new MqttProperties()));
        } else if (pub.qos() == QoS.EXACTLY_ONCE) {
            sendPacket(conn, new PubRecPacket(pub.packetId(), ReasonCode.SUCCESS,
                    new MqttProperties()));
        }

        // Route to subscribers with QoS downgrade
        Set<ClientConnection> subscribers = topicTree.getMatchingSubscribers(pub.topic());
        for (var subscriber : subscribers) {
            if (subscriber == conn) continue;
            MqttSession subSession = subscriber.getSession();
            if (subSession != null && subSession.isConnected()) {
                QoS effectiveQoS = downgradeQoS(pub.qos(), pub.topic(), subSession);
                int newPacketId = effectiveQoS == QoS.AT_MOST_ONCE ? 0 : subSession.nextPacketId();
                var forward = new PublishPacket(pub.topic(), pub.payload(), effectiveQoS,
                        false, false, newPacketId, pub.properties());
                sendPacket(subscriber, forward);
                if (effectiveQoS != QoS.AT_MOST_ONCE) {
                    subSession.addInflightMessage(newPacketId, forward);
                }
            } else if (subSession != null && !subSession.isCleanSession()) {
                subSession.queueMessage(pub);
            }
        }
    }

    private QoS downgradeQoS(QoS publishQoS, String topic, MqttSession subscriberSession) {
        QoS subscriberMaxQoS = publishQoS;
        for (var sub : subscriberSession.getSubscriptions().values()) {
            var tf = new ssg.legoflow.messaging.mqtt.topic.TopicFilter(sub.topicFilter());
            if (tf.matches(topic)) {
                if (sub.qos().value() < subscriberMaxQoS.value()) {
                    subscriberMaxQoS = sub.qos();
                }
            }
        }
        return publishQoS.value() <= subscriberMaxQoS.value() ? publishQoS : subscriberMaxQoS;
    }

    private void handleSubscribe(ClientConnection conn, MqttSession session,
                                 SubscribePacket sub) throws IOException {
        List<ReasonCode> reasonCodes = new ArrayList<>();
        MqttEventListener ev = listener;
        for (var subscription : sub.subscriptions()) {
            // ACL check — deny subscribe if not allowed
            if (config.aclChecker() != null
                    && !config.aclChecker().check(conn.username(), subscription.topicFilter(), "sub")) {
                LOG.info("ACL denied subscribe from {} on {}", conn.clientId(), subscription.topicFilter());
                reasonCodes.add(ReasonCode.NOT_AUTHORIZED);
                continue;
            }
            session.addSubscription(subscription);
            topicTree.subscribe(subscription.topicFilter(), conn);
            if (ev != null) {
                ev.onEvent(MqttEventListener.EventType.SUBSCRIPTION_ADDED,
                        conn.clientId(), subscription.topicFilter());
            }
            reasonCodes.add(switch (subscription.qos()) {
                case AT_MOST_ONCE -> ReasonCode.GRANTED_QOS_0;
                case AT_LEAST_ONCE -> ReasonCode.GRANTED_QOS_1;
                case EXACTLY_ONCE -> ReasonCode.GRANTED_QOS_2;
            });
            // Send retained messages
            for (var retained : retainStore.getMatching(subscription.topicFilter())) {
                sendPacket(conn, retained);
            }
        }
        sendPacket(conn, new SubAckPacket(sub.packetId(), reasonCodes, new MqttProperties()));
    }

    private void handleUnsubscribe(ClientConnection conn, MqttSession session,
                                   UnsubscribePacket unsub) throws IOException {
        List<ReasonCode> reasonCodes = new ArrayList<>();
        for (var topic : unsub.topics()) {
            session.removeSubscription(topic);
            topicTree.unsubscribe(topic, conn);
            reasonCodes.add(ReasonCode.SUCCESS);
        }
        sendPacket(conn, new UnsubAckPacket(unsub.packetId(), reasonCodes, new MqttProperties()));
    }

    private void disconnectClient(ClientConnection conn) {
        String clientId = conn.clientId();
        MqttEventListener ev = listener;
        connectedClients.remove(clientId);
        MqttSession session = sessions.get(clientId);
        if (session != null) {
            session.setConnected(false);
            for (var sub : session.getSubscriptions().values()) {
                topicTree.unsubscribe(sub.topicFilter(), conn);
            }
            if (session.isCleanSession()) {
                sessions.remove(clientId);
            }
        }
        if (ev != null) {
            ev.onEvent(MqttEventListener.EventType.CLIENT_DISCONNECTED, clientId, null);
        }
        // Will message delivery
        if (!conn.isCleanDisconnect() && conn.getWillMessage() != null) {
            if (ev != null) {
                ev.onEvent(MqttEventListener.EventType.WILL_DELIVERED, clientId, null);
            }
            WillMessage will = conn.getWillMessage();
            var pub = new PublishPacket(will.topic(), will.payload(), will.qos(),
                    will.retain(), false, 0, will.properties());
            if (will.retain()) {
                retainStore.put(will.topic(), will.payload());
            }
            Set<ClientConnection> subscribers = topicTree.getMatchingSubscribers(will.topic());
            for (var subscriber : subscribers) {
                try {
                    sendPacket(subscriber, pub);
                } catch (IOException e) {
                    LOG.debug("Failed to deliver will message to {}", subscriber.clientId());
                }
            }
        }
        conn.close();
        LOG.info("Client disconnected: {}", clientId);
    }

    private void sendPacket(ClientConnection conn, MqttPacket packet) throws IOException {
        ByteBuffer encoded = conn.codec().encode(packet);
        conn.transport().send(encoded);
    }

    private MqttVersion detectVersion(ByteBuffer buf) {
        // Save position, scan for protocol level byte
        int pos = buf.position();
        buf.get(); // skip first byte (packet type + flags)
        // Skip remaining length
        int b;
        do { b = buf.get() & 0xFF; } while ((b & 0x80) != 0);
        // Skip protocol name length + "MQTT"
        int nameLen = buf.getShort() & 0xFFFF;
        buf.position(buf.position() + nameLen);
        int protocolLevel = buf.get() & 0xFF;
        buf.position(pos); // restore
        return MqttVersion.fromProtocolLevel(protocolLevel);
    }

    // --- Keep-alive timeout enforcement ---

    private void startKeepAliveMonitor(ClientConnection conn) {
        if (conn.getKeepAlive() <= 0) return;

        executor.submit(() -> {
            long timeoutMs = (long) (conn.getKeepAlive() * 1500L); // 1.5x keep_alive
            while (running.get() && conn.isOpen()) {
                try {
                    Thread.sleep(timeoutMs);
                    long elapsed = System.currentTimeMillis() - conn.lastActivityTime();
                    if (elapsed > timeoutMs) {
                        LOG.info("Keep-alive timeout for client {}: {}ms since last activity",
                                conn.clientId(), elapsed);
                        conn.setCleanDisconnect(false); // Deliver will on keep-alive timeout
                        MqttEventListener ev = listener;
                        if (ev != null) {
                            ev.onEvent(MqttEventListener.EventType.KEEP_ALIVE_TIMEOUT, conn.clientId(), null);
                        }
                        conn.close();
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    // --- Session expiry sweep ---

    private void startSessionExpirySweep() {
        sessionExpiryScheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("mqtt-session-expiry-", 0).factory());
        sessionExpiryScheduler.scheduleAtFixedRate(this::sweepExpiredSessions,
                30, 30, TimeUnit.SECONDS);
    }

    /**
     * Removes expired sessions from the session store.
     * Visible for testing.
     */
    void sweepExpiredSessions() {
        var iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            MqttSession session = entry.getValue();
            if (session.isExpired()) {
                LOG.info("Session expired for client: {}", entry.getKey());
                if (listener != null) {
                    listener.onEvent(MqttEventListener.EventType.SESSION_EXPIRED, entry.getKey(), null);
                }
                session.clear();
                iterator.remove();
            }
        }
    }

    /**
     * Internal representation of a connected client.
     */
    static final class ClientConnection implements AutoCloseable {
        private final String clientId;
        private final MqttTransport transport;
        private final MqttCodec codec;
        private final MqttVersion version;
        private final String username;
        private volatile MqttSession session;
        private volatile boolean cleanDisconnect = false;
        private volatile WillMessage willMessage;
        private volatile int keepAlive;
        private volatile long lastActivityTime;

        ClientConnection(String clientId, MqttTransport transport,
                         MqttCodec codec, MqttVersion version, String username) {
            this.clientId = clientId;
            this.transport = transport;
            this.codec = codec;
            this.version = version;
            this.username = username;
            this.lastActivityTime = System.currentTimeMillis();
        }

        String clientId() { return clientId; }
        MqttTransport transport() { return transport; }
        MqttCodec codec() { return codec; }
        MqttVersion version() { return version; }
        String username() { return username; }
        MqttSession getSession() { return session; }
        void setSession(MqttSession session) { this.session = session; }
        boolean isCleanDisconnect() { return cleanDisconnect; }
        void setCleanDisconnect(boolean clean) { this.cleanDisconnect = clean; }
        WillMessage getWillMessage() { return willMessage; }
        void setWillMessage(WillMessage will) { this.willMessage = will; }
        int getKeepAlive() { return keepAlive; }
        void setKeepAlive(int keepAlive) { this.keepAlive = keepAlive; }
        long lastActivityTime() { return lastActivityTime; }
        void updateLastActivity() { this.lastActivityTime = System.currentTimeMillis(); }
        boolean isOpen() { return transport.isOpen(); }

        @Override
        public void close() {
            transport.close();
        }
    }
}
