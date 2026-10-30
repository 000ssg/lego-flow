package ssg.legoflow.messaging.mqtt.bridge;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import ssg.legoflow.messaging.mqtt.transport.MqttTransport;
import ssg.legoflow.messaging.mqtt.topic.TopicFilter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-memory MQTT bridge that connects two {@link MqttBroker} instances using
 * {@link InMemoryMqttTransport} pairs.
 *
 * <p>This is a reference implementation suitable for testing and local
 * multi-broker scenarios. For production use, a TCP-based bridge implementation
 * would connect to remote brokers over the network.</p>
 *
 * <p>The bridge maintains two clients:
 * <ul>
 *   <li>A client connected to the local broker (receives local publishes)</li>
 *   <li>A client connected to the remote broker (receives remote publishes)</li>
 * </ul>
 * When a bridged topic is published on one side, the bridge forwards it to the
 * other side.</p>
 *
 * @since 0.2.0
 */
public final class InMemoryMqttBridge implements MqttBridge {

    private static final Logger LOG = Logger.getLogger(InMemoryMqttBridge.class.getName());

    private final MqttBroker localBroker;
    private final MqttBroker remoteBroker;
    private final String bridgeId;
    private final Map<String, String> localToRemote = new ConcurrentHashMap<>();
    private final Map<String, String> remoteToLocal = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private MqttClient localClient;
    private MqttClient remoteClient;
    private Thread localForwardThread;
    private Thread remoteForwardThread;

    /**
     * Creates a new in-memory bridge between two brokers.
     *
     * @param localBroker the local broker
     * @param remoteBroker the remote broker
     * @param bridgeId the unique bridge identifier
     */
    public InMemoryMqttBridge(MqttBroker localBroker, MqttBroker remoteBroker, String bridgeId) {
        this.localBroker = localBroker;
        this.remoteBroker = remoteBroker;
        this.bridgeId = bridgeId;
    }

    @Override
    public void start() throws Exception {
        if (running.getAndSet(true)) {
            return;
        }

        // Create client for local broker
        var localTransports = InMemoryMqttTransport.createPair();
        localBroker.handleConnection(localTransports[0]);
        localClient = createClient(localTransports[1], bridgeId + "-local");

        // Create client for remote broker
        var remoteTransports = InMemoryMqttTransport.createPair();
        remoteBroker.handleConnection(remoteTransports[0]);
        remoteClient = createClient(remoteTransports[1], bridgeId + "-remote");

        // Connect both clients
        localClient.connect().get(10, java.util.concurrent.TimeUnit.SECONDS);
        remoteClient.connect().get(10, java.util.concurrent.TimeUnit.SECONDS);

        // Start forwarding threads
        startForwardingThreads();
        LOG.log(Level.INFO, () -> "Bridge " + bridgeId + " started");
    }

    @Override
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        try {
            if (localClient != null) localClient.disconnect();
            if (remoteClient != null) remoteClient.disconnect();
            LOG.log(Level.INFO, () -> "Bridge " + bridgeId + " stopped");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error stopping bridge " + bridgeId, e);
        }
    }

    @Override
    public void bridgeTopic(String localFilter, String remoteFilter, QoS qos) {
        localToRemote.put(localFilter, remoteFilter);
        remoteToLocal.put(remoteFilter, localFilter);

        // Subscribe local client to local filter
        localClient.subscribe(localFilter, qos, (topic, payload, q, r) -> {
            if (running.get() && remoteClient != null && remoteClient.isConnected()) {
                try {
                    remoteClient.publish(remoteFilter, payload, q, false);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to forward " + topic + " to remote", e);
                }
            }
        });

        // Subscribe remote client to remote filter
        remoteClient.subscribe(remoteFilter, qos, (topic, payload, q, r) -> {
            if (running.get() && localClient != null && localClient.isConnected()) {
                try {
                    localClient.publish(localFilter, payload, q, false);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to forward " + topic + " to local", e);
                }
            }
        });
    }

    @Override
    public void unbridgeTopic(String localFilter) {
        String remoteFilter = localToRemote.remove(localFilter);
        if (remoteFilter != null) {
            remoteToLocal.remove(remoteFilter);
            // Note: MQTT doesn't support un-subscribe with filters, so we skip
            // In a real bridge, you'd track subscription IDs and unsubscribe
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        stop();
    }

    private MqttClient createClient(MqttTransport transport, String clientId) {
        var config = MqttClientConfig.defaults()
                .host("localhost")
                .port(1883)
                .clientId(clientId)
                .cleanSession(false)
                .build();
        return new MqttClient(config, transport);
    }

    private void startForwardingThreads() {
        // The message forwarding is handled by the subscribe callbacks above,
        // but we need to keep the clients' internal threads alive
        localForwardThread = Thread.ofVirtual().name("bridge-local-" + bridgeId).start(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        remoteForwardThread = Thread.ofVirtual().name("bridge-remote-" + bridgeId).start(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
}
