package ssg.legoflow.messaging.amqp.interop;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.ClientConfig;
import ssg.legoflow.messaging.amqp.container.AmqpContainer;
import ssg.legoflow.messaging.amqp.container.ContainerConfig;
import ssg.legoflow.messaging.amqp.container.ContainerMode;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.transport.InMemoryTransport;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * In-process integration tests: lego-flow client ↔ lego-flow server using
 * {@link InMemoryTransport}. No Docker, no network — pure pipeline verification.
 *
 * <p>These tests run on every CI build and verify the complete DP/DF/service pipeline
 * end-to-end (handshake, session, links, messaging).
 */
class InProcessIntegrationTest {

    private SelectableChannelManager channelManager;

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

            var serverReady = new CountDownLatch(1);
            Thread serverThread = Thread.ofVirtual().name("inproc-server").start(() -> {
                serverReady.countDown();
                serverContainer.handleConnection(transportPair[1]);
            });
            assertThat(serverReady.await(5, TimeUnit.SECONDS)).as("Server must start").isTrue();

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
        }

        @Test void connectAndCreateSession() throws Exception {
            var transportPair = InMemoryTransport.createPair();
            var serverContainer = new AmqpContainer(ContainerConfig.builder()
                    .containerId("test-server")
                    .host("localhost")
                    .port(0)
                    .mode(ContainerMode.STANDARD)
                    .build());
            serverContainer.start();

            var serverReady = new CountDownLatch(1);
            Thread serverThread = Thread.ofVirtual().name("inproc-server").start(() -> {
                serverReady.countDown();
                serverContainer.handleConnection(transportPair[1]);
            });
            assertThat(serverReady.await(5, TimeUnit.SECONDS)).as("Server must start").isTrue();

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
        }
    }
}
