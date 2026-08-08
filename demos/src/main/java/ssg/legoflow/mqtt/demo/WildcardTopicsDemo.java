package ssg.legoflow.mqtt.demo;

import ssg.legoflow.mqtt.broker.MqttBroker;
import ssg.legoflow.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.mqtt.client.MqttClient;
import ssg.legoflow.mqtt.client.MqttClientConfig;
import ssg.legoflow.mqtt.protocol.QoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Demo demonstrating MQTT topic patterns with + and # wildcards.
 *
 * @since 0.1.0
 */
public final class WildcardTopicsDemo {

    private static final Logger LOG = LoggerFactory.getLogger(WildcardTopicsDemo.class);

    private final Map<String, List<String>> receivedByFilter = new ConcurrentHashMap<>();
    private MqttBroker broker;

    /**
     * Runs the wildcard topics demo.
     *
     * @param port the broker port (0 for ephemeral)
     * @throws Exception on error
     */
    public void run(int port) throws Exception {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.bind("localhost", port);
        int actualPort = broker.getPort();

        try (var pub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(actualPort).clientId("wc-pub").build());
             var sub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(actualPort).clientId("wc-sub").build())) {

            pub.connect().get();
            sub.connect().get();

            // Subscribe with single-level wildcard
            String singleLevel = "sensors/+/temperature";
            receivedByFilter.put(singleLevel, new CopyOnWriteArrayList<>());
            sub.subscribe(singleLevel, QoS.AT_LEAST_ONCE, (t, payload, q, r) -> {
                receivedByFilter.get(singleLevel).add(t);
                LOG.info("Single-level match: {}", t);
            }).get();

            // Subscribe with multi-level wildcard
            String multiLevel = "sensors/#";
            receivedByFilter.put(multiLevel, new CopyOnWriteArrayList<>());
            sub.subscribe(multiLevel, QoS.AT_LEAST_ONCE, (t, payload, q, r) -> {
                receivedByFilter.get(multiLevel).add(t);
                LOG.info("Multi-level match: {}", t);
            }).get();

            // Publish on various topics
            pub.publish("sensors/room1/temperature", "22.5".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get();
            pub.publish("sensors/room2/temperature", "23.0".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get();
            pub.publish("sensors/room1/humidity", "45".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get();

            Thread.sleep(500);
        }
    }

    /** Returns received topics keyed by subscription filter. */
    public Map<String, List<String>> getReceivedByFilter() { return receivedByFilter; }

    /** Stops the broker. */
    public void stop() { if (broker != null) broker.stop(); }
}
