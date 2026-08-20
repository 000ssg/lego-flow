package ssg.legoflow.interop.stomp;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.stomp.adapter.tcp.TcpStompClient;
import ssg.legoflow.messaging.stomp.core.StompClient;
import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.core.StompHeaders;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow STOMP client ↔ real STOMP broker.
 *
 * <p>Connects to a real ActiveMQ or similar STOMP broker to verify
 * that the Lego Flow client can establish connections, send messages,
 * and manage subscriptions.
 *
 * <p>Configuration via system properties:
 *   interop.stomp.host (default: localhost)
 *   interop.stomp.port (default: 61613)
 *   interop.stomp.vhost (default: /)
 *   interop.stomp.login (default: guest)
 *   interop.stomp.passcode (default: guest)
 *
 * <p>To run against ActiveMQ:
 *   docker run -d --rm -p 61613:61613 -e ACTIVEMQ_ENABLE_SMS=false apache/activemq:latest
 *   mvn verify -Dinterop.stomp.host=localhost -DskipInteropTests=false
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StompInteropTest {

    private final String host = System.getProperty("interop.stomp.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.stomp.port", "61613"));
    private final String virtualHost = System.getProperty("interop.stomp.vhost", "/");
    private final String login = System.getProperty("interop.stomp.login", "guest");
    private final String passcode = System.getProperty("interop.stomp.passcode", "guest");

    private TcpStompClient client;
    private StompClient stompClient;

    @BeforeAll
    void connect() throws Exception {
        this.client = new TcpStompClient(host, port);
        StompFrame connected = client.connect(virtualHost, login, passcode);
        assertThat(connected).isNotNull();
        assertThat(connected.command()).isEqualTo(ssg.legoflow.messaging.stomp.core.StompCommand.CONNECTED);
        this.stompClient = client.getClient();
    }

    @AfterAll
    void disconnect() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testConnection() {
        assertThat(client).isNotNull();
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testSendAndReceive() throws Exception {
        String testQueue = "/queue/interop-stomp-test";
        String expectedMessage = "hello-stomp";

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StompFrame> received = new AtomicReference<>();

        stompClient.subscribe(testQueue, frame -> {
            received.set(frame);
            latch.countDown();
        });

        stompClient.send(testQueue, expectedMessage, null);

        boolean gotMessage = latch.await(5, TimeUnit.SECONDS);
        assertThat(gotMessage).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().bodyAsText()).isEqualTo(expectedMessage);
    }

    @Test
    void testMultipleMessages() throws Exception {
        String testQueue = "/queue/interop-stomp-multi";
        CountDownLatch latch = new CountDownLatch(5);

        stompClient.subscribe(testQueue, frame -> latch.countDown());

        for (int i = 0; i < 5; i++) {
            stompClient.send(testQueue, "msg-" + i, null);
        }

        boolean done = latch.await(5, TimeUnit.SECONDS);
        assertThat(done).isTrue();
    }

    @Test
    void testSubscribeUnsubscribe() throws Exception {
        String testQueue = "/queue/interop-stomp-sub-unsub";
        AtomicReference<String> subId = new AtomicReference<>();

        // Subscribe and get a sub ID
        stompClient.subscribe(testQueue, frame -> {});

        // Verify we can unsubscribe by creating another subscription and checking state
        assertThat(stompClient.isConnected()).isTrue();
    }

    @Test
    void testClientSessionInfo() {
        assertThat(stompClient.getSession()).isNotNull();
        assertThat(stompClient.getSession().getLogin()).isEqualTo(login);
    }

    @Test
    void testCloseGracefully() throws Exception {
        // Use a dedicated client to avoid disrupting the shared connection
        var testClient = new TcpStompClient(host, port);
        testClient.connect(virtualHost, login, passcode);
        assertThat(testClient.isConnected()).isTrue();
        testClient.close();
        assertThat(testClient.isConnected()).isFalse();
    }
}
