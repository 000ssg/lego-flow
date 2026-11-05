package ssg.legoflow.messaging.nats.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for NATS server and client services.
 *
 * <p>Connection tests use the full TCP pipeline through {@link SelectableChannelManager}.
 * Pub/sub tests use in-memory transports to verify the protocol layer.
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
    void testPubSubViaInMemoryTransport() throws Exception {
        // Use in-memory transport to test pub/sub protocol directly
        var server = new ssg.legoflow.messaging.nats.server.NatsServer();
        server.start();

        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = ssg.legoflow.messaging.nats.server.InMemoryNats.client(server);
             var pub = ssg.legoflow.messaging.nats.server.InMemoryNats.client(server)) {
            sub.subscribe("test.topic", msg -> {
                received.add(msg.dataAsString());
                latch.countDown();
            });
            Thread.sleep(50);

            pub.publish("test.topic", "hello");
            assertThat(latch.await(3, TimeUnit.SECONDS))
                    .as("client should receive published message")
                    .isTrue();
            assertThat(received).containsExactly("hello");
        } finally {
            server.close();
        }
    }

    @Test
    void testPubSubMultipleMessages() throws Exception {
        var server = new ssg.legoflow.messaging.nats.server.NatsServer();
        server.start();

        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(3);

        try (var sub = ssg.legoflow.messaging.nats.server.InMemoryNats.client(server);
             var pub = ssg.legoflow.messaging.nats.server.InMemoryNats.client(server)) {
            sub.subscribe("events", msg -> {
                received.add(msg.dataAsString());
                latch.countDown();
            });
            Thread.sleep(50);

            pub.publish("events", "e1");
            pub.publish("events", "e2");
            pub.publish("events", "e3");
            assertThat(latch.await(3, TimeUnit.SECONDS))
                    .as("client should receive all messages")
                    .isTrue();
            assertThat(received).containsExactly("e1", "e2", "e3");
        } finally {
            server.close();
        }
    }

    @Test
    void testPubSubWildcard() throws Exception {
        var server = new ssg.legoflow.messaging.nats.server.NatsServer();
        server.start();

        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(2);

        try (var sub = ssg.legoflow.messaging.nats.server.InMemoryNats.client(server);
             var pub = ssg.legoflow.messaging.nats.server.InMemoryNats.client(server)) {
            sub.subscribe("events.>", msg -> {
                received.add(msg.dataAsString());
                latch.countDown();
            });
            Thread.sleep(50);

            pub.publish("events.foo", "f1");
            pub.publish("events.bar", "f2");
            assertThat(latch.await(3, TimeUnit.SECONDS))
                    .as("client should receive wildcard matches")
                    .isTrue();
            assertThat(received).containsExactly("f1", "f2");
        } finally {
            server.close();
        }
    }
}
