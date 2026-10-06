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
import java.util.concurrent.TimeUnit;

/**
 * Demo demonstrating clean session vs persistent session: offline message queuing.
 *
 * <p>Runs against an in-house {@link MqttBroker} over in-memory transport pairs —
 * no network, no external broker.</p>
 *
 * @since 0.1.0
 */
public final class SessionPersistenceDemo {

    private static final Logger LOG = LoggerFactory.getLogger(SessionPersistenceDemo.class);

    private final List<String> receivedAfterReconnect = new CopyOnWriteArrayList<>();

    /**
     * Runs the session persistence demo against an in-house broker (in-memory transport).
     *
     * @throws Exception on error
     */
    public void run() throws Exception {
        var broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.start();
        try {
            var persistConfig = MqttClientConfig.defaults()
                    .clientId("persistent-client")
                    .cleanSession(false)
                    .build();

            // Step 1: Connect with persistent session, subscribe, then disconnect
            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            try (var sub = new MqttClient(persistConfig, subPair[1])) {
                sub.connect().get(5, TimeUnit.SECONDS);
                sub.subscribe("events/important", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    receivedAfterReconnect.add(new String(p, StandardCharsets.UTF_8));
                    LOG.info("Received after reconnect: {}", new String(p, StandardCharsets.UTF_8));
                }).get(5, TimeUnit.SECONDS);
                sub.disconnect().get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            // Step 2: Publish while the persistent client is offline
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);
            try (var pub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("publisher").build(), pubPair[1])) {
                pub.connect().get(5, TimeUnit.SECONDS);
                pub.publish("events/important", "offline-msg-1".getBytes(StandardCharsets.UTF_8),
                        QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                pub.publish("events/important", "offline-msg-2".getBytes(StandardCharsets.UTF_8),
                        QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            // Step 3: Reconnect persistent client — should receive queued messages
            var sub2Pair = InMemoryMqttTransport.createPair();
            broker.handleConnection(sub2Pair[0]);
            try (var sub2 = new MqttClient(persistConfig, sub2Pair[1])) {
                sub2.connect().get(5, TimeUnit.SECONDS);
                sub2.subscribe("events/important", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    receivedAfterReconnect.add(new String(p, StandardCharsets.UTF_8));
                }).get(5, TimeUnit.SECONDS);
                Thread.sleep(500);
            }
        } finally {
            broker.stop();
        }
    }

    /** Returns messages received after reconnect. */
    public List<String> getReceivedAfterReconnect() { return receivedAfterReconnect; }
}
