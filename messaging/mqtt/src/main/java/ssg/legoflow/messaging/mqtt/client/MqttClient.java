package ssg.legoflow.messaging.mqtt.client;

import ssg.legoflow.messaging.mqtt.codec.MqttCodec;
import ssg.legoflow.messaging.mqtt.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * MQTT client supporting v3.1.1 and v5.0 protocol versions.
 *
 * <p>Provides asynchronous connect, disconnect, publish, subscribe, and unsubscribe
 * operations. Supports automatic reconnect with configurable backoff, keep-alive
 * ping management, and QoS 1/2 message flow state tracking.
 *
 * @since 0.1.0
 */
public final class MqttClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MqttClient.class);

    private final MqttClientConfig config;
    private final MqttCodec codec;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger packetIdGenerator = new AtomicInteger(1);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Map<Integer, CompletableFuture<?>> pendingAcks = new ConcurrentHashMap<>();
    private final Map<String, MqttMessageListener> topicListeners = new ConcurrentHashMap<>();
    private final Map<Integer, PublishPacket> inflightMessages = new ConcurrentHashMap<>();

    private volatile SocketChannel channel;
    private volatile SSLEngine sslEngine;
    private volatile MqttCallback callback;
    private volatile ScheduledExecutorService keepAliveScheduler;
    private volatile Future<?> readerFuture;

    /**
     * Creates a new MQTT client with the given configuration.
     *
     * @param config the client configuration
     */
    public MqttClient(MqttClientConfig config) {
        this.config = Objects.requireNonNull(config);
        this.codec = new MqttCodec(config.version());
    }

    /**
     * Connects to the MQTT broker.
     *
     * @return a future that completes with the CONNACK packet
     */
    public CompletableFuture<ConnAckPacket> connect() {
        return connect(config);
    }

    /**
     * Connects to the MQTT broker using the given configuration.
     *
     * @param connectConfig the configuration for this connection attempt
     * @return a future that completes with the CONNACK packet
     */
    public CompletableFuture<ConnAckPacket> connect(MqttClientConfig connectConfig) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                channel = SocketChannel.open(
                        new InetSocketAddress(connectConfig.host(), connectConfig.port()));
                channel.configureBlocking(true);

                // TLS handshake if configured
                if (connectConfig.tlsConfig() != null) {
                    try {
                        SSLContext sslCtx = connectConfig.tlsConfig().createSslContext();
                        sslEngine = connectConfig.tlsConfig().createClientEngine(
                                sslCtx, connectConfig.host(), connectConfig.port());
                        performTlsHandshake(channel, sslEngine);
                    } catch (java.security.GeneralSecurityException e) {
                        throw new IOException("Failed to initialize TLS", e);
                    }
                }

                var connectPacket = new ConnectPacket(
                        connectConfig.version(),
                        connectConfig.clientId(),
                        connectConfig.cleanSession(),
                        connectConfig.keepAlive(),
                        connectConfig.username(),
                        connectConfig.password(),
                        connectConfig.will(),
                        new MqttProperties()
                );

                sendPacket(connectPacket);

                ByteBuffer response = ByteBuffer.allocate(4096);
                if (sslEngine != null) {
                    readTls(channel, sslEngine, response);
                } else {
                    channel.read(response);
                }
                response.flip();

                MqttPacket packet = codec.decode(response);
                if (packet instanceof ConnAckPacket connAck) {
                    if (connAck.returnCode() == ConnectReturnCode.ACCEPTED) {
                        connected.set(true);
                        startKeepAlive(connectConfig.keepAlive());
                        startReader();
                        LOG.info("Connected to MQTT broker {}:{} as {}",
                                connectConfig.host(), connectConfig.port(), connectConfig.clientId());
                    } else {
                        LOG.warn("Connection refused: {}", connAck.returnCode());
                        channel.close();
                    }
                    return connAck;
                }
                throw new IOException("Unexpected packet type: " + packet.type());
            } catch (IOException e) {
                LOG.error("Failed to connect to broker", e);
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Disconnects from the MQTT broker.
     *
     * @return a future that completes when disconnected
     */
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (connected.compareAndSet(true, false)) {
                    sendPacket(new DisconnectPacket(ReasonCode.NORMAL_DISCONNECTION, new MqttProperties()));
                    stopKeepAlive();
                    if (readerFuture != null) {
                        readerFuture.cancel(true);
                    }
                    if (channel != null && channel.isOpen()) {
                        channel.close();
                    }
                    LOG.info("Disconnected from MQTT broker");
                }
            } catch (IOException e) {
                LOG.error("Error during disconnect", e);
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Publishes a message to the given topic.
     *
     * @param topic   the topic to publish to
     * @param payload the message payload
     * @param qos     the QoS level
     * @param retain  whether the message should be retained
     * @return a future that completes when the publish flow is done
     */
    public CompletableFuture<Void> publish(String topic, byte[] payload, QoS qos, boolean retain) {
        int packetId = (qos == QoS.AT_MOST_ONCE) ? 0 : nextPacketId();
        var publishPacket = new PublishPacket(topic, payload, qos, retain, false,
                packetId, new MqttProperties());

        if (qos == QoS.AT_MOST_ONCE) {
            return CompletableFuture.runAsync(() -> sendPacket(publishPacket), executor);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingAcks.put(packetId, future);
        inflightMessages.put(packetId, publishPacket);
        CompletableFuture.runAsync(() -> sendPacket(publishPacket), executor);
        return future;
    }

    /**
     * Subscribes to a topic with the given QoS and listener.
     *
     * @param topicFilter the topic filter
     * @param qos         the maximum QoS level
     * @param listener    the message listener
     * @return a future that completes with the SUBACK packet
     */
    public CompletableFuture<SubAckPacket> subscribe(String topicFilter, QoS qos,
                                                     MqttMessageListener listener) {
        return subscribe(List.of(new TopicSubscription(topicFilter, qos)), listener);
    }

    /**
     * Subscribes to multiple topics with the given listener.
     *
     * @param subscriptions the list of topic subscriptions
     * @param listener      the message listener
     * @return a future that completes with the SUBACK packet
     */
    public CompletableFuture<SubAckPacket> subscribe(List<TopicSubscription> subscriptions,
                                                     MqttMessageListener listener) {
        int packetId = nextPacketId();
        for (var sub : subscriptions) {
            topicListeners.put(sub.topicFilter(), listener);
        }
        var subscribePacket = new SubscribePacket(packetId, subscriptions, new MqttProperties());

        CompletableFuture<SubAckPacket> future = new CompletableFuture<>();
        pendingAcks.put(packetId, future);
        CompletableFuture.runAsync(() -> sendPacket(subscribePacket), executor);
        return future;
    }

    /**
     * Unsubscribes from the given topic filters.
     *
     * @param topicFilters the topic filters to unsubscribe from
     * @return a future that completes when unsubscribed
     */
    public CompletableFuture<Void> unsubscribe(String... topicFilters) {
        int packetId = nextPacketId();
        for (var filter : topicFilters) {
            topicListeners.remove(filter);
        }
        var unsubPacket = new UnsubscribePacket(packetId, List.of(topicFilters), new MqttProperties());

        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingAcks.put(packetId, future);
        CompletableFuture.runAsync(() -> sendPacket(unsubPacket), executor);
        return future;
    }

    /**
     * Returns whether the client is currently connected.
     *
     * @return {@code true} if connected
     */
    public boolean isConnected() {
        return connected.get() && channel != null && channel.isOpen();
    }

    /**
     * Sets the callback for lifecycle and message events.
     *
     * @param callback the callback
     */
    public void setCallback(MqttCallback callback) {
        this.callback = callback;
    }

    @Override
    public void close() {
        disconnect().join();
        executor.shutdown();
    }

    // --- Private methods ---

    private void sendPacket(MqttPacket packet) {
        try {
            ByteBuffer encoded = codec.encode(packet);
            if (sslEngine != null) {
                writeTls(channel, sslEngine, encoded);
            } else {
                while (encoded.hasRemaining()) {
                    channel.write(encoded);
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to send packet: {}", packet.type(), e);
            handleConnectionLost(e);
        }
    }

    private int nextPacketId() {
        return packetIdGenerator.getAndUpdate(id -> id >= 65535 ? 1 : id + 1);
    }

    private void startKeepAlive(int keepAliveSeconds) {
        if (keepAliveSeconds <= 0) return;
        keepAliveScheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("mqtt-keepalive-", 0).factory());
        keepAliveScheduler.scheduleAtFixedRate(() -> {
            if (connected.get()) {
                sendPacket(new PingReqPacket());
            }
        }, keepAliveSeconds, keepAliveSeconds, TimeUnit.SECONDS);
    }

    private void stopKeepAlive() {
        if (keepAliveScheduler != null) {
            keepAliveScheduler.shutdownNow();
            keepAliveScheduler = null;
        }
    }

    private void startReader() {
        readerFuture = executor.submit(() -> {
            ByteBuffer readBuf = ByteBuffer.allocate(65536);
            while (connected.get()) {
                try {
                    readBuf.clear();
                    int bytesRead;
                    if (sslEngine != null) {
                        bytesRead = readTls(channel, sslEngine, readBuf);
                    } else {
                        bytesRead = channel.read(readBuf);
                    }
                    if (bytesRead == -1) {
                        handleConnectionLost(new IOException("Connection closed by broker"));
                        break;
                    }
                    if (bytesRead > 0) {
                        readBuf.flip();
                        List<MqttPacket> packets = codec.decodeAll(readBuf);
                        for (var packet : packets) {
                            handleIncomingPacket(packet);
                        }
                    }
                } catch (IOException e) {
                    if (connected.get()) {
                        handleConnectionLost(e);
                    }
                    break;
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleIncomingPacket(MqttPacket packet) {
        switch (packet) {
            case PublishPacket pub -> {
                deliverMessage(pub);
                if (pub.qos() == QoS.AT_LEAST_ONCE) {
                    sendPacket(new PubAckPacket(pub.packetId(), ReasonCode.SUCCESS, new MqttProperties()));
                } else if (pub.qos() == QoS.EXACTLY_ONCE) {
                    sendPacket(new PubRecPacket(pub.packetId(), ReasonCode.SUCCESS, new MqttProperties()));
                }
            }
            case PubAckPacket ack -> {
                inflightMessages.remove(ack.packetId());
                var future = (CompletableFuture<Void>) pendingAcks.remove(ack.packetId());
                if (future != null) future.complete(null);
                if (callback != null) callback.onDeliveryComplete(ack.packetId());
            }
            case PubRecPacket rec -> {
                sendPacket(new PubRelPacket(rec.packetId(), ReasonCode.SUCCESS, new MqttProperties()));
            }
            case PubRelPacket rel -> {
                sendPacket(new PubCompPacket(rel.packetId(), ReasonCode.SUCCESS, new MqttProperties()));
            }
            case PubCompPacket comp -> {
                inflightMessages.remove(comp.packetId());
                var future = (CompletableFuture<Void>) pendingAcks.remove(comp.packetId());
                if (future != null) future.complete(null);
                if (callback != null) callback.onDeliveryComplete(comp.packetId());
            }
            case SubAckPacket subAck -> {
                var future = (CompletableFuture<SubAckPacket>) pendingAcks.remove(subAck.packetId());
                if (future != null) future.complete(subAck);
            }
            case UnsubAckPacket unsubAck -> {
                var future = (CompletableFuture<Void>) pendingAcks.remove(unsubAck.packetId());
                if (future != null) future.complete(null);
            }
            case PingRespPacket ignored -> LOG.trace("Received PINGRESP");
            case ConnAckPacket ignored -> LOG.trace("Received unexpected CONNACK");
            default -> LOG.debug("Unhandled packet type: {}", packet.type());
        }
    }

    private void deliverMessage(PublishPacket pub) {
        if (callback != null) {
            callback.onMessage(pub.topic(), pub);
        }
        for (var entry : topicListeners.entrySet()) {
            var tf = new ssg.legoflow.messaging.mqtt.topic.TopicFilter(entry.getKey());
            if (tf.matches(pub.topic())) {
                entry.getValue().onMessage(pub.topic(), pub.payload(), pub.qos(), pub.retain());
            }
        }
    }

    private void handleConnectionLost(Throwable cause) {
        if (connected.compareAndSet(true, false)) {
            LOG.warn("Connection lost: {}", cause.getMessage());
            stopKeepAlive();
            if (callback != null) {
                callback.onConnectionLost(cause);
            }
            if (config.autoReconnect()) {
                scheduleReconnect();
            }
        }
    }

    private void scheduleReconnect() {
        executor.submit(() -> {
            while (!connected.get()) {
                try {
                    Thread.sleep(config.reconnectDelay().toMillis());
                    LOG.info("Attempting reconnect to {}:{}", config.host(), config.port());
                    connect(config).get(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
                    if (connected.get() && callback != null) {
                        callback.onReconnected();
                    }
                } catch (Exception e) {
                    LOG.debug("Reconnect attempt failed: {}", e.getMessage());
                }
            }
        });
    }

    // --- TLS support ---

    private void performTlsHandshake(SocketChannel ch, SSLEngine engine) throws IOException {
        try {
            engine.beginHandshake();
            SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();

            int netBufSize = engine.getSession().getPacketBufferSize();
            int appBufSize = engine.getSession().getApplicationBufferSize();
            ByteBuffer myNetData = ByteBuffer.allocate(netBufSize);
            ByteBuffer peerNetData = ByteBuffer.allocate(netBufSize);
            ByteBuffer myAppData = ByteBuffer.allocate(appBufSize);
            ByteBuffer peerAppData = ByteBuffer.allocate(appBufSize);

            while (hs != SSLEngineResult.HandshakeStatus.FINISHED
                    && hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                switch (hs) {
                    case NEED_UNWRAP -> {
                        if (ch.read(peerNetData) < 0) {
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
                            ch.write(myNetData);
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
        } catch (Exception e) {
            throw new IOException("TLS handshake failed", e);
        }
    }

    private int readTls(SocketChannel ch, SSLEngine engine, ByteBuffer appBuf) throws IOException {
        int netBufSize = engine.getSession().getPacketBufferSize();
        ByteBuffer netBuf = ByteBuffer.allocate(netBufSize);
        int bytesRead = ch.read(netBuf);
        if (bytesRead <= 0) return bytesRead;
        netBuf.flip();
        SSLEngineResult res = engine.unwrap(netBuf, appBuf);
        return res.bytesProduced();
    }

    private void writeTls(SocketChannel ch, SSLEngine engine, ByteBuffer appBuf) throws IOException {
        int netBufSize = engine.getSession().getPacketBufferSize();
        ByteBuffer netBuf = ByteBuffer.allocate(netBufSize);
        engine.wrap(appBuf, netBuf);
        netBuf.flip();
        while (netBuf.hasRemaining()) {
            ch.write(netBuf);
        }
    }
}
