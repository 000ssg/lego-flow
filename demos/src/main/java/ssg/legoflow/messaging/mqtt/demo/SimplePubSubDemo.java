package ssg.legoflow.messaging.mqtt.demo;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
/**
 * Simplest MQTT demo: one publisher, one subscriber, single topic.
 *
 * @since 0.1.0
 */
public final class SimplePubSubDemo {

    private static final Logger LOG = LoggerFactory.getLogger(SimplePubSubDemo.class);

    private final MqttBroker broker;
    private final List<String> receivedMessages = new CopyOnWriteArrayList<>();
    private final int port;
    private MqttClient publisher;
    private MqttClient subscriber;

    /**
     * Creates and initializes the demo with an embedded broker.
     *
     * @param port the broker port (0 for ephemeral)
     */
    public SimplePubSubDemo(int port) {
        this.port = port;
        this.broker = new MqttBroker(new MqttBrokerConfig("localhost", port, 10, 65536, 32,
                true, false, 0, 100));
    }

    /**
     * Runs the publish/subscribe demo.
     *
     * @param topic   the topic to use
     * @param message the message to publish
     * @throws Exception on error
     */
    public void run(String topic, String message) throws Exception {
        broker.bind("localhost", port);
        int actualPort = broker.getPort();

        publisher = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(actualPort).clientId("publisher-1").build());
        subscriber = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(actualPort).clientId("subscriber-1").build());

        publisher.connect().get();
        subscriber.connect().get();

        subscriber.subscribe(topic, QoS.AT_LEAST_ONCE,
                (t, payload, qos, retain) -> {
                    String msg = new String(payload, StandardCharsets.UTF_8);
                    receivedMessages.add(msg);
                    LOG.info("Received on {}: {}", t, msg);
                }).get();

        publisher.publish(topic, message.getBytes(StandardCharsets.UTF_8),
                QoS.AT_LEAST_ONCE, false).get();

        Thread.sleep(500); // Allow delivery
        LOG.info("Demo complete. Received {} messages", receivedMessages.size());
    }

    /** Returns the list of received messages. */
    public List<String> getReceivedMessages() { return receivedMessages; }

    /** Returns the broker instance. */
    public MqttBroker getBroker() { return broker; }

    /** Stops all components. */
    public void stop() {
        if (publisher != null) publisher.close();
        if (subscriber != null) subscriber.close();
        broker.stop();
    }
}
