package ssg.legoflow.messaging.stomp.server.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import ssg.legoflow.messaging.stomp.client.service.StompClientService;
import ssg.legoflow.messaging.stomp.core.StompEventListener;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Multi-client integration test for STOMP server.
 */
class StompServerServiceTest {

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
    void testBuilderCreatesService() {
        var service = StompServerService.builder().port(61613).build();
        assertThat(service).isNotNull();
    }

    @Test
    void testInitialStateIsDisconnected() {
        var service = StompServerService.builder().port(0).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test
    void testDisconnectBeforeConnectDoesNotThrow() {
        var service = StompServerService.builder().port(0).build();
        try {
            service.disconnect(service.getServiceContext());
        } catch (Exception e) {
            fail("should not throw: " + e);
        }
    }

    @Test
    void testBuilderWithCustomPriority() {
        var service = StompServerService.builder().port(61613)
                .priority(50)
                .build();
        assertThat(service.getPriority()).isEqualTo(50);
    }

    @Test
    void testChannelHandlerCanBeCreated() {
        var service = StompServerService.builder().port(61613).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test
    void testGetBrokerIsNullBeforeConnect() {
        var service = StompServerService.builder().port(0).build();
        assertThat(service.getBroker()).isNull();
    }

    @Test
    void testMultipleClients() throws Exception {
        var server = StompServerService.builder().port(0).build();
        server.connect(ctx);
        int port = server.port();
        assertThat(port).as("server should bind a real port").isGreaterThan(0);

        // Wait for server to be ready
        int retries = 20;
        while (retries-- > 0) {
            if (server.getBroker() != null) break;
            Thread.sleep(100);
        }
        assertThat(server.getBroker()).isNotNull();

        // Create multiple clients and connect them
        var clients = new StompClientService[3];
        for (int i = 0; i < 3; i++) {
            clients[i] = StompClientService.builder("localhost", port)
                    .name("stomp-client-" + i)
                    .build();
            clients[i].connect(ctx);
            assertThat(clients[i].getClient()).as("Client " + i + " should connect").isNotNull();
            assertThat(clients[i].isConnected()).as("Client " + i + " should be connected").isTrue();
        }

        // Server collects messages from all clients
        var serverReceived = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(3);
        server.getBroker().setListener((event, sessionId, detail) -> {
            if (event == StompEventListener.EventType.MESSAGE_DELIVERED) {
                serverReceived.add(detail);
                latch.countDown();
            }
        });

        // Each client sends a message
        for (int i = 0; i < 3; i++) {
            clients[i].getClient().connect("localhost", "user" + i, "pass" + i, port, 0);
            clients[i].getClient().send("/queue/test", "hello-from-client-" + i, "text/plain");
        }

        // Verify server received all messages
        assertThat(latch.await(10, TimeUnit.SECONDS))
            .as("Server should receive all messages")
            .isTrue();
        assertThat(serverReceived).hasSize(3);

        // Cleanup
        for (var c : clients) {
            c.disconnect(ctx);
        }
        server.disconnect(ctx);
    }
}
