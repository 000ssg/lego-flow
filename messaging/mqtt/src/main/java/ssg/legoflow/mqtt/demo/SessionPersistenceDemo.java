package ssg.legoflow.mqtt.demo;

import ssg.legoflow.mqtt.broker.MqttBroker;
import ssg.legoflow.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.mqtt.client.MqttClient;
import ssg.legoflow.mqtt.client.MqttClientConfig;
import ssg.legoflow.mqtt.protocol.MqttVersion;
import ssg.legoflow.mqtt.protocol.QoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Demo demonstrating clean session vs persistent session: offline message queuing.
 *
 * @since 1.0.0
 */
public final class SessionPersistenceDemo {

    private static final Logger LOG = LoggerFactory.getLogger(SessionPersistenceDemo.class);

    private final List<String> receivedAfterReconnect = new CopyOnWriteArrayList<>();
    private MqttBroker broker;

    /**
     * Runs the session persistence demo.
     *
     * @param port the broker port (0 for ephemeral)
     * @throws Exception on error
     */
    public void run(int port) throws Exception {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.bind("localhost", port);
        int actualPort = broker.getPort();

        // Step 1: Connect with persistent session, subscribe, then disconnect
        var config = MqttClientConfig.defaults()
                .host("localhost").port(actualPort)
                .clientId("persistent-client")
                .cleanSession(false)
                .build();

        try (var sub = new MqttClient(config)) {
            sub.connect().get();
            sub.subscribe("events/important", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                receivedAfterReconnect.add(new String(p, StandardCharsets.UTF_8));
                LOG.info("Received after reconnect: {}", new String(p, StandardCharsets.UTF_8));
            }).get();
            sub.disconnect().get();
        }

        Thread.sleep(200);

        // Step 2: Publish while the persistent client is offline
        try (var pub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(actualPort).clientId("publisher").build())) {
            pub.connect().get();
            pub.publish("events/important", "offline-msg-1".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get();
            pub.publish("events/important", "offline-msg-2".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get();
        }

        Thread.sleep(200);

        // Step 3: Reconnect persistent client — should receive queued messages
        try (var sub2 = new MqttClient(config)) {
            sub2.connect().get();
            sub2.subscribe("events/important", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                receivedAfterReconnect.add(new String(p, StandardCharsets.UTF_8));
            }).get();
            Thread.sleep(500);
        }
    }

    /** Returns messages received after reconnect. */
    public List<String> getReceivedAfterReconnect() { return receivedAfterReconnect; }

    /** Stops the broker. */
    public void stop() { if (broker != null) broker.stop(); }
}
