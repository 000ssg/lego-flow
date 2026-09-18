package ssg.legoflow.interop.mqtt;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.service.MqttClientService;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow MQTT client → Mosquitto broker.
 *
 * <p>Exercises the production client path: {@link MqttClientService} opens a
 * {@code TcpDataChannel} + {@code MqttPipelineTransport} against a real external
 * broker (Mosquitto), driven by the in-house {@link SelectableChannelManager}.
 * This complements {@link MqttV5FeatureTest}, which covers v5 features against
 * the in-house {@code MqttBroker} over in-memory transports.
 *
 * <p>Configuration via system properties:
 *   interop.mosquitto.host (default: localhost)
 *   interop.mosquitto.port (default: 1883)
 *
 * <p>Requires the Mosquitto container from {@code docker-compose.yml} to be running:
 * {@code docker compose -f interop-tests/docker-compose.yml up -d mosquitto}
 */
@Tag("messaging-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MqttMosquittoInteropTest {

    private final String host = System.getProperty("interop.mosquitto.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.mosquitto.port", "1883"));

    private static SelectableChannelManager channelManager;
    private MqttClientService service;

    @BeforeAll
    static void setUpManager() {
        channelManager = new SelectableChannelManager(null);
        channelManager.startEventLoop();
    }

    @AfterAll
    static void tearDownManager() throws Exception {
        if (channelManager != null) {
            channelManager.stopEventLoop();
            channelManager.close();
        }
    }

    @BeforeEach
    void connect() {
        service = MqttClientService.builder(host, port)
                .name("interop-mqtt-" + System.identityHashCode(this))
                .build();
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.setAttribute("channelManager", channelManager);
        service.connect(ctx);
    }

    @AfterEach
    void disconnect() {
        if (service != null) {
            var ctx = new DefaultServiceContext(ServiceUser.anonymous());
            ctx.setAttribute("channelManager", channelManager);
            service.disconnect(ctx);
        }
    }

    @Test
    void testConnection() {
        var client = service.getClient();
        assertThat(client).isNotNull();
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testSendAndReceive() throws Exception {
        var client = service.getClient();
        String topic = "legoflow/interop/" + System.identityHashCode(this);
        String expected = "hello-mosquitto";

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> received = new AtomicReference<>();
        client.setCallback(new ssg.legoflow.messaging.mqtt.client.MqttCallback() {
            @Override
            public void onMessage(String t, ssg.legoflow.messaging.mqtt.protocol.PublishPacket message) {
                if (topic.equals(t)) {
                    received.set(message.payload());
                    latch.countDown();
                }
            }

            @Override public void onConnectionLost(Throwable cause) { }
            @Override public void onReconnected() { }
            @Override public void onDeliveryComplete(int packetId) { }
        });

        client.subscribe(topic, QoS.AT_LEAST_ONCE, (t, p, q, r) -> { }).get(10, TimeUnit.SECONDS);
        client.publish(topic, expected.getBytes(StandardCharsets.UTF_8),
                QoS.AT_LEAST_ONCE, false).get(10, TimeUnit.SECONDS);

        assertThat(latch.await(10, TimeUnit.SECONDS))
                .as("should receive message from Mosquitto").isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(new String(received.get(), StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    void testMultipleMessages() throws Exception {
        var client = service.getClient();
        String topic = "legoflow/interop-multi/" + System.identityHashCode(this);
        CountDownLatch latch = new CountDownLatch(5);

        client.setCallback(new ssg.legoflow.messaging.mqtt.client.MqttCallback() {
            @Override
            public void onMessage(String t, ssg.legoflow.messaging.mqtt.protocol.PublishPacket message) {
                if (topic.equals(t)) latch.countDown();
            }

            @Override public void onConnectionLost(Throwable cause) { }
            @Override public void onReconnected() { }
            @Override public void onDeliveryComplete(int packetId) { }
        });

        client.subscribe(topic, QoS.AT_LEAST_ONCE, (t, p, q, r) -> { }).get(10, TimeUnit.SECONDS);
        for (int i = 0; i < 5; i++) {
            client.publish(topic, ("msg-" + i).getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get(10, TimeUnit.SECONDS);
        }

        assertThat(latch.await(10, TimeUnit.SECONDS))
                .as("should receive 5 messages from Mosquitto").isTrue();
    }
}
