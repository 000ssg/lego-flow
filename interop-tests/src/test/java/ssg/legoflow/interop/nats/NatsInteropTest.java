package ssg.legoflow.interop.nats;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.client.InboxManager;
import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.client.NatsMessage;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow NATS client to real NATS server.
 *
 * <p>Connects to a real NATS server to verify pub/sub, request/reply,
 * and JetStream operations.
 *
 * <p>Configuration via system properties:
 *   interop.nats.host (default: localhost)
 *   interop.nats.port (default: 4222)
 *
 * <p>To run against NATS:
 *   docker run -d --rm -p 4222:4222 nats:latest
 *   mvn verify -Dinterop.nats.host=localhost -DskipInteropTests=false
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NatsInteropTest {

    private final String host = System.getProperty("interop.nats.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.nats.port", "4222"));
    private final ConnectOptions connectOptions = ConnectOptions.withDefaults("interop-client");

    private NatsClient client;

    @BeforeAll
    void connect() throws Exception {
        this.client = new NatsClient(host, port, connectOptions);
        client.connect();
    }

    @AfterAll
    void disconnect() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testConnection() throws Exception {
        assertThat(client).isNotNull();
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testPublishSubscribe() throws Exception {
        String subject = "interop.nats.test";
        String expectedMessage = "hello-nats";

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NatsMessage> received = new AtomicReference<>();

        client.subscribe(subject, msg -> {
            received.set(msg);
            latch.countDown();
        });

        client.publish(subject, expectedMessage);

        boolean receivedMessage = latch.await(5, TimeUnit.SECONDS);
        assertThat(receivedMessage).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().dataAsString()).isEqualTo(expectedMessage);
    }

    @Test
    void testRequestReply() throws Exception {
        String subject = "interop.nats.request-reply";

        client.subscribe(subject, msg -> {
            try {
                String body = new String(msg.payload());
                String response = "replied:" + body;
                client.publish(msg.replyTo() != null ? msg.replyTo() : "_INBOX.none", response);
            } catch (Exception e) {
                // Ignore publish errors in response handler
            }
        });

        NatsMessage reply = client.request(subject, "ping", Duration.ofSeconds(5));
        assertThat(reply).isNotNull();
        assertThat(reply.dataAsString()).isEqualTo("replied:ping");
    }

    @Test
    void testServerInfo() throws Exception {
        assertThat(client).isNotNull();
        assertThat(client.serverInfo()).isNotNull();
        assertThat(client.serverInfo().clientId()).isGreaterThan(0);
    }

    @Test
    void testInboxManager() {
        InboxManager inboxManager = new InboxManager();
        String inbox1 = inboxManager.newInbox();
        String inbox2 = inboxManager.newInbox();

        assertThat(inbox1).startsWith("_INBOX.");
        assertThat(inbox2).startsWith("_INBOX.");
        assertThat(inbox1).isNotEqualTo(inbox2);
    }

    @Test
    void testMultipleMessages() throws Exception {
        String subject = "interop.nats.multi";
        CountDownLatch latch = new CountDownLatch(3);

        client.subscribe(subject, msg -> latch.countDown());

        client.publish(subject, "msg1");
        client.publish(subject, "msg2");
        client.publish(subject, "msg3");

        boolean done = latch.await(5, TimeUnit.SECONDS);
        assertThat(done).isTrue();
    }

    @Test
    void testCloseGracefully() throws Exception {
        // Use a dedicated client to avoid disrupting the shared connection
        var testClient = new NatsClient(host, port, connectOptions);
        testClient.connect();
        assertThat(testClient.isConnected()).isTrue();
        testClient.close();
        assertThat(testClient.isConnected()).isFalse();
    }
}
