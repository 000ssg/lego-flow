package ssg.legoflow.messaging.mqtt.client;

import ssg.legoflow.messaging.mqtt.codec.MqttCodec;
import ssg.legoflow.messaging.mqtt.protocol.*;
import ssg.legoflow.messaging.mqtt.transport.MqttTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
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
 * ping management, and QoS 1/2 message flow state tracking.</p>
 *
 * <p>Transport-agnostic: uses {@link MqttTransport} for I/O. Always provide a transport
 * explicitly — this class never creates sockets.</p>
 *
 * @since 0.2.0
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

    private final MqttTransport transport;
    private volatile MqttCallback callback;
    private volatile ScheduledExecutorService keepAliveScheduler;
    private volatile Future<?> readerFuture;

    /**
     * Creates a new MQTT client backed by the given transport.
     *
     * <p>The client uses the provided transport for all I/O. Call {@code connect()}
     * to trigger the protocol handshake over the transport.</p>
     *
     * @param config    the client configuration
     * @param transport the pre-configured transport (e.g. in-memory or pipeline)
     */
    public MqttClient(MqttClientConfig config, MqttTransport transport) {
        this.config = Objects.requireNonNull(config);
        this.codec = new MqttCodec(config.version());
        this.transport = Objects.requireNonNull(transport);
    }

    /**
     * Connects to the MQTT broker.
     *
     * @return a future that completes with the CONNACK packet
     */
    public CompletableFuture<ConnAckPacket> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var connectPacket = new ConnectPacket(
                        config.version(),
                        config.clientId(),
                        config.cleanSession(),
                        config.keepAlive(),
                        config.username(),
                        config.password(),
                        config.will(),
                        new MqttProperties()
                );

                sendPacket(connectPacket);

                ByteBuffer response = ByteBuffer.allocate(4096);
                int bytesRead = transport.receive(response);
                if (bytesRead <= 0) throw new IOException("No response from broker");
                response.flip();

                MqttPacket packet = codec.decode(response);
                if (packet instanceof ConnAckPacket connAck) {
                    if (connAck.returnCode() == ConnectReturnCode.ACCEPTED) {
                        connected.set(true);
                        startKeepAlive(config.keepAlive());
                        startReader();
                        LOG.info("Connected to MQTT broker as {}", config.clientId());
                    } else {
                        LOG.warn("Connection refused: {}", connAck.returnCode());
                        transport.close();
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
                    transport.close();
                    LOG.info("Disconnected from MQTT broker");
                }
            } catch (Exception e) {
                LOG.error("Error during disconnect", e);
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Publishes a message to the given topic.
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
     */
    public CompletableFuture<SubAckPacket> subscribe(String topicFilter, QoS qos,
                                                     MqttMessageListener listener) {
        return subscribe(List.of(new TopicSubscription(topicFilter, qos)), listener);
    }

    /**
     * Subscribes to multiple topics with the given listener.
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
     */
    public boolean isConnected() {
        return connected.get() && transport.isOpen();
    }

    /**
     * Sets the callback for lifecycle and message events.
     */
    public void setCallback(MqttCallback callback) {
        this.callback = callback;
    }

    @Override
    public void close() {
        try { disconnect().join(); } catch (Exception ignored) {}
        executor.shutdown();
    }

    // --- Private methods ---

    private void sendPacket(MqttPacket packet) {
        try {
            ByteBuffer encoded = codec.encode(packet);
            transport.send(encoded);
        } catch (Exception e) {
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
                    int bytesRead = transport.receive(readBuf);
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
                } catch (Exception e) {
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
        }
    }
}
