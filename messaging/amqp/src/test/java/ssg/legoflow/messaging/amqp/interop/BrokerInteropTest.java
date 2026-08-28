package ssg.legoflow.messaging.amqp.interop;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.BrokerMode;
import ssg.legoflow.messaging.amqp.client.ClientConfig;
import ssg.legoflow.messaging.amqp.client.service.AmqpClientService;
import ssg.legoflow.messaging.amqp.container.AmqpContainer;
import ssg.legoflow.messaging.amqp.container.ContainerConfig;
import ssg.legoflow.messaging.amqp.container.ContainerMode;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.transport.InMemoryTransport;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Interop tests: lego-flow client ↔ Docker brokers, in-process client ↔ server.
 *
 * <p>Client uses {@link SocketChannel} on a virtual thread (no selector needed for single
 * connection). Server uses {@link SelectableChannelManager} for accept + multiplexing.
 * <p>Brokers expected running:
 * <ul>
 *   <li>RabbitMQ: localhost:5672 (RABBITMQ mode, ANONYMOUS)</li>
 *   <li>Qpid Dispatch: localhost:5674 (QPID_DISPATCH, no SASL)</li>
 *   <li>Artemis: localhost:5675 (ARTEMIS, PLAIN auth admin/admin)</li>
 * </ul>
 */
class BrokerInteropTest {

    private static final Logger LOG = LoggerFactory.getLogger(BrokerInteropTest.class);
    private static final String HOST = "localhost";

    private static final int RABBITMQ_PORT = 5672;
    private static final int QPID_PORT = 5674;
    private static final int ARTEMIS_PORT = 5675;

    private SelectableChannelManager channelManager;

    @BeforeAll
    static void waitForBrokers() throws Exception {
        waitForPort(RABBITMQ_PORT, Duration.ofSeconds(30));
        waitForPort(QPID_PORT, Duration.ofSeconds(30));
        waitForPort(ARTEMIS_PORT, Duration.ofSeconds(30));
        LOG.info("All brokers ready");
    }

    private static void waitForPort(int port, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try (SocketChannel ch = SocketChannel.open()) {
                if (ch.connect(new InetSocketAddress(HOST, port))) return;
            } catch (Exception ignored) {
                Thread.sleep(500);
            }
        }
        throw new RuntimeException("Port " + port + " did not open within " + timeout);
    }

    @BeforeEach
    void createChannelManager() {
        channelManager = new SelectableChannelManager(null);
        channelManager.startEventLoop();
    }

    @AfterEach
    void closeChannelManager() {
        channelManager.stopEventLoop();
        channelManager.close();
    }

    /** Creates a client service that connects to a Docker broker. */
    private AmqpClientService connectClient(String host, int port, BrokerMode mode, String containerId,
                                            String username, String password) {
        var service = AmqpClientService.builder(host, port)
                .brokerMode(mode)
                .containerId(containerId)
                .timeout(Duration.ofSeconds(10))
                .build();
        service.connect(new DefaultServiceContext(ServiceUser.anonymous()));
        return service;
    }

    @Nested class RabbitMQ {
        @Test void connectAndCreateSession() throws Exception {
            var service = connectClient(HOST, RABBITMQ_PORT, BrokerMode.RABBITMQ, "test-rabbit", null, null);
            AmqpClient client = service.getClient();
            assertThat(client).isNotNull();
            assertThat(client.isConnected()).isTrue();
            try {
                var session = client.createSession();
                assertThat(session).isNotNull();
            } finally {
                client.close();
                service.disconnect(new DefaultServiceContext(ServiceUser.anonymous()));
            }
            LOG.info("RabbitMQ: connect + createSession OK");
        }
    }

    @Disabled("amd64-only Docker image — incompatible with arm64 hosts (Apple Silicon)")
    @Nested class QpidDispatch {
        @Test void connectAndCreateSession() throws Exception {
            var service = connectClient(HOST, QPID_PORT, BrokerMode.QPID_DISPATCH, "test-qpid", null, null);
            AmqpClient client = service.getClient();
            assertThat(client.isConnected()).isTrue();
            try {
                var session = client.createSession();
                assertThat(session).isNotNull();
            } finally {
                client.close();
                service.disconnect(new DefaultServiceContext(ServiceUser.anonymous()));
            }
            LOG.info("Qpid Dispatch: connect + createSession OK");
        }
    }

    @Nested class ApacheArtemis {
        @Test void connectWithAuth() throws Exception {
            var service = connectClient(HOST, ARTEMIS_PORT, BrokerMode.ARTEMIS, "test-artemis", "admin", "admin");
            AmqpClient client = service.getClient();
            assertThat(client.isConnected()).isTrue();
            try {
                var session = client.createSession();
                assertThat(session).isNotNull();
            } finally {
                client.close();
                service.disconnect(new DefaultServiceContext(ServiceUser.anonymous()));
            }
            LOG.info("Artemis: connect + auth + createSession OK");
        }
    }

    @Nested class InProcess {
        @Test void clientServerMessaging() throws Exception {
            var transportPair = InMemoryTransport.createPair();
            var serverContainer = new AmqpContainer(ContainerConfig.builder()
                    .containerId("test-server")
                    .host("localhost")
                    .port(0)
                    .mode(ContainerMode.STANDARD)
                    .build());
            serverContainer.start();

            final String[] receivedBody = {null};
            var receiveLatch = new CountDownLatch(1);

            serverContainer.messageHandler((connCtx, incoming) -> {
                receivedBody[0] = incoming.message().bodyAsString();
                receiveLatch.countDown();
                serverContainer.accept(connCtx, incoming);
            });

            Thread serverThread = Thread.ofVirtual().name("inproc-server").start(() -> {
                serverContainer.handleConnection(transportPair[1]);
            });
            Thread.sleep(300);

            var clientConfig = ClientConfig.builder()
                    .containerId("test-client")
                    .host("localhost")
                    .port(0)
                    .maxFrameSize(Integer.MAX_VALUE)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            var client = new AmqpClient(clientConfig);
            client.connect(transportPair[0]);

            assertThat(client.isConnected()).isTrue();
            try {
                var session = client.createSession();
                var sender = client.createSender(session, "s1", "q1");
                var msg = AmqpMessage.of("hello-inproc");
                client.send(sender, msg, true);
                assertThat(receiveLatch.await(10, TimeUnit.SECONDS))
                        .as("Message should be received")
                        .isTrue();
                assertThat(receivedBody[0]).isEqualTo("hello-inproc");
            } finally {
                client.close();
                serverContainer.close();
            }
            LOG.info("In-process client↔server: messaging OK");
        }

        @Test void connect() throws Exception {
            var transportPair = InMemoryTransport.createPair();
            var serverContainer = new AmqpContainer(ContainerConfig.builder()
                    .containerId("test-server")
                    .host("localhost")
                    .port(0)
                    .mode(ContainerMode.STANDARD)
                    .build());
            serverContainer.start();

            Thread serverThread = Thread.ofVirtual().name("inproc-server").start(() -> {
                serverContainer.handleConnection(transportPair[1]);
            });
            Thread.sleep(300);

            var clientConfig = ClientConfig.builder()
                    .containerId("test-client")
                    .host("localhost")
                    .port(0)
                    .maxFrameSize(Integer.MAX_VALUE)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            var client = new AmqpClient(clientConfig);
            client.connect(transportPair[0]);

            assertThat(client.isConnected()).isTrue();
            try {
                var session = client.createSession();
                assertThat(session).isNotNull();
            } finally {
                client.close();
                serverContainer.close();
            }
            LOG.info("In-process client↔server: connect OK");
        }
    }
}
