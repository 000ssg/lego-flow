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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Demo demonstrating QoS levels 0, 1 and 2: at-most-once, at-least-once,
 * and exactly-once delivery semantics.
 *
 * <p>Runs against an in-house {@link MqttBroker} over in-memory transport pairs —
 * no network, no external broker.</p>
 *
 * @since 0.1.0
 */
public final class QoSLevelsDemo {

    private static final Logger LOG = LoggerFactory.getLogger(QoSLevelsDemo.class);

    private final List<String> receivedQoS0 = new CopyOnWriteArrayList<>();
    private final List<String> receivedQoS1 = new CopyOnWriteArrayList<>();
    private final List<String> receivedQoS2 = new CopyOnWriteArrayList<>();
    private MqttBroker broker;

    /**
     * Runs the QoS levels demo against an in-house broker (in-memory transport).
     *
     * @throws Exception on error
     */
    public void run() throws Exception {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.start();

        // QoS 0 -- fire and forget
        try {
            var pair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pair[0]);
            try (var client = new MqttClient(MqttClientConfig.defaults()
                    .clientId("qos0-demo").build(), pair[1])) {
                client.connect().get(5, TimeUnit.SECONDS);
                client.publish("qos/level0", "fire".getBytes(StandardCharsets.UTF_8),
                        QoS.AT_MOST_ONCE, false).get(5, TimeUnit.SECONDS);
                LOG.info("QoS 0 published");
            }
        } catch (Exception e) {
            LOG.warn("QoS 0 publish: {}", e.getMessage());
        }

        // QoS 1 -- at least once (PUBACK)
        var qos1 = new CountDownLatch(1);
        var pair1 = InMemoryMqttTransport.createPair();
        broker.handleConnection(pair1[0]);
        try (var sub = new MqttClient(MqttClientConfig.defaults()
                .clientId("qos1-sub").build(), pair1[1])) {
            sub.connect().get(5, TimeUnit.SECONDS);
            sub.subscribe("qos/level1", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                receivedQoS1.add(new String(p, StandardCharsets.UTF_8));
                qos1.countDown();
            }).get(5, TimeUnit.SECONDS);

            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);
            try (var pub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("qos1-pub").build(), pubPair[1])) {
                pub.connect().get(5, TimeUnit.SECONDS);
                pub.publish("qos/level1", "once".getBytes(StandardCharsets.UTF_8),
                        QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            }
            qos1.await(5, TimeUnit.SECONDS);
        }

        // QoS 2 -- exactly once (PUBREC/PUBREL/PUBCOMP)
        var qos2 = new CountDownLatch(1);
        var pair2 = InMemoryMqttTransport.createPair();
        broker.handleConnection(pair2[0]);
        try (var sub = new MqttClient(MqttClientConfig.defaults()
                .clientId("qos2-sub").build(), pair2[1])) {
            sub.connect().get(5, TimeUnit.SECONDS);
            sub.subscribe("qos/level2", QoS.EXACTLY_ONCE, (t, p, q, r) -> {
                receivedQoS2.add(new String(p, StandardCharsets.UTF_8));
                qos2.countDown();
            }).get(5, TimeUnit.SECONDS);

            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);
            try (var pub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("qos2-pub").build(), pubPair[1])) {
                pub.connect().get(5, TimeUnit.SECONDS);
                pub.publish("qos/level2", "exact".getBytes(StandardCharsets.UTF_8),
                        QoS.EXACTLY_ONCE, false).get(5, TimeUnit.SECONDS);
            }
            qos2.await(5, TimeUnit.SECONDS);
        }
    }

    /** Returns messages received at QoS 1. */
    public List<String> getReceivedQoS1() { return receivedQoS1; }

    /** Returns messages received at QoS 2. */
    public List<String> getReceivedQoS2() { return receivedQoS2; }

    /** Stops the broker. */
    public void stop() { if (broker != null) broker.stop(); }
}
