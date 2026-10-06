package ssg.legoflow.messaging.mqtt.demo;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Demo demonstrating MQTT topic patterns with + and # wildcards.
 *
 * <p>Runs against an in-house {@link MqttBroker} over in-memory transport pairs —
 * no network, no external broker.</p>
 *
 * @since 0.1.0
 */
public final class WildcardTopicsDemo {

    private static final Logger LOG = LoggerFactory.getLogger(WildcardTopicsDemo.class);

    private final Map<String, List<String>> receivedByFilter = new ConcurrentHashMap<>();

    /**
     * Runs the wildcard topics demo against an in-house broker (in-memory transport).
     *
     * @throws Exception on error
     */
    public void run() throws Exception {
        var broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.start();
        try {
            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);

            try (var pub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("wc-pub").build(), pubPair[1]);
                 var sub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("wc-sub").build(), subPair[1])) {

                pub.connect().get(5, TimeUnit.SECONDS);
                sub.connect().get(5, TimeUnit.SECONDS);

                // Subscribe with single-level wildcard
                String singleLevel = "sensors/+/temperature";
                receivedByFilter.put(singleLevel, new CopyOnWriteArrayList<>());
                sub.subscribe(singleLevel, QoS.AT_LEAST_ONCE, (t, payload, q, r) -> {
                    receivedByFilter.get(singleLevel).add(t);
                    LOG.info("Single-level match: {}", t);
                }).get(5, TimeUnit.SECONDS);

                // Subscribe with multi-level wildcard
                String multiLevel = "sensors/#";
                receivedByFilter.put(multiLevel, new CopyOnWriteArrayList<>());
                sub.subscribe(multiLevel, QoS.AT_LEAST_ONCE, (t, payload, q, r) -> {
                    receivedByFilter.get(multiLevel).add(t);
                    LOG.info("Multi-level match: {}", t);
                }).get(5, TimeUnit.SECONDS);

                // Publish on various topics
                pub.publish("sensors/room1/temperature", "22.5".getBytes(StandardCharsets.UTF_8),
                        QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                pub.publish("sensors/room2/temperature", "23.0".getBytes(StandardCharsets.UTF_8),
                        QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                pub.publish("sensors/room1/humidity", "45".getBytes(StandardCharsets.UTF_8),
                        QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);

                Thread.sleep(500);
            }
        } finally {
            broker.stop();
        }
    }

    /** Returns received topics keyed by subscription filter. */
    public Map<String, List<String>> getReceivedByFilter() { return receivedByFilter; }
}
