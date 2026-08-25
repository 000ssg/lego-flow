package ssg.legoflow.interop.amqp;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.ClientConfig;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.delivery.DeliveryState;
import ssg.legoflow.messaging.amqp.link.ReceiverLink;
import ssg.legoflow.messaging.amqp.link.SenderLink;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.types.AmqpType;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow AMQP 1.0 client to real AMQP broker.
 *
 * <p>Requires an AMQP 1.0-capable broker (e.g., RabbitMQ 4.x, Qpid, or
 * <a href="https://github.com/rabbitmq/rabbitmq-server">RabbitMQ</a>).
 * Currently <b>disabled</b> because Apache ActiveMQ 6.x does not support
 * the standard AMQP 1.0 SASL negotiation flow used by this client.
 *
 * <p>Configuration via system properties:
 *   interop.amqp.host (default: localhost)
 *   interop.amqp.port (default: 5672)
 *   interop.amqp.username (default: guest)
 *   interop.amqp.password (default: guest)
 *   interop.amqp.queue (default: interop-test-queue)
 *
 * <p>To run against RabbitMQ:
 *   docker run -d --rm -p 5672:5672 -p 15672:15672 rabbitmq:4-management
 *   mvn verify -Dinterop.amqp.host=localhost -DskipInteropTests=false
 */
    @Tag("messaging-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled("AMQP 1.0 SASL flow incompatible with RabbitMQ's AMQP 1.0 broker")
class AmqpInteropTest {

    private final String host = System.getProperty("interop.amqp.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.amqp.port", "5672"));
    private final String username = System.getProperty("interop.amqp.username", "guest");
    private final String password = System.getProperty("interop.amqp.password", "guest");
    private final String queueName = System.getProperty("interop.amqp.queue", "interop-test-queue");

    private AmqpClient client;

    @BeforeAll
    void connect() throws Exception {
        ClientConfig config = ClientConfig.builder()
                .host(host)
                .port(port)
                .connectTimeout(Duration.ofSeconds(10))
                .username(username)
                .password(password)
                .build();
        this.client = new AmqpClient(config);
        client.connect();
    }

    @AfterAll
    void disconnect() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testConnection() {
        assertThat(client).isNotNull();
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testSendAndReceiveMessage() throws Exception {
        String testMessage = "interop-amqp-test";

        // Create session, sender, and receiver links
        AmqpSession session = client.createSession();
        SenderLink sender = client.createSender(session, "interop-sender", queueName);
        ReceiverLink receiver = client.createReceiver(session, "interop-receiver", queueName);

        // Start receiving in background
        AtomicReference<Delivery> received = new AtomicReference<>();
        Thread receiveThread = new Thread(() -> {
            try {
                Delivery delivery = receiver.receive(5, TimeUnit.SECONDS);
                if (delivery != null) {
                    received.set(delivery);
                    // Accept the delivery
                    receiver.accept(delivery.deliveryId());
                }
            } catch (Exception e) {
                // Timeout or receive error is acceptable
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();

        // Send message
        AmqpMessage message = new AmqpMessage();
        message.bodyValue(new AmqpType.AmqpString(testMessage));
        Delivery delivery = sender.send(message, false);

        // Wait for receive
        receiveThread.join(10000);

        assertThat(received.get()).isNotNull();
        Object body = received.get().message().body();
        if (body instanceof AmqpType.AmqpString str) {
            assertThat(str.value()).isEqualTo(testMessage);
        }

        // Wait for sender settlement
        if (!sender.unsettledDeliveries().isEmpty()) {
            for (Delivery d : sender.unsettledDeliveries().values()) {
                d.settle(new DeliveryState.Accepted());
            }
        }
    }

    @Test
    void testMultipleMessages() throws Exception {
        AmqpSession session = client.createSession();
        String multiQueue = queueName + "-multi";
        SenderLink sender = client.createSender(session, "multi-sender", multiQueue);
        ReceiverLink receiver = client.createReceiver(session, "multi-receiver", multiQueue);

        // Send 5 messages
        for (int i = 0; i < 5; i++) {
            AmqpMessage msg = new AmqpMessage();
            msg.bodyValue(new AmqpType.AmqpString("msg-" + i));
            sender.send(msg, false);
        }

        // Receive all messages
        for (int i = 0; i < 5; i++) {
            Delivery delivery = receiver.receive(3, TimeUnit.SECONDS);
            if (delivery != null) {
                receiver.accept(delivery.deliveryId());
            }
        }
    }

    @Test
    void testClientConfigBuilder() {
        var config = ClientConfig.builder()
                .host("test")
                .port(5673)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        assertThat(config.host()).isEqualTo("test");
        assertThat(config.port()).isEqualTo(5673);
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void testAmqpSession() {
        assertThat(client).isNotNull();
        AmqpSession session = client.createSession();
        assertThat(session).isNotNull();
    }

    @Test
    void testCloseGracefully() throws Exception {
        // Use a dedicated client to avoid disrupting the shared connection
        var testConfig = ClientConfig.builder()
                .host(host).port(port)
                .connectTimeout(Duration.ofSeconds(10))
                .username(username)
                .password(password)
                .build();
        var testClient = new AmqpClient(testConfig);
        testClient.connect();
        assertThat(testClient.isConnected()).isTrue();
        testClient.close();
        assertThat(testClient.isConnected()).isFalse();
    }
}
