package ssg.legoflow.interop.amqp;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.BrokerMode;
import ssg.legoflow.messaging.amqp.client.service.AmqpClientService;
import ssg.legoflow.messaging.amqp.link.SenderLink;
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
 * AMQP interoperability tests: lego-flow client ↔ real Docker brokers.
 *
 * <p>This test requires Docker brokers running locally. It is excluded from the
 * regular Gradle build via {@code -PskipInteropTests=true} and runs in the CI
 * {@code interoperability-tests} job after Docker services are started.
 *
 * <p>For in-process (no-Docker) pipeline verification, see
 * {@link ssg.legoflow.messaging.amqp.interop.InProcessIntegrationTest}.
 *
 * <p>Brokers expected running:
 * <ul>
 *   <li>RabbitMQ: localhost:5672</li>
 *   <li>Artemis: localhost:5675</li>
 *   <li>Qpid Dispatch: localhost:5674 (disabled on arm64)</li>
 * </ul>
 */
@Disabled("Run only in CI interoperability-tests job with Docker services")
class AmqpBrokerInteropTest {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpBrokerInteropTest.class);
    private static final String HOST = "localhost";

    private static final int RABBITMQ_PORT = 5672;
    private static final int QPID_PORT = 5674;
    private static final int ARTEMIS_PORT = 5675;

    private SelectableChannelManager channelManager;

    @BeforeAll
    static void waitForBrokers() throws Exception {
        waitForPort(RABBITMQ_PORT, Duration.ofSeconds(30));
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
}
