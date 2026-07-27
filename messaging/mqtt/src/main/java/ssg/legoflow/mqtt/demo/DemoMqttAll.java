package ssg.legoflow.mqtt.demo;

import ssg.legoflow.mqtt.broker.MqttBroker;
import ssg.legoflow.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.mqtt.client.MqttClient;
import ssg.legoflow.mqtt.client.MqttClientConfig;
import ssg.legoflow.mqtt.codec.MqttCodec;
import ssg.legoflow.mqtt.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive demo of all MQTT module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link MqttBroker}</b> -- No external dependencies.
 * Runs anywhere without installation. Supports MQTT v3.1.1 and v5.0, all 15 control packet
 * types, QoS 0/1/2 delivery flows, wildcard topic matching, retained messages, persistent
 * sessions, last will and testament, and keep-alive monitoring.
 * Ideal for development, testing, CI/CD, and learning the MQTT protocol.</p>
 *
 * <p><b>Alternative: External Mosquitto / HiveMQ / EMQX</b> -- Set {@link #USE_EXTERNAL}{@code =true}
 * and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}. Required for:</p>
 * <ul>
 *   <li>Production load testing with thousands of concurrent connections</li>
 *   <li>Clustering and high availability (multi-node broker mesh)</li>
 *   <li>Advanced authentication plugins (LDAP, OAuth, JWT)</li>
 *   <li>Integration testing against a specific broker implementation</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the broker lifecycle (bind/stop).
 * All client code (connect, publish, subscribe) uses the same API regardless of backend.
 * When {@code USE_EXTERNAL=true}, the demo skips broker creation and connects directly
 * to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Pub/sub -- QoS 0 (fire-and-forget), QoS 1 (at-least-once), QoS 2 (exactly-once)</li>
 *   <li>Wildcard topics -- single-level (+) and multi-level (#) matching</li>
 *   <li>Retained messages -- late subscribers receive the last retained value</li>
 *   <li>Session persistence -- clean vs persistent sessions with subscription restore</li>
 *   <li>Last will and testament -- broker publishes will on ungraceful disconnect</li>
 *   <li>Keep-alive -- PINGREQ/PINGRESP heartbeat mechanism</li>
 *   <li>Topic tree -- hierarchical topic structure with wildcard routing</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoMqttAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoMqttAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house MqttBroker (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for Mosquitto/HiveMQ/EMQX
    // =========================================================================

    /** Set to {@code true} to connect to an external MQTT broker (Mosquitto, HiveMQ, EMQX). */
    public static boolean USE_EXTERNAL = false;

    /** Host for external MQTT broker. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external MQTT broker. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 1883;

    private DemoMqttAll() {}

    /**
     * Results from running the full demo.
     *
     * @param pubSubQoS0       true if QoS 0 message was delivered
     * @param pubSubQoS1       true if QoS 1 message was delivered
     * @param pubSubQoS2       true if QoS 2 message was delivered
     * @param wildcardSingle   number of topics matched by single-level wildcard (+)
     * @param wildcardMulti    number of topics matched by multi-level wildcard (#)
     * @param retainedReceived true if late subscriber received the retained message
     * @param sessionPersist   number of messages received via restored subscription after reconnect
     * @param willDelivered    true if last will message was delivered on ungraceful disconnect
     * @param keepAliveOk      true if keep-alive configuration was accepted
     * @param topicTreeMatches number of distinct topics routed through the topic tree
     */
    public record Results(
            boolean pubSubQoS0,
            boolean pubSubQoS1,
            boolean pubSubQoS2,
            int wildcardSingle,
            int wildcardMulti,
            boolean retainedReceived,
            int sessionPersist,
            boolean willDelivered,
            boolean keepAliveOk,
            int topicTreeMatches
    ) {}

    /**
     * Runs the comprehensive demo covering all MQTT features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runWithBroker(EXTERNAL_HOST, EXTERNAL_PORT, null);
        }
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            LOG.info("In-house MqttBroker started on port {}", port);
            return runWithBroker("localhost", port, broker);
        }
    }

    private static Results runWithBroker(String host, int port, MqttBroker broker) throws Exception {
        boolean qos0 = demoPubSubQoS0(host, port);
        boolean qos1 = demoPubSubQoS1(host, port);
        boolean qos2 = demoPubSubQoS2(host, port);
        int wcSingle = demoWildcardSingleLevel(host, port);
        int wcMulti = demoWildcardMultiLevel(host, port);
        boolean retained = demoRetainedMessages(host, port);
        int sessionPersist = demoSessionPersistence(host, port);
        boolean will = demoLastWillAndTestament(host, port);
        boolean keepAlive = demoKeepAlive(host, port);
        int topicTree = demoTopicTree(host, port);

        return new Results(qos0, qos1, qos2, wcSingle, wcMulti, retained,
                sessionPersist, will, keepAlive, topicTree);
    }

    // ======================== 1. PUB/SUB QoS 0 ==============================

    /**
     * Demonstrates QoS 0 (fire and forget) publish/subscribe.
     * No acknowledgement -- message delivered at most once.
     */
    static boolean demoPubSubQoS0(String host, int port) throws Exception {
        LOG.info("=== 1. Pub/Sub QoS 0 (fire and forget) ===");
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = client(host, port, "qos0-sub");
             var pub = client(host, port, "qos0-pub")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("demo/qos0", QoS.AT_MOST_ONCE, (t, payload, q, r) -> {
                received.add(new String(payload, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            pub.publish("demo/qos0", "fire-and-forget".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_MOST_ONCE, false).get(5, TimeUnit.SECONDS);

            latch.await(5, TimeUnit.SECONDS);
        }
        LOG.info("QoS 0 received: {}", received.size());
        return !received.isEmpty();
    }

    // ======================== 2. PUB/SUB QoS 1 ==============================

    /**
     * Demonstrates QoS 1 (at least once) with PUBLISH/PUBACK flow.
     */
    static boolean demoPubSubQoS1(String host, int port) throws Exception {
        LOG.info("=== 2. Pub/Sub QoS 1 (at least once) ===");
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = client(host, port, "qos1-sub");
             var pub = client(host, port, "qos1-pub")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("demo/qos1", QoS.AT_LEAST_ONCE, (t, payload, q, r) -> {
                received.add(new String(payload, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            pub.publish("demo/qos1", "at-least-once".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);

            latch.await(5, TimeUnit.SECONDS);
        }
        LOG.info("QoS 1 received: {}", received.size());
        return !received.isEmpty();
    }

    // ======================== 3. PUB/SUB QoS 2 ==============================

    /**
     * Demonstrates QoS 2 (exactly once) with PUBLISH/PUBREC/PUBREL/PUBCOMP flow.
     */
    static boolean demoPubSubQoS2(String host, int port) throws Exception {
        LOG.info("=== 3. Pub/Sub QoS 2 (exactly once) ===");
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = client(host, port, "qos2-sub");
             var pub = client(host, port, "qos2-pub")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("demo/qos2", QoS.EXACTLY_ONCE, (t, payload, q, r) -> {
                received.add(new String(payload, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            pub.publish("demo/qos2", "exactly-once".getBytes(StandardCharsets.UTF_8),
                    QoS.EXACTLY_ONCE, false).get(5, TimeUnit.SECONDS);

            latch.await(5, TimeUnit.SECONDS);
        }
        LOG.info("QoS 2 received: {}", received.size());
        return !received.isEmpty();
    }

    // ======================== 4. WILDCARD TOPICS (+) ========================

    /**
     * Demonstrates single-level wildcard (+): matches exactly one topic level.
     * {@code sensors/+/temperature} matches {@code sensors/room1/temperature}
     * but not {@code sensors/room1/sub/temperature}.
     */
    static int demoWildcardSingleLevel(String host, int port) throws Exception {
        LOG.info("=== 4. Wildcard Topics (+) ===");
        var matched = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(2);

        try (var sub = client(host, port, "wc-single-sub");
             var pub = client(host, port, "wc-single-pub")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            // Subscribe with single-level wildcard
            sub.subscribe("sensors/+/temperature", QoS.AT_LEAST_ONCE, (t, payload, q, r) -> {
                matched.add(t);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // These should match
            pub.publish("sensors/room1/temperature", "22.5".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            pub.publish("sensors/room2/temperature", "23.0".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            // This should NOT match (wrong leaf level)
            pub.publish("sensors/room1/humidity", "45".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);

            latch.await(5, TimeUnit.SECONDS);
        }
        LOG.info("Single-level wildcard matched {} topics", matched.size());
        return matched.size();
    }

    // ======================== 5. WILDCARD TOPICS (#) ========================

    /**
     * Demonstrates multi-level wildcard (#): matches zero or more topic levels.
     * {@code home/#} matches {@code home}, {@code home/kitchen},
     * and {@code home/kitchen/temperature}.
     */
    static int demoWildcardMultiLevel(String host, int port) throws Exception {
        LOG.info("=== 5. Wildcard Topics (#) ===");
        var matched = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(3);

        try (var sub = client(host, port, "wc-multi-sub");
             var pub = client(host, port, "wc-multi-pub")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            // Subscribe with multi-level wildcard
            sub.subscribe("home/#", QoS.AT_LEAST_ONCE, (t, payload, q, r) -> {
                matched.add(t);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // All of these should match
            pub.publish("home/kitchen/temperature", "22".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            pub.publish("home/bedroom/light", "on".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            pub.publish("home/garden", "watered".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);

            latch.await(5, TimeUnit.SECONDS);
        }
        LOG.info("Multi-level wildcard matched {} topics", matched.size());
        return matched.size();
    }

    // ======================== 6. RETAINED MESSAGES ==========================

    /**
     * Demonstrates retained messages: a publisher publishes with retain=true,
     * then a late subscriber connects and immediately receives the last retained value.
     */
    static boolean demoRetainedMessages(String host, int port) throws Exception {
        LOG.info("=== 6. Retained Messages ===");
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        // Step 1: publish retained message
        try (var pub = client(host, port, "retain-pub")) {
            pub.connect().get(5, TimeUnit.SECONDS);
            pub.publish("status/server1", "healthy".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, true).get(5, TimeUnit.SECONDS);
        }

        Thread.sleep(200);

        // Step 2: late subscriber should receive the retained message
        try (var sub = client(host, port, "retain-sub")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            sub.subscribe("status/server1", QoS.AT_LEAST_ONCE, (t, payload, q, r) -> {
                received.add(new String(payload, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            latch.await(5, TimeUnit.SECONDS);
        }
        LOG.info("Retained message received: {}", received);
        return received.contains("healthy");
    }

    // ======================== 7. SESSION PERSISTENCE ========================

    /**
     * Demonstrates persistent session: subscriber connects with cleanSession=false,
     * subscribes, disconnects, then reconnects. The broker preserves the session and
     * subscriptions -- on reconnect, the session-present flag is true and the restored
     * subscription can immediately receive new messages without re-subscribing.
     */
    static int demoSessionPersistence(String host, int port) throws Exception {
        LOG.info("=== 7. Session Persistence ===");
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(2);

        var persistConfig = MqttClientConfig.defaults()
                .host(host).port(port)
                .clientId("persistent-demo-client")
                .cleanSession(false)
                .build();

        // Step 1: connect with persistent session, subscribe, then disconnect
        try (var sub = new MqttClient(persistConfig)) {
            sub.connect().get(5, TimeUnit.SECONDS);
            sub.subscribe("events/demo", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                    .get(5, TimeUnit.SECONDS);
            sub.disconnect().get(5, TimeUnit.SECONDS);
        }

        Thread.sleep(200);

        // Step 2: reconnect -- session-present flag confirms the broker preserved the session.
        // The broker restores subscriptions to the topic tree, so a callback registered
        // before connect will receive messages published after reconnect.
        try (var sub2 = new MqttClient(persistConfig)) {
            sub2.setCallback(new ssg.legoflow.mqtt.client.MqttCallback() {
                @Override public void onMessage(String topic, ssg.legoflow.mqtt.protocol.PublishPacket message) {
                    received.add(new String(message.payload(), StandardCharsets.UTF_8));
                    latch.countDown();
                }
                @Override public void onConnectionLost(Throwable cause) {}
                @Override public void onReconnected() {}
                @Override public void onDeliveryComplete(int packetId) {}
            });
            var ack = sub2.connect().get(5, TimeUnit.SECONDS);
            boolean sessionPresent = ack.sessionPresent();
            LOG.info("Reconnect: sessionPresent={}", sessionPresent);

            Thread.sleep(200);

            // Step 3: publish messages -- the restored subscription should deliver them
            try (var pub = client(host, port, "persist-pub")) {
                pub.connect().get(5, TimeUnit.SECONDS);
                pub.publish("events/demo", "after-reconnect-1".getBytes(StandardCharsets.UTF_8),
                        QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                pub.publish("events/demo", "after-reconnect-2".getBytes(StandardCharsets.UTF_8),
                        QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            }

            latch.await(5, TimeUnit.SECONDS);

            // sessionPresent must be true and messages must be received via restored subscription
            if (!sessionPresent) return 0;
        }

        LOG.info("Session persistence: received {} messages via restored subscription", received.size());
        return received.size();
    }

    // ======================== 8. LAST WILL AND TESTAMENT ====================

    /**
     * Demonstrates last will and testament: a client configures a will message,
     * then disconnects ungracefully (close without DISCONNECT). The broker
     * publishes the will message on behalf of the disconnected client.
     */
    static boolean demoLastWillAndTestament(String host, int port) throws Exception {
        LOG.info("=== 8. Last Will and Testament ===");
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        // Subscriber listens for will messages
        try (var sub = client(host, port, "will-sub")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            sub.subscribe("clients/status", QoS.AT_LEAST_ONCE, (t, payload, q, r) -> {
                received.add(new String(payload, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            Thread.sleep(100);

            // Client with a will message -- use raw socket so we can close abruptly
            // without sending DISCONNECT (MqttClient.close() sends DISCONNECT, which
            // suppresses the will per MQTT spec).
            var codec = new MqttCodec(MqttVersion.V3_1_1);
            try (var ch = SocketChannel.open(new InetSocketAddress(host, port))) {
                ch.configureBlocking(true);
                var will = new WillMessage("clients/status",
                        "will-client disconnected".getBytes(StandardCharsets.UTF_8),
                        QoS.AT_LEAST_ONCE, false);
                var connect = new ConnectPacket(MqttVersion.V3_1_1, "will-client", true,
                        60, null, null, will, new MqttProperties());
                ch.write(codec.encode(connect));
                // Read CONNACK
                var buf = ByteBuffer.allocate(256);
                ch.read(buf);
                Thread.sleep(100);
            }
            // SocketChannel.close() closes the TCP connection without DISCONNECT,
            // triggering the broker to publish the will message.

            // Wait for broker to detect disconnect and publish will
            latch.await(5, TimeUnit.SECONDS);
        }

        LOG.info("Will delivered: {}", received);
        return received.contains("will-client disconnected");
    }

    // ======================== 9. KEEP-ALIVE =================================

    /**
     * Demonstrates keep-alive configuration. The client sets a keep-alive interval;
     * the broker expects PINGREQ within 1.5x that interval or disconnects the client.
     */
    static boolean demoKeepAlive(String host, int port) throws Exception {
        LOG.info("=== 9. Keep-Alive ===");
        var config = MqttClientConfig.defaults()
                .host(host).port(port)
                .clientId("keepalive-client")
                .keepAlive(30) // 30 seconds keep-alive interval
                .build();

        try (var client = new MqttClient(config)) {
            client.connect().get(5, TimeUnit.SECONDS);
            boolean connected = client.isConnected();
            LOG.info("Keep-alive client connected: {}, keepAlive={}", connected, config.keepAlive());
            return connected && config.keepAlive() == 30;
        }
    }

    // ======================== 10. TOPIC TREE ================================

    /**
     * Demonstrates hierarchical topic tree routing: multiple subscribers on
     * different levels of the topic hierarchy receive appropriate messages.
     */
    static int demoTopicTree(String host, int port) throws Exception {
        LOG.info("=== 10. Topic Tree ===");
        Map<String, List<String>> receivedByFilter = new ConcurrentHashMap<>();
        var latch = new CountDownLatch(5);

        try (var sub = client(host, port, "tree-sub");
             var pub = client(host, port, "tree-pub")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            // Subscribe at different levels of the topic tree
            String exactTopic = "building/floor1/room1/temperature";
            receivedByFilter.put(exactTopic, new CopyOnWriteArrayList<>());
            sub.subscribe(exactTopic, QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                receivedByFilter.get(exactTopic).add(t);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            String floorWildcard = "building/floor1/+/temperature";
            receivedByFilter.put(floorWildcard, new CopyOnWriteArrayList<>());
            sub.subscribe(floorWildcard, QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                receivedByFilter.get(floorWildcard).add(t);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            String buildingWildcard = "building/#";
            receivedByFilter.put(buildingWildcard, new CopyOnWriteArrayList<>());
            sub.subscribe(buildingWildcard, QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                receivedByFilter.get(buildingWildcard).add(t);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // Publish to topic matching all three subscriptions
            pub.publish("building/floor1/room1/temperature", "22.5".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            // Publish to topic matching floor wildcard and building wildcard
            pub.publish("building/floor1/room2/temperature", "23.0".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);

            latch.await(5, TimeUnit.SECONDS);
        }

        // Count total distinct topics received across all filters
        int totalMatches = receivedByFilter.values().stream().mapToInt(List::size).sum();
        LOG.info("Topic tree total matches: {} (exact={}, floor+={}, building#={})",
                totalMatches,
                receivedByFilter.get("building/floor1/room1/temperature").size(),
                receivedByFilter.get("building/floor1/+/temperature").size(),
                receivedByFilter.get("building/#").size());
        return totalMatches;
    }

    private static MqttClient client(String host, int port, String clientId) {
        return new MqttClient(MqttClientConfig.defaults()
                .host(host).port(port).clientId(clientId).build());
    }
}
