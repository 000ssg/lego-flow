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
 * Demo demonstrating MQTT retained messages: publish retained, new subscriber
 * receives the last value.
 *
 * <p>Runs against an in-house {@link MqttBroker} over in-memory transport pairs —
 * no network, no external broker.</p>
 *
 * @since 0.1.0
 */
public final class RetainedMessageDemo {

    private static final Logger LOG = LoggerFactory.getLogger(RetainedMessageDemo.class);

    private final List<String> receivedByLateSubscriber = new CopyOnWriteArrayList<>();
    private MqttBroker broker;

    /**
     * Runs the retained messages demo against an in-house broker (in-memory transport).
     *
     * @throws Exception on error
     */
    public void run() throws Exception {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.start();
        try {
            // Step 1: publisher publishes a retained message
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);
            try (var pub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("retain-pub").build(), pubPair[1])) {
                pub.connect().get(5, TimeUnit.SECONDS);
                pub.publish("status/device1", "online".getBytes(StandardCharsets.UTF_8),
                        QoS.AT_LEAST_ONCE, true).get(5, TimeUnit.SECONDS);
                LOG.info("Published retained message");
            }

            Thread.sleep(200);

            // Step 2: late subscriber connects and should receive the retained message
            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            var latch = new CountDownLatch(1);
            try (var sub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("retain-sub").build(), subPair[1])) {
                sub.connect().get(5, TimeUnit.SECONDS);
                sub.subscribe("status/device1", QoS.AT_LEAST_ONCE, (t, payload, q, r) -> {
                    String msg = new String(payload, StandardCharsets.UTF_8);
                    receivedByLateSubscriber.add(msg);
                    latch.countDown();
                    LOG.info("Late subscriber received: {} (retain={})", msg, r);
                }).get(5, TimeUnit.SECONDS);
                latch.await(5, TimeUnit.SECONDS);
            }
        } finally {
            broker.stop();
        }
    }

    /** Returns messages received by the late subscriber. */
    public List<String> getReceivedByLateSubscriber() { return receivedByLateSubscriber; }

    /** Stops the broker. */
    public void stop() { if (broker != null) broker.stop(); }
}
