package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.codec.MqttCodec;
import ssg.legoflow.messaging.mqtt.protocol.*;
import ssg.legoflow.messaging.mqtt.topic.TopicTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Lightweight MQTT message broker.
 *
 * <p>Accepts client connections, manages sessions (clean and persistent),
 * routes PUBLISH messages to matching subscribers via a {@link TopicTree},
 * supports QoS 0/1/2 delivery, retained messages, will message delivery,
 * TLS encrypted connections, pluggable authentication, session expiry,
 * keep-alive timeout enforcement, and QoS downgrade.
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

    private volatile ServerSocketChannel serverChannel;
    private volatile int boundPort;
    private volatile SSLContext sslContext;
    private volatile ScheduledExecutorService sessionExpiryScheduler;

    /**
     * Creates a new MQTT broker with the given configuration.
     *
     * @param config the broker configuration
     */
    public MqttBroker(MqttBrokerConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Starts the broker and binds to the configured host and port.
     *
     * @throws IOException if the broker cannot bind
     */
    public void start() throws IOException {
        bind(config.host(), config.port());
    }

    /**
     * Binds the broker to the given host and port and starts accepting connections.
     *
     * @param host the bind address
     * @param port the port (0 for ephemeral)
     * @throws IOException if binding fails
     */
    public void bind(String host, int port) throws IOException {
        // Initialize TLS if configured
        if (config.tlsConfig() != null) {
            try {
                sslContext = config.tlsConfig().createSslContext();
                LOG.info("TLS enabled for MQTT broker");
            } catch (Exception e) {
                throw new IOException("Failed to initialize TLS", e);
            }
        }

        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(host, port));
        boundPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
        running.set(true);
        LOG.info("MQTT broker started on {}:{}", host, boundPort);

        executor.submit(this::acceptLoop);

        // Start session expiry sweep if configured
        startSessionExpirySweep();
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
                if (serverChannel != null && serverChannel.isOpen()) {
                    serverChannel.close();
                }
                executor.shutdown();
                LOG.info("MQTT broker stopped");
            } catch (IOException e) {
                LOG.error("Error stopping broker", e);
            }
        }
    }

    /**
     * Returns the set of currently connected client IDs.
     *
     * @return the connected client IDs
     */
    public Set<String> getConnectedClients() {
        return Collections.unmodifiableSet(connectedClients.keySet());
    }

    /**
     * Returns the port the broker is bound to.
     *
     * @return the bound port
     */
    public int getPort() {
        return boundPort;
    }

    /**
     * Returns the retain store.
     *
     * @return the retain store
     */
    public RetainStore getRetainStore() {
        return retainStore;
    }

    /**
     * Returns the session store (for testing/inspection).
     *
     * @return the sessions map (unmodifiable view)
     */
    public Map<String, MqttSession> getSessions() {
        return Collections.unmodifiableMap(sessions);
    }

    /**
     * Returns whether TLS is enabled.
     *
     * @return {@code true} if TLS is configured
     */
    public boolean isTlsEnabled() {
        return sslContext != null;
    }

    @Override
    public void close() {
        stop();
    }

    // --- Private methods ---

    private void acceptLoop() {
        while (running.get()) {
            try {
                SocketChannel clientChannel = serverChannel.accept();
                if (clientChannel != null) {
                    executor.submit(() -> handleClient(clientChannel));
                }
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error accepting connection", e);
                }
            }
        }
    }

    private void handleClient(SocketChannel clientChannel) {
        ClientConnection conn = null;
        try {
            clientChannel.configureBlocking(true);

            // TLS handshake if enabled
            SSLEngine sslEngine = null;
            if (sslContext != null) {
                sslEngine = config.tlsConfig().createServerEngine(sslContext);
                performTlsHandshake(clientChannel, sslEngine);
            }

            ByteBuffer buf = ByteBuffer.allocate(65536);

            // Read CONNECT packet (through TLS if enabled)
            int bytesRead;
            if (sslEngine != null) {
                bytesRead = readTls(clientChannel, sslEngine, buf);
            } else {
                bytesRead = clientChannel.read(buf);
            }
            if (bytesRead <= 0) {
                clientChannel.close();
                return;
            }
            buf.flip();

            // Detect version from CONNECT packet
            MqttVersion version = detectVersion(buf);
            // Per-connection codec — stateful, holds an internal accumulator for partial packet reassembly
            MqttCodec codec = new MqttCodec(version);
            MqttPacket packet = codec.decode(buf);

            if (!(packet instanceof ConnectPacket connectPacket)) {
                clientChannel.close();
                return;
            }

            String clientId = connectPacket.clientId();
            conn = new ClientConnection(clientId, clientChannel, codec, version, sslEngine,
                    connectPacket.username());

            // Authentication check
            if (config.authenticator() != null) {
                if (!config.authenticator().authenticate(connectPacket.username(), connectPacket.password())) {
                    var connAck = new ConnAckPacket(false, ConnectReturnCode.BAD_CREDENTIALS,
                            new MqttProperties());
                    sendPacket(conn, connAck);
                    LOG.info("Client authentication failed: {}", clientId);
                    clientChannel.close();
                    return;
                }
            }

            // Handle existing session — Clean Start (v5.0) / Clean Session (v3.1.1)
            MqttSession existingSession = sessions.get(clientId);
            boolean sessionPresent = false;

            if (existingSession != null) {
                if (connectPacket.cleanSession()) {
                    // Clean Start = true: discard any existing session state
                    existingSession.clear();
                    sessions.remove(clientId);
                } else {
                    // Clean Start = false: resume existing session
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
                // Update expiry interval from CONNECT properties if present
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
                if (sslEngine != null) {
                    bytesRead = readTls(clientChannel, sslEngine, buf);
                } else {
                    bytesRead = clientChannel.read(buf);
                }
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
            // v5.0: Disconnect with Will — publish the will message
            conn.setCleanDisconnect(false);
        } else {
            // Normal disconnection — do not publish will
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
            if (subscriber == conn) continue; // Don't echo back
            MqttSession subSession = subscriber.getSession();
            if (subSession != null && subSession.isConnected()) {
                // QoS downgrade: deliver at min(pub QoS, subscriber's max QoS)
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

    /**
     * Downgrades QoS to the minimum of the published QoS and the subscriber's
     * maximum QoS for the matching topic filter.
     */
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
            // Unsubscribe from topic tree
            for (var sub : session.getSubscriptions().values()) {
                topicTree.unsubscribe(sub.topicFilter(), conn);
            }
            if (session.isCleanSession()) {
                sessions.remove(clientId);
            }
        }

        // Fire disconnect event
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
        if (conn.sslEngine() != null) {
            writeTls(conn.channel(), conn.sslEngine(), encoded);
        } else {
            SocketChannel ch = conn.channel();
            if (ch != null && ch.isOpen()) {
                while (encoded.hasRemaining()) {
                    ch.write(encoded);
                }
            }
        }
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

    // --- TLS support ---

    private void performTlsHandshake(SocketChannel channel, SSLEngine engine) throws IOException {
        engine.beginHandshake();
        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();

        int appBufSize = engine.getSession().getApplicationBufferSize();
        int netBufSize = engine.getSession().getPacketBufferSize();
        ByteBuffer myNetData = ByteBuffer.allocate(netBufSize);
        ByteBuffer peerNetData = ByteBuffer.allocate(netBufSize);
        ByteBuffer myAppData = ByteBuffer.allocate(appBufSize);
        ByteBuffer peerAppData = ByteBuffer.allocate(appBufSize);

        while (hs != SSLEngineResult.HandshakeStatus.FINISHED
                && hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            switch (hs) {
                case NEED_UNWRAP -> {
                    if (channel.read(peerNetData) < 0) {
                        throw new IOException("TLS handshake: connection closed");
                    }
                    peerNetData.flip();
                    SSLEngineResult res = engine.unwrap(peerNetData, peerAppData);
                    peerNetData.compact();
                    hs = res.getHandshakeStatus();
                }
                case NEED_WRAP -> {
                    myNetData.clear();
                    SSLEngineResult res = engine.wrap(myAppData, myNetData);
                    hs = res.getHandshakeStatus();
                    // If wrap returns BUFFER_UNDERFLOW, we need peer data first
                    if (res.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        hs = SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
                    }
                    myNetData.flip();
                    while (myNetData.hasRemaining()) {
                        channel.write(myNetData);
                    }
                }
                case NEED_TASK -> {
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    hs = engine.getHandshakeStatus();
                }
                default -> throw new IOException("Unexpected handshake status: " + hs);
            }
        }
    }

    private int readTls(SocketChannel channel, SSLEngine engine, ByteBuffer appBuf) throws IOException {
        int netBufSize = engine.getSession().getPacketBufferSize();
        ByteBuffer netBuf = ByteBuffer.allocate(netBufSize);
        int bytesRead = channel.read(netBuf);
        if (bytesRead <= 0) return bytesRead;
        netBuf.flip();
        SSLEngineResult res = engine.unwrap(netBuf, appBuf);
        return res.bytesProduced();
    }

    private void writeTls(SocketChannel channel, SSLEngine engine, ByteBuffer appBuf) throws IOException {
        int netBufSize = engine.getSession().getPacketBufferSize();
        ByteBuffer netBuf = ByteBuffer.allocate(netBufSize);
        SSLEngineResult res = engine.wrap(appBuf, netBuf);
        netBuf.flip();
        while (netBuf.hasRemaining()) {
            channel.write(netBuf);
        }
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
        private final SocketChannel channel;
        private final MqttCodec codec;
        private final MqttVersion version;
        private final SSLEngine sslEngine;
        private final String username;
        private volatile MqttSession session;
        private volatile boolean cleanDisconnect = false;
        private volatile WillMessage willMessage;
        private volatile int keepAlive;
        private volatile long lastActivityTime;

        ClientConnection(String clientId, SocketChannel channel,
                         MqttCodec codec, MqttVersion version, SSLEngine sslEngine, String username) {
            this.clientId = clientId;
            this.channel = channel;
            this.codec = codec;
            this.version = version;
            this.sslEngine = sslEngine;
            this.username = username;
            this.lastActivityTime = System.currentTimeMillis();
        }

        String clientId() { return clientId; }
        SocketChannel channel() { return channel; }
        MqttCodec codec() { return codec; }
        MqttVersion version() { return version; }
        SSLEngine sslEngine() { return sslEngine; }
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
        boolean isOpen() { return channel.isOpen(); }

        @Override
        public void close() {
            try {
                if (channel.isOpen()) channel.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
