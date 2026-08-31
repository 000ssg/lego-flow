package ssg.legoflow.messaging.amqp.service;

import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.service.AmqpClientService;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.server.service.AmqpContainerService;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for AMQP client ↔ container over TCP through {@link SelectableChannelManager}.
 *
 * <p>These exercise the full pipeline: SocketChannel → SelectableChannelManager →
 * DataChannel → ChannelPipeline → PipelineTransport → AMQP protocol layer.
 */
class AmqpServiceIntegrationTest {

    private SelectableChannelManager manager;
    private DefaultServiceContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
        manager = new SelectableChannelManager(ctx);
        ctx.setAttribute("channelManager", manager);
        manager.startEventLoop();
    }

    @AfterEach
    void tearDown() {
        manager.stopEventLoop();
        manager.close();
    }

    @Test
    void testServerStartsAndStops() throws Exception {
        var server = AmqpContainerService.builder()
                .port(0)
                .containerId("test-server")
                .build();

        assertThat(server.port()).isNegative(); // Not yet connected

        server.connect(ctx);
        int actualPort = server.port();
        assertThat(actualPort).isGreaterThan(0);

        // Give selector time to register the server channel
        Thread.sleep(200);

        server.disconnect(ctx);

        // Verify graceful shutdown
        assertThat(server.isConnected()).isFalse();
    }

    @Test
    void testClientConnectsToServer() throws Exception {
        // Start server
        var server = AmqpContainerService.builder()
                .port(0)
                .containerId("test-server")
                .build();
        server.connect(ctx);
        int port = server.port();

        // Brief delay for server to accept connections
        Thread.sleep(300);

        // Connect client
        var client = AmqpClientService.builder("localhost", port)
                .timeout(Duration.ofSeconds(10))
                .build();
        client.connect(ctx);

        assertThat(client.isConnected()).isTrue();
        assertThat(client.getClient()).isNotNull();

        client.disconnect(ctx);
        server.disconnect(ctx);
    }

    @Test
    void testClientDisconnectBeforeConnectDoesNotThrow() throws Exception {
        var client = AmqpClientService.builder("localhost", 5672).build();
        // Should not throw even without prior connection
        try {
            client.disconnect(ctx);
        } catch (Exception e) {
            fail("disconnect() should not throw before connect: " + e.getMessage());
        }
    }

    @Test
    void testServerDisconnectBeforeConnectDoesNotThrow() throws Exception {
        var server = AmqpContainerService.builder().port(0).build();
        try {
            server.disconnect(ctx);
        } catch (Exception e) {
            fail("disconnect() should not throw before connect: " + e.getMessage());
        }
    }

    @Test
    void testClientConnectionTimeout() {
        var client = AmqpClientService.builder("localhost", 59999)
                .timeout(Duration.ofMillis(500))
                .build();
        // Connecting to a closed port: OS may refuse immediately (RST) or the latch
        // may time out waiting for TCP connect. Either way, connection should fail.
        assertThatThrownBy(() -> client.connect(ctx))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testFullMessagingPipeline() throws Exception {
        // Start server with message handler
        var server = AmqpContainerService.builder()
                .port(0)
                .containerId("test-server")
                .build();
        server.connect(ctx);
        int port = server.port();

        final String[] receivedBody = {null};
        var receiveLatch = new CountDownLatch(1);

        server.setMessageHandler((connCtx, incoming) -> {
            receivedBody[0] = incoming.message().bodyAsString();
            receiveLatch.countDown();
            server.getContainer().accept(connCtx, incoming);
        });

        // Give server time to start accepting
        Thread.sleep(300);

        // Connect client
        var client = AmqpClientService.builder("localhost", port)
                .timeout(Duration.ofSeconds(10))
                .build();
        client.connect(ctx);

        try {
            AmqpClient amqpClient = client.getClient();
            assertThat(amqpClient).isNotNull();

            var session = amqpClient.createSession();
            var sender = amqpClient.createSender(session, "sender-1", "test-queue");
            var msg = AmqpMessage.of("hello-service-layer");
            amqpClient.send(sender, msg, true);

            // Wait for server to receive
            assertThat(receiveLatch.await(10, TimeUnit.SECONDS))
                    .as("Server should receive the message")
                    .isTrue();
            assertThat(receivedBody[0]).isEqualTo("hello-service-layer");
        } finally {
            client.disconnect(ctx);
            server.disconnect(ctx);
        }
    }

    @Test
    void testMultipleClients() throws Exception {
        var server = AmqpContainerService.builder()
                .port(0)
                .containerId("multi-server")
                .build();
        server.connect(ctx);
        int port = server.port();

        Thread.sleep(300); // Server ready

        // Each client must have a unique service name — the manager uses name as the
        // key for channels and pipelines. Using the same name causes collisions.
        var clients = new AmqpClientService[3];
        for (int i = 0; i < 3; i++) {
            clients[i] = AmqpClientService.builder("localhost", port)
                    .name("amqp-client-" + i) // Unique name per instance
                    .timeout(Duration.ofSeconds(10))
                    .containerId("client-" + i)
                    .build();
            clients[i].connect(ctx);
            assertThat(clients[i].getClient())
                    .as("Client " + i + " should be connected")
                    .isNotNull();
        }

        // Verify all clients can create sessions
        for (int i = 0; i < 3; i++) {
            var session = clients[i].getClient().createSession();
            assertThat(session).as("Session for client " + i).isNotNull();
        }

        // Cleanup
        for (var c : clients) {
            c.disconnect(ctx);
        }
        server.disconnect(ctx);
    }
}
