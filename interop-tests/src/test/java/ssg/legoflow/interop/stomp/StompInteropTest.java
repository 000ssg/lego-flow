package ssg.legoflow.interop.stomp;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.stomp.core.StompClient;
import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.client.service.StompClientService;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow STOMP client → RabbitMQ STOMP plugin.
 *
 * <p>Configuration via system properties:
 *   interop.stomp.host (default: localhost)
 *   interop.stomp.port (default: 61613)
 *   interop.stomp.login (default: guest)
 *   interop.stomp.passcode (default: guest)
 */
@Tag("messaging-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StompInteropTest {

    private final String host = System.getProperty("interop.stomp.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.stomp.port", "61613"));
    private final String login = System.getProperty("interop.stomp.login", "guest");
    private final String passcode = System.getProperty("interop.stomp.passcode", "guest");

    private static SelectableChannelManager channelManager;
    private StompClientService service;

    @BeforeAll
    static void setUpManager() {
        channelManager = new SelectableChannelManager(null);
        channelManager.startEventLoop();
    }

    @AfterAll
    static void tearDownManager() throws Exception {
        if (channelManager != null) {
            channelManager.stopEventLoop();
            channelManager.close();
        }
    }

    @BeforeEach
    void connect() {
        service = StompClientService.builder(host, port)
                .name("interop-stomp-" + System.identityHashCode(this))
                .login(login)
                .passcode(passcode)
                .timeoutMs(10000)
                .build();
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.setAttribute("channelManager", channelManager);
        service.connect(ctx);
    }

    @AfterEach
    void disconnect() {
        if (service != null) {
            var ctx = new DefaultServiceContext(ServiceUser.anonymous());
            ctx.setAttribute("channelManager", channelManager);
            service.disconnect(ctx);
        }
    }

    @Test
    void testConnection() {
        assertThat(service.getClient()).isNotNull();
    }

    @Test
    void testSendAndReceive() throws Exception {
        String testQueue = "/queue/interop-stomp-test-" + System.identityHashCode(this);
        String expectedMessage = "hello-stomp";
        var stompClient = service.getClient();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StompFrame> received = new AtomicReference<>();

        stompClient.subscribe(testQueue, frame -> {
            received.set(frame);
            latch.countDown();
        });

        stompClient.send(testQueue, expectedMessage, null);

        assertThat(latch.await(5, TimeUnit.SECONDS)).as("should receive message").isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().bodyAsText()).isEqualTo(expectedMessage);
    }

    @Test
    void testMultipleMessages() throws Exception {
        String testQueue = "/queue/interop-stomp-multi-" + System.identityHashCode(this);
        var stompClient = service.getClient();
        CountDownLatch latch = new CountDownLatch(5);

        stompClient.subscribe(testQueue, frame -> latch.countDown());

        for (int i = 0; i < 5; i++) {
            stompClient.send(testQueue, "msg-" + i, null);
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).as("should receive 5 messages").isTrue();
    }
}
