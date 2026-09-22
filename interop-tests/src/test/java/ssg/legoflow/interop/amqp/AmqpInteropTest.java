package ssg.legoflow.interop.amqp;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.BrokerMode;
import ssg.legoflow.messaging.amqp.client.ClientConfig;
import ssg.legoflow.messaging.amqp.client.service.AmqpClientService;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.link.ReceiverLink;
import ssg.legoflow.messaging.amqp.link.SenderLink;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.types.AmqpType;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow AMQP 1.0 client ↔ Apache ActiveMQ Artemis.
 *
 * <h3>Verified Test Context — DO NOT GUESS</h3>
 * <p>
 * BROKER: Apache ActiveMQ Artemis (apache/artemis:latest-alpine)
 * CONTAINER: {@code docker ps | grep artemis-test}
 * PORT: 5672 (standard AMQP). Docker maps -p 5672:5672.
 * CREDENTIALS: artemis / guest. The docker entrypoint creates ONE broker user from
 * {@code ARTEMIS_USER} (default "artemis") + {@code ARTEMIS_PASSWORD} (default "guest").
 * NOTE: {@code ARTEMIS_USERNAME} is NOT read by the entrypoint, and "guest" is NOT a user —
 * using it fails SASL with code 1.
 * <p>
 * <b>VERIFY BEFORE EACH RUN:</b> {@code docker ps | grep artemis-test} shows
 * {@code Up ... 0.0.0.0:5672->5672/tcp}. If not — start it.
 * <pre>{@code
 * docker run -d --name artemis-test -p 5672:5672 -p 8161:8161 \
 *   -e ARTEMIS_PASSWORD=guest \
 *   apache/artemis:latest-alpine run
 * }</pre>
 *
 * <h3>Artemis Wire Sequence (proto-3 / SASL-first) — VERIFIED 2026-09-10 against live broker</h3>
 * <ol>
 *   <li>TCP connect — server sends <b>nothing</b> (waits for client header)</li>
 *   <li>Client → Artemis: {@code AMQP\x03\x01\x00\x00} (SASL_HEADER)</li>
 *   <li>Artemis → client: SASL_HEADER echo + sasl-mechanisms frame {@code [PLAIN, ANONYMOUS]}</li>
 *   <li>Client → Artemis: sasl-init PLAIN {@code \0artemis\0guest}</li>
 *   <li>Artemis → client: sasl-outcome code 0 (ok) on success</li>
 *   <li>Client → Artemis: {@code AMQP\x00\x01\x00\x00} (AMQP 1.0 header); Artemis echoes it</li>
 *   <li>OPEN → OPEN, then BEGIN/ATTACH...</li>
 * </ol>
 * <p><b>proto-0 is NOT accepted</b> on the 5672 acceptor (protocols=AMQP): sending the raw
 * AMQP 1.0 header first makes Artemis reply with SASL_HEADER and <b>close</b>.
 * An earlier "Artemis sends a 0-9-1 header on connect" note was a misread — those 8 bytes
 * were the <i>echo of our own header</i>.
 *
 * <h3>Known failures and root causes (final results, keep updated)</h3>
 * <ul>
 *   <li>{@code Transport closed} on first read = {@code proto0Accepted=true} sent the AMQP 1.0
 *       header first; Artemis replies with SASL_HEADER and closes. Fix: SASL-first
 *       (remove ARTEMIS from the proto0Accepted list in ClientConfig.brokerMode).</li>
 *   <li>{@code SASL authentication failed with code: 1} = bad credentials. The realm only has
 *       the entrypoint-created user (default artemis/guest). "guest/guest" is NOT valid.</li>
 *   <li>{@code AMQP header mismatch value 1, expecting 0} in broker log = client sent
 *       {@code AMQP\x00\x01} where the server expected the SASL header (proto-0 vs proto-3).</li>
 *   <li>{@code amqp:decode-error: the next-outgoing-id / incoming-window field is mandatory}
 *       = Proton (Artemis) requires ALL FOUR session window fields (next-incoming-id,
 *       incoming-window, next-outgoing-id, outgoing-window) in every FLOW frame. The spec's
 *       link-scoped variant (session fields null) is NOT accepted. Fix: ReceiverLink.issueCredit
 *       always attaches the full session state.</li>
 *   <li>Connecting to the wrong container = always verify {@code docker ps} before running.</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * Read from {@code interop-tests/pom.xml} system properties. Defaults:
 * host=localhost, port=5672, username=artemis, password=guest, broker=ARTEMIS.
 */
@Tag("interop-messaging-core")
class AmqpInteropTest {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpInteropTest.class);

    enum Broker { ARTEMIS, RABBITMQ }

    // VERIFIED: Artemis on standard port 5672. Container: artemis-test
    private final String host = System.getProperty("interop.amqp.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.amqp.port", "5672"));
    // Verified against the live container (2026-09-10): the broker realm has ONE user
    // created by the docker entrypoint from ARTEMIS_USER (default "artemis") +
    // ARTEMIS_PASSWORD (default "guest"). "guest" is NOT a user — that was the
    // "SASL authentication failed with code: 1" root cause. Use artemis/guest.
    private final String username = System.getProperty("interop.amqp.username", "artemis");
    private final String password = System.getProperty("interop.amqp.password", "guest");
    private final Broker broker = Broker.valueOf(System.getProperty("interop.amqp.broker", "ARTEMIS"));

    private BrokerMode brokerMode() {
        return switch (broker) {
            case RABBITMQ -> BrokerMode.RABBITMQ;
            case ARTEMIS -> BrokerMode.ARTEMIS;
        };
    }

    // Shared event loop only — no shared protocol state
    private static SelectableChannelManager channelManager;

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

    /** Unique queue name for each test to avoid cross-test interference. */
    private String testQueue() {
        return "interop-" + broker.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private AmqpClientService connectClient(String nameSuffix) {
        var builder = AmqpClientService.builder(host, port)
                .name("interop-" + nameSuffix)
                .containerId("interop-client-" + nameSuffix + "-" + UUID.randomUUID().toString().substring(0, 8))
                .timeout(Duration.ofSeconds(10))
                .brokerMode(brokerMode());
        var svc = builder.username(username).password(password).build();
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.setAttribute("channelManager", channelManager);
        svc.connect(ctx);
        LOG.info("Connected to {} broker at {}:{} ", broker, host, port);
        return svc;
    }

    private void disconnectClient(AmqpClientService svc) {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.setAttribute("channelManager", channelManager);
        svc.disconnect(ctx);
    }

    @Test
    void testConnection() throws Exception {
        var svc = connectClient("conn");
        try {
            assertThat(svc.getClient()).as("client created").isNotNull();
            assertThat(svc.getClient().isConnected()).as("is connected").isTrue();
        } finally {
            disconnectClient(svc);
        }
    }

    @Test
    void testAmqpSession() throws Exception {
        var svc = connectClient("session");
        try {
            var client = svc.getClient();
            assertThat(client).as("client exists").isNotNull();
            AmqpSession session = client.createSession();
            assertThat(session).as("session created").isNotNull();
        } finally {
            disconnectClient(svc);
        }
    }

    @Test
    void testSendAndReceiveMessage() throws Exception {
        var svc = connectClient("sendrecv");
        try {
            String testQueue = testQueue();
            String testMessage = "interop-amqp-test";

            var client = svc.getClient();
            AmqpSession session = client.createSession();

            String effectiveQueue = brokerMode().formatAddress(testQueue);
            LOG.info("Test queue: {} (effective: {})", testQueue, effectiveQueue);

            SenderLink sender = client.createSender(session, "interop-sender", effectiveQueue);
            ReceiverLink receiver = client.createReceiver(session, "interop-receiver", effectiveQueue);

            // Build message before starting receive thread — avoids frame-stealing race
            AmqpMessage message = new AmqpMessage();
            message.bodyValue(new AmqpType.AmqpString(testMessage));

            // Start receive thread AFTER setup — no frame-stealing possible
            var latch = new CountDownLatch(1);
            AtomicReference<Delivery> received = new AtomicReference<>();
            Thread receiveThread = new Thread(() -> {
                try {
                    Delivery delivery = receiver.receive(10, TimeUnit.SECONDS);
                    System.out.println("[RECEIVE] delivery=" + delivery);
                    if (delivery != null) {
                        received.set(delivery);
                        receiver.accept(delivery.deliveryId());
                    } else {
                        System.out.println("[RECEIVE] delivery was null (timeout)");
                    }
                    latch.countDown();
                } catch (Exception e) {
                    System.out.println("[RECEIVE] exception: " + e.getMessage());
                    e.printStackTrace(System.out);
                    latch.countDown();
                }
            });
            receiveThread.setDaemon(true);
            receiveThread.start();

            // Send at-most-once
            LOG.info("Sending message to queue: {}", effectiveQueue);
            Delivery sent = sender.send(message, true);
            LOG.info("Send result: {}", sent);
            assertThat(sent).as("send should succeed").isNotNull();

            assertThat(latch.await(10, TimeUnit.SECONDS)).as("should receive message").isTrue();
            receiveThread.join(5000);

            assertThat(received.get()).as("received delivery").isNotNull();
            Object body = received.get().message().body();
            if (body instanceof AmqpType.AmqpString str) {
                assertThat(str.value()).isEqualTo(testMessage);
            }
        } finally {
            disconnectClient(svc);
        }
    }

    @Test
    void testMultipleMessages() throws Exception {
        var svc = connectClient("multi");
        try {
            var client = svc.getClient();
            AmqpSession session = client.createSession();
            String testQueue = testQueue();
            String effectiveQueue = brokerMode().formatAddress(testQueue);

            SenderLink sender = client.createSender(session, "multi-sender", effectiveQueue);
            ReceiverLink receiver = client.createReceiver(session, "multi-receiver", effectiveQueue);

            // Send all messages first, then receive — no frame-stealing race
            for (int i = 0; i < 5; i++) {
                AmqpMessage msg = new AmqpMessage();
                msg.bodyValue(new AmqpType.AmqpString("msg-" + i));
                sender.send(msg, false);
            }

            for (int i = 0; i < 5; i++) {
                Delivery delivery = receiver.receive(3, TimeUnit.SECONDS);
                if (delivery != null) {
                    receiver.accept(delivery.deliveryId());
                }
            }
        } finally {
            disconnectClient(svc);
        }
    }

    @Test
    void testClientConfigBuilder() {
        var config = ClientConfig.builder()
                .host("test")
                .port(5673)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        assertThat(config.host()).isEqualTo("test");
        assertThat(config.port()).isEqualTo(5673);
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void testCloseGracefully() throws Exception {
        var svc = connectClient("close");
        try {
            assertThat(svc.getClient().isConnected()).as("connected before close").isTrue();
        } finally {
            disconnectClient(svc);
            assertThat(svc.getClient().isConnected()).as("disconnected after close").isFalse();
        }
    }
}
