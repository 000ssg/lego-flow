package ssg.legoflow.interop.amqp;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.BrokerMode;
import ssg.legoflow.messaging.amqp.client.ClientConfig;
import ssg.legoflow.messaging.amqp.client.service.AmqpClientService;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.link.ReceiverLink;
import ssg.legoflow.messaging.amqp.link.SenderLink;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.types.AmqpType;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow AMQP 1.0 client ↔ various AMQP 1.0 brokers.
 *
 * <p>Each test creates its own connection — no shared protocol state between tests.
 * The SelectableChannelManager (event loop) is shared, but connections are per-test.
 *
 * <p>Configuration via system properties:
 *   interop.amqp.host (default: localhost)
 *   interop.amqp.port (default: 5672)
 *   interop.amqp.username (default: guest)
 *   interop.amqp.password (default: guest)
 *   interop.amqp.queue (default: interop-test-queue)
 *   interop.amqp.broker (default: ARTEMIS — one of ARTEMIS, RABBITMQ)
 */
@Tag("messaging-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AmqpInteropTest {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpInteropTest.class);

    enum Broker { ARTEMIS, RABBITMQ }

    private final String host = System.getProperty("interop.amqp.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.amqp.port", "5672"));
    private final String username = System.getProperty("interop.amqp.username", "guest");
    private final String password = System.getProperty("interop.amqp.password", "guest");
    private final String queueName = System.getProperty("interop.amqp.queue", "interop-test-queue");
    private final Broker broker = Broker.valueOf(System.getProperty("interop.amqp.broker", "ARTEMIS"));

    private BrokerMode brokerMode() {
        return switch (broker) {
            case RABBITMQ -> BrokerMode.RABBITMQ;
            default -> BrokerMode.STANDARD;
        };
    }

    // Shared event loop only — no shared connection state
    private static SelectableChannelManager channelManager;

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

    /** Create a fresh connection for the current test. */
    private AmqpClientService connectClient(String nameSuffix) {
        var builder = AmqpClientService.builder(host, port)
                .name("interop-" + nameSuffix)
                .containerId("interop-client-" + nameSuffix)
                .timeout(Duration.ofSeconds(10))
                .brokerMode(brokerMode());
        var svc = builder.username(username).password(password).build();
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.setAttribute("channelManager", channelManager);
        svc.connect(ctx);
        LOG.info("Connected to {} broker at {}:{}", broker, host, port);
        return svc;
    }

    /** Disconnect and close the given client. */
    private void disconnectClient(AmqpClientService svc) {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.setAttribute("channelManager", channelManager);
        svc.disconnect(ctx);
    }

    @Test
    void testConnection() throws Exception {
        var svc = connectClient("conn");
        try {
            assertThat(svc.getClient()).isNotNull();
            assertThat(svc.getClient().isConnected()).isTrue();
        } finally {
            disconnectClient(svc);
        }
    }

    @Test
    void testAmqpSession() throws Exception {
        var svc = connectClient("session");
        try {
            var client = svc.getClient();
            assertThat(client).isNotNull();
            AmqpSession session = client.createSession();
            assertThat(session).isNotNull();
        } finally {
            disconnectClient(svc);
        }
    }

    @Test
    void testSendAndReceiveMessage() throws Exception {
        var svc = connectClient("sendrecv");
        try {
            String testMessage = "interop-amqp-test";

            var client = svc.getClient();
            AmqpSession session = client.createSession();

            // RabbitMQ requires /queues/ prefix for address routing
            String effectiveQueue = brokerMode().formatAddress(queueName);

            SenderLink sender = client.createSender(session, "interop-sender", effectiveQueue);
            ReceiverLink receiver = client.createReceiver(session, "interop-receiver", effectiveQueue);

            // Build message before starting receive thread — avoids frame-stealing race
            AmqpMessage message = new AmqpMessage();
            message.bodyValue(new AmqpType.AmqpString(testMessage));

            // Start receive thread AFTER setup — no frame-stealing possible
            var latch = new CountDownLatch(1);
            AtomicReference<Delivery> received = new AtomicReference<>();
            Thread receiveThread = new Thread(() -> {
                try {
                    Delivery delivery = receiver.receive(10, TimeUnit.SECONDS);
                    if (delivery != null) {
                        received.set(delivery);
                        receiver.accept(delivery.deliveryId());
                    }
                    latch.countDown();
                } catch (Exception e) {
                    LOG.warn("Receive thread error", e);
                    latch.countDown();
                }
            });
            receiveThread.setDaemon(true);
            receiveThread.start();

            // Send at-most-once
            Delivery sent = sender.send(message, true);
            assertThat(sent).as("send should succeed").isNotNull();

            assertThat(latch.await(10, TimeUnit.SECONDS)).as("should receive message").isTrue();
            receiveThread.join(5000);

            assertThat(received.get()).isNotNull();
            Object body = received.get().message().body();
            if (body instanceof AmqpType.AmqpString str) {
                assertThat(str.value()).isEqualTo(testMessage);
            }
        } finally {
            disconnectClient(svc);
        }
    }

    @Test
    void testMultipleMessages() throws Exception {
        var svc = connectClient("multi");
        try {
            var client = svc.getClient();
            AmqpSession session = client.createSession();
            String multiQueue = queueName + "-multi";
            // RabbitMQ requires /queues/ prefix
            String effectiveQueue = brokerMode().formatAddress(multiQueue);
            SenderLink sender = client.createSender(session, "multi-sender", effectiveQueue);
            ReceiverLink receiver = client.createReceiver(session, "multi-receiver", effectiveQueue);

            // Send all messages first, then receive — no frame-stealing race
            for (int i = 0; i < 5; i++) {
                AmqpMessage msg = new AmqpMessage();
                msg.bodyValue(new AmqpType.AmqpString("msg-" + i));
                sender.send(msg, false);
            }

            for (int i = 0; i < 5; i++) {
                Delivery delivery = receiver.receive(3, TimeUnit.SECONDS);
                if (delivery != null) {
                    receiver.accept(delivery.deliveryId());
                }
            }
        } finally {
            disconnectClient(svc);
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
    void testCloseGracefully() throws Exception {
        var svc = connectClient("close");
        try {
            assertThat(svc.getClient().isConnected()).isTrue();
        } finally {
            disconnectClient(svc);
            assertThat(svc.getClient().isConnected()).isFalse();
        }
    }
}
