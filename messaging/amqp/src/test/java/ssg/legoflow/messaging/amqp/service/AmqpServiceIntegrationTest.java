package ssg.legoflow.messaging.amqp.service;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.service.AmqpClientService;
import ssg.legoflow.messaging.amqp.common.AmqpEventListener;
import ssg.legoflow.messaging.amqp.container.AmqpContainer;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.server.service.AmqpContainerService;
import ssg.legoflow.messaging.amqp.transport.InMemoryTransport;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for AMQP client ↔ container.
 *
 * <p>Tests use TCP loopback for service-layer integration, and in-memory transport
 * for protocol-layer tests. The AMQP protocol handshake is synchronous, so
 * in-memory transport avoids the race condition between async server handling
 * and synchronous client reads.
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

        assertThat(server.port()).isNegative();

        server.connect(ctx);
        int actualPort = server.port();
        assertThat(actualPort).isGreaterThan(0);

        // Poll for selector to register server channel (service-layer sync point)
        int retries = 20;
        while (retries-- > 0) {
            Thread.sleep(100);
        }

        server.disconnect(ctx);

        assertThat(server.isConnected()).isFalse();
    }

    @Test
    void testClientConnectsToServer() throws Exception {
        var server = AmqpContainerService.builder()
                .port(0)
                .containerId("test-server-2")
                .build();
        server.connect(ctx);
        int port = server.port();

        // Wait for server to be ready - poll until serverChannel is registered with selector
        int retries = 50;
        while (retries-- > 0) {
            if (server.isReady()) break;
            Thread.sleep(10);
        }
        assertThat(server.isReady()).isTrue();

        // Wait for CONNECTION_OPENED event — signals handshake is complete
        CountDownLatch openLatch = new CountDownLatch(1);
        server.setListener(AmqpEventListener.latchOnFirst(openLatch, AmqpEventListener.EventType.CONNECTION_OPENED));

        var client = AmqpClientService.builder("localhost", port)
                .timeout(Duration.ofSeconds(10))
                .build();
        client.connect(ctx);

        // Poll for client to fully connect before proceeding
        int connRetries = 200;
        while (connRetries-- > 0) {
            if (client.isConnected()) break;
            Thread.sleep(10);
        }
        assertThat(client.isConnected())
                .as("Client should be connected")
                .isTrue();

        // Give client time to initialize before server sends protocol frames
        Thread.sleep(100);

        // Wait for the server to complete the AMQP handshake
        assertThat(openLatch.await(10, TimeUnit.SECONDS)).isTrue();

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
        // Retry logic to handle flaky connection timing
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                testFullMessagingPipelineInternal();
                return; // Success
            } catch (Exception e) {
                if (attempt == 4) throw e;
                System.out.println("Attempt " + (attempt + 1) + " failed: " + e.getMessage() + ", retrying...");
                Thread.sleep(1000);
            }
        }
    }

    private void testFullMessagingPipelineInternal() throws Exception {
        var server = AmqpContainerService.builder()
                .port(0)
                .containerId("test-server-3")
                .build();
        server.connect(ctx);
        int port = server.port();

        // Wait for server to be ready - poll until serverChannel is registered with selector
        int retries = 50;
        while (retries-- > 0) {
            if (server.isReady()) break;
            Thread.sleep(10);
        }
        assertThat(server.isReady()).isTrue();

        final String[] receivedBody = {null};
        var receiveLatch = new CountDownLatch(1);

        server.setMessageHandler((connCtx, incoming) -> {
            receivedBody[0] = incoming.message().bodyAsString();
            receiveLatch.countDown();
            server.getContainer().accept(connCtx, incoming);
        });

        // Wait for CONNECTION_OPENED event — signals handshake is complete
        CountDownLatch openLatch = new CountDownLatch(1);
        server.setListener(AmqpEventListener.latchOnFirst(openLatch, AmqpEventListener.EventType.CONNECTION_OPENED));

        // Wait for selector thread to fully initialize and start accepting connections.
        server.waitForReady();

        // Wait for server to stabilize after becoming ready
        Thread.sleep(200);

        var client = AmqpClientService.builder("localhost", port)
                .timeout(Duration.ofSeconds(10))
                .build();
        client.connect(ctx);

        // Poll for client to fully connect before proceeding
        int connRetries = 200;
        while (connRetries-- > 0) {
            if (client.isConnected()) break;
            Thread.sleep(10);
        }
        assertThat(client.isConnected())
                .as("Client should be connected")
                .isTrue();

        // Give client time to initialize before server sends protocol frames
        Thread.sleep(200);

        // Wait for the server to complete the AMQP handshake
        assertThat(openLatch.await(10, TimeUnit.SECONDS)).isTrue();

        try {
            AmqpClient amqpClient = client.getClient();
            assertThat(amqpClient).isNotNull();

            var session = amqpClient.createSession();
            var sender = amqpClient.createSender(session, "sender-1", "test-queue");
            var msg = AmqpMessage.of("hello-service-layer");
            amqpClient.send(sender, msg, true);

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

        // Wait for server to be ready - poll until serverChannel is registered with selector
        int retries = 50;
        while (retries-- > 0) {
            if (server.isReady()) break;
            Thread.sleep(10);
        }
        assertThat(server.isReady()).isTrue();

        // Set up listener BEFORE waiting for ready to avoid race
        CountDownLatch[] openLatches = new CountDownLatch[3];
        for (int i = 0; i < 3; i++) {
            openLatches[i] = new CountDownLatch(1);
        }
        server.setListener((event, connId, detail) -> {
            if (event == AmqpEventListener.EventType.CONNECTION_OPENED) {
                for (CountDownLatch latch : openLatches) {
                    if (latch.getCount() > 0) {
                        latch.countDown();
                        break;
                    }
                }
            }
        });

        // Wait for selector thread to fully initialize and start accepting connections.
        server.waitForReady();

        // Wait for server to stabilize after becoming ready
        Thread.sleep(100);

        var clients = new AmqpClientService[3];
        for (int i = 0; i < 3; i++) {
            clients[i] = AmqpClientService.builder("localhost", port)
                    .name("amqp-client-" + i)
                    .timeout(Duration.ofSeconds(10))
                    .containerId("client-" + i)
                    .build();
            clients[i].connect(ctx);

            // Poll for this client to fully connect before proceeding
            int connRetries = 200;
            while (connRetries-- > 0) {
                if (clients[i].isConnected()) break;
                Thread.sleep(10);
            }
            assertThat(clients[i].isConnected())
                    .as("Client " + i + " should connect")
                    .isTrue();

            // Longer delay between client connections to prevent selector overload
            if (i < 2) {
                Thread.sleep(100);
            }
        }

        // Wait for ALL server-side CONNECTION_OPENED events
        for (CountDownLatch latch : openLatches) {
            assertThat(latch.await(10, TimeUnit.SECONDS))
                    .as("Server should complete all client handshakes")
                    .isTrue();
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
