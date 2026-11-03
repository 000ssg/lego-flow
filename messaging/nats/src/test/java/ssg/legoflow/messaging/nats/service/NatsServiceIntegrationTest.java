package ssg.legoflow.messaging.nats.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Integration tests for NATS server and client services using real TCP sockets.
 */
class NatsServiceIntegrationTest {

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
    void testServerServiceLifecycle() throws Exception {
        var server = NatsServerService.builder().port(0).build();
        server.connect(ctx);
        assertThat(server.getPort()).isGreaterThan(0);
        assertThat(server.getServer()).isNotNull();
        assertThat(server.getServer().isRunning()).isTrue();
        server.disconnect(ctx);
    }

    @Test
    void testClientAndServerServiceOverTcp() throws Exception {
        var server = NatsServerService.builder().port(0).build();
        server.connect(ctx);
        int port = server.getPort();
        assertThat(port).as("server should bind a real port").isGreaterThan(0);

        // Wait for server to be ready
        int retries = 20;
        while (retries-- > 0) {
            if (server.getServer() != null && server.getServer().isRunning()) break;
            Thread.sleep(100);
        }
        assertThat(server.getServer().isRunning()).isTrue();

        // Create and connect client
        var client = NatsService.builder("localhost", port).build();
        client.connect(ctx);
        assertThat(client.getClient()).as("client should connect").isNotNull();
        assertThat(client.getClient().isConnected()).as("client should be connected").isTrue();

        // Publish and receive
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);
        client.subscribe("test.topic", msg -> {
            received.add(msg.dataAsString());
            latch.countDown();
        });

        // Wait for subscription to be processed
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline) {
            var transport = client.getClient().transport();
            if (transport instanceof ssg.legoflow.messaging.nats.transport.PipelineNatsTransport pnt && pnt.getOutboundQueueSize() == 0) break;
            Thread.sleep(50);
        }

        client.getClient().publish("test.topic", "hello");
        assertThat(latch.await(5, TimeUnit.SECONDS))
            .as("client should receive published message")
            .isTrue();
        assertThat(received).containsExactly("hello");

        client.disconnect(ctx);
        server.disconnect(ctx);
    }

    @Test
    void testServerServiceLifecycleOnly() throws Exception {
        var server = NatsServerService.builder().port(0).build();
        server.connect(ctx);
        assertThat(server.getPort()).isGreaterThan(0);
        assertThat(server.getServer()).isNotNull();
        assertThat(server.getServer().isRunning()).isTrue();
        server.disconnect(ctx);
    }

    @Test
    void testMultipleClients() throws Exception {
        var server = NatsServerService.builder().port(0).build();
        server.connect(ctx);
        int port = server.getPort();
        assertThat(port).as("server should bind a real port").isGreaterThan(0);

        // Wait for server to be ready
        int retries = 20;
        while (retries-- > 0) {
            if (server.getServer() != null && server.getServer().isRunning()) break;
            Thread.sleep(100);
        }
        assertThat(server.getServer().isRunning()).isTrue();

        // Create multiple clients and connect them
        var clients = new NatsService[3];
        for (int i = 0; i < 3; i++) {
            clients[i] = NatsService.builder("localhost", port).build();
            clients[i].connect(ctx);
            assertThat(clients[i].getClient()).as("Client " + i + " should connect").isNotNull();
            assertThat(clients[i].getClient().isConnected()).as("Client " + i + " should be connected").isTrue();
        }

        // Wait for all connections to be fully established
        Thread.sleep(200);

        // Each client subscribes and publishes
        @SuppressWarnings("unchecked")
        var receivedMessages = new AtomicReference<String>[] {
            new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>()
        };
        var latches = new CountDownLatch[3];
        for (int i = 0; i < 3; i++) {
            receivedMessages[i] = new AtomicReference<>();
            latches[i] = new CountDownLatch(1);
            final int idx = i;
            clients[i].subscribe("test.topic", msg -> {
                receivedMessages[idx].set(msg.dataAsString());
                latches[idx].countDown();
            });
        }

        // Wait for subscriptions to be processed
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline) {
            boolean allDrained = true;
            for (var c : clients) {
                var transport = c.getClient().transport();
                if (transport instanceof ssg.legoflow.messaging.nats.transport.PipelineNatsTransport pnt && pnt.getOutboundQueueSize() > 0) {
                    allDrained = false;
                    break;
                }
            }
            if (allDrained) break;
            Thread.sleep(50);
        }

        // Each client publishes a message
        for (int i = 0; i < 3; i++) {
            clients[i].getClient().publish("test.topic", "hello-from-client-" + i);
        }

        // Verify all clients received messages
        for (int i = 0; i < 3; i++) {
            assertThat(latches[i].await(10, TimeUnit.SECONDS))
                .as("Client " + i + " should receive message")
                .isTrue();
            assertThat(receivedMessages[i].get()).isEqualTo("hello-from-client-" + i);
        }

        // Cleanup
        for (var c : clients) {
            c.disconnect(ctx);
        }
        server.disconnect(ctx);
    }
}