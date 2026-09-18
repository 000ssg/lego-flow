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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * Simplest MQTT demo: one publisher, one subscriber, single topic.
 *
 * <p>Runs against an in-house {@link MqttBroker} over in-memory transport pairs —
 * no network, no external broker.</p>
 *
 * @since 0.1.0
 */
public final class SimplePubSubDemo {

    private static final Logger LOG = LoggerFactory.getLogger(SimplePubSubDemo.class);

    private final List<String> receivedMessages = new CopyOnWriteArrayList<>();

    /**
     * Runs the publish/subscribe demo against an in-house broker (in-memory transport).
     *
     * @param topic   the topic to use
     * @param message the message to publish
     * @throws Exception on error
     */
    public void run(String topic, String message) throws Exception {
        var broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.start();
        try {
            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);

            var latch = new CountDownLatch(1);
            try (var subscriber = new MqttClient(MqttClientConfig.defaults()
                    .clientId("subscriber-1").build(), subPair[1]);
                 var publisher = new MqttClient(MqttClientConfig.defaults()
                    .clientId("publisher-1").build(), pubPair[1])) {
                publisher.connect().get(5, TimeUnit.SECONDS);
                subscriber.connect().get(5, TimeUnit.SECONDS);

                subscriber.subscribe(topic, QoS.AT_LEAST_ONCE,
                        (t, payload, qos, retain) -> {
                            String msg = new String(payload, StandardCharsets.UTF_8);
                            receivedMessages.add(msg);
                            latch.countDown();
                            LOG.info("Received on {}: {}", t, msg);
                        }).get(5, TimeUnit.SECONDS);

                publisher.publish(topic, message.getBytes(StandardCharsets.UTF_8),
                        QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);

                latch.await(5, TimeUnit.SECONDS);
            }
        } finally {
            broker.stop();
        }
        LOG.info("Demo complete. Received {} messages", receivedMessages.size());
    }

    /** Returns the list of received messages. */
    public List<String> getReceivedMessages() { return receivedMessages; }
}
