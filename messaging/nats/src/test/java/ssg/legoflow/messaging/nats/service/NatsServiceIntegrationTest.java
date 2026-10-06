package ssg.legoflow.messaging.nats.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import java.time.Duration;
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
        assertThat(client.getClient()).as("client should be connected").isNotNull();
        assertThat(client.getClient().isConnected()).isTrue();

        // Pub/sub over real TCP.
        var latch = new CountDownLatch(1);
        var got = new AtomicReference<String>();
        client.subscribe("test.topic", msg -> {
            got.set(msg.dataAsString());
            latch.countDown();
        });
        // Give the SUB a moment to reach the server, then publish.
        Thread.sleep(100);
        client.getClient().publish("test.topic", "hello-tcp");

        assertThat(latch.await(5, TimeUnit.SECONDS)).as("message should arrive over TCP").isTrue();
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
}
