package ssg.legoflow.messaging.stomp.server.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import ssg.legoflow.messaging.stomp.client.service.StompClientService;

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

        // Create multiple clients and connect them with small delays to avoid race conditions
        var clients = new StompClientService[3];
        for (int i = 0; i < 3; i++) {
            clients[i] = StompClientService.builder("localhost", port)
                    .name("stomp-client-" + i)
                    .build();
            clients[i].connect(ctx);
            assertThat(clients[i].getClient()).as("Client " + i + " should connect").isNotNull();
            assertThat(clients[i].isConnected()).as("Client " + i + " should be connected").isTrue();
            // Small delay between connections to avoid race conditions
            if (i < 2) Thread.sleep(50);
        }

        // Cleanup
        for (var c : clients) {
            c.disconnect(ctx);
        }
        server.disconnect(ctx);
    }
}
