package ssg.legoflow.messaging.nats.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import ssg.legoflow.messaging.nats.transport.PipelineNatsTransport;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration test: NATS client service ↔ server service over real TCP through
 * the {@link SelectableChannelManager} (mirrors the AMQP {@code AmqpServiceIntegrationTest}).
 *
 * <p>Exercises the full production pipeline: SocketChannel → SelectableChannelManager →
 * TcpDataChannel → ChannelPipeline → {@link ssg.legoflow.messaging.nats.transport.PipelineNatsTransport}
 * → NATS protocol layer. The protocol core itself never touches sockets — this test only
 * drives the service layer, which owns the (manager-managed) channels.
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
    void testClientAndServerServiceOverTcp() throws Exception {
        var server = NatsServerService.builder().port(0).build();
        server.connect(ctx);
        int port = server.getPort();
        assertThat(port).as("server should bind a real port").isGreaterThan(0);

        // Poll for the selector to register the server channel (service-layer sync point).
        int retries = 20;
        while (retries-- > 0) {
            if (server.getServer() != null && server.getServer().isRunning()) break;
            Thread.sleep(100);
        }

        var client = NatsService.builder("localhost", port).build();
        client.connect(ctx);

        // Wait for the client's handshake data to be flushed to the server
        // (PipelineByteTransport is enqueue-only; wait for outbound queue to drain)
        long handshakeDeadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < handshakeDeadline) {
            var transport = client.getClient().transport();
            if (transport instanceof PipelineNatsTransport pnt && pnt.getOutboundQueueSize() == 0) break;
            Thread.sleep(50);
        }

        assertThat(client.getClient()).as("client should be connected").isNotNull();
        assertThat(client.getClient().isConnected()).isTrue();

        // Pub/sub over real TCP.
        var latch = new CountDownLatch(1);
        var got = new AtomicReference<String>();
        client.subscribe("test.topic", msg -> {
            got.set(msg.dataAsString());
            latch.countDown();
        });
        // Wait for the SUB to be fully processed by the server before publishing.
        // With enqueue-only transport, the SUB must be written and processed before
        // the PUB arrives; poll until the outbound queue is drained (SUB written).
        long subDeadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < subDeadline) {
            var transport = client.getClient().transport();
            if (transport instanceof PipelineNatsTransport pnt && pnt.getOutboundQueueSize() == 0) break;
            Thread.sleep(50);
        }
        client.getClient().publish("test.topic", "hello-tcp");

        // Use 10-second timeout to account for TCP round-trip latency
        assertThat(latch.await(10, TimeUnit.SECONDS)).as("message should arrive over TCP").isTrue();
        assertThat(got.get()).isEqualTo("hello-tcp");

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

        // Each client subscribes and publishes
        var receivedMessages = new java.util.ArrayList<java.util.concurrent.atomic.AtomicReference<String>>(3);
        var latches = new CountDownLatch[3];
        for (int i = 0; i < 3; i++) {
            receivedMessages.add(new java.util.concurrent.atomic.AtomicReference<>());
            latches[i] = new CountDownLatch(1);
            final int idx = i;
            clients[i].subscribe("test.topic", msg -> {
                receivedMessages.get(idx).set(msg.dataAsString());
                latches[idx].countDown();
            });
        }

        // Wait for subscriptions to be processed
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline) {
            boolean allDrained = true;
            for (var c : clients) {
                var transport = c.getClient().transport();
                if (transport instanceof PipelineNatsTransport pnt && pnt.getOutboundQueueSize() > 0) {
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
            assertThat(receivedMessages.get(i).get()).isEqualTo("hello-from-client-" + i);
        }

        // Cleanup
        for (var c : clients) {
            c.disconnect(ctx);
        }
        server.disconnect(ctx);
    }
}
