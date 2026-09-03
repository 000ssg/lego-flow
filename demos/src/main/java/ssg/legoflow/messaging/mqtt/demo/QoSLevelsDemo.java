package ssg.legoflow.messaging.mqtt.demo;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * Demo demonstrating MQTT QoS 0, 1, and 2 delivery guarantees.
 *
 * @since 0.1.0
 */
public final class QoSLevelsDemo {

    private static final Logger LOG = LoggerFactory.getLogger(QoSLevelsDemo.class);

    private final Map<QoS, List<String>> receivedByQoS = new EnumMap<>(QoS.class);
    private MqttBroker broker;

    /**
     * Creates the demo.
     */
    public QoSLevelsDemo() {
        for (QoS qos : QoS.values()) {
            receivedByQoS.put(qos, new CopyOnWriteArrayList<>());
        }
    }

    /**
     * Runs the demo, publishing at each QoS level.
     *
     * @param port the broker port (0 for ephemeral)
     * @throws Exception on error
     */
    public void run(int port) throws Exception {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.bind("localhost", port);
        int actualPort = broker.getPort();

        try (var pub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(actualPort).clientId("qos-pub").build());
             var sub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(actualPort).clientId("qos-sub").build())) {

            pub.connect().get();
            sub.connect().get();

            for (QoS qos : QoS.values()) {
                String topic = "test/qos/" + qos.value();
                sub.subscribe(topic, qos, (t, payload, q, r) -> {
                    receivedByQoS.get(qos).add(new String(payload, StandardCharsets.UTF_8));
                    LOG.info("QoS {}: received '{}'", qos, new String(payload, StandardCharsets.UTF_8));
                }).get();

                pub.publish(topic, ("msg-qos-" + qos.value()).getBytes(StandardCharsets.UTF_8),
                        qos, false).get();
            }

            Thread.sleep(500);
        }
    }

    /** Returns received messages keyed by QoS level. */
    public Map<QoS, List<String>> getReceivedByQoS() { return receivedByQoS; }

    /** Stops the broker. */
    public void stop() { if (broker != null) broker.stop(); }
}
