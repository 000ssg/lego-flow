package ssg.legoflow.messaging.mqtt.demo;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * Demo demonstrating MQTT retained messages: publish retained, new subscriber receives last value.
 *
 * @since 0.1.0
 */
public final class RetainedMessageDemo {

    private static final Logger LOG = LoggerFactory.getLogger(RetainedMessageDemo.class);

    private final List<String> receivedByLateSubscriber = new CopyOnWriteArrayList<>();
    private MqttBroker broker;

    /**
     * Runs the retained messages demo.
     *
     * @param port the broker port (0 for ephemeral)
     * @throws Exception on error
     */
    public void run(int port) throws Exception {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.bind("localhost", port);
        int actualPort = broker.getPort();

        // Publisher publishes a retained message
        try (var pub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(actualPort).clientId("retain-pub").build())) {
            pub.connect().get();
            pub.publish("status/device1", "online".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, true).get();
            LOG.info("Published retained message");
        }

        Thread.sleep(200);

        // Late subscriber connects and should receive the retained message
        try (var sub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(actualPort).clientId("retain-sub").build())) {
            sub.connect().get();
            sub.subscribe("status/device1", QoS.AT_LEAST_ONCE, (t, payload, q, r) -> {
                String msg = new String(payload, StandardCharsets.UTF_8);
                receivedByLateSubscriber.add(msg);
                LOG.info("Late subscriber received: {} (retain={})", msg, r);
            }).get();
            Thread.sleep(500);
        }
    }

    /** Returns messages received by the late subscriber. */
    public List<String> getReceivedByLateSubscriber() { return receivedByLateSubscriber; }

    /** Stops the broker. */
    public void stop() { if (broker != null) broker.stop(); }
}
