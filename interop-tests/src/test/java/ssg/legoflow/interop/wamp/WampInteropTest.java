package ssg.legoflow.interop.wamp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampMessageType;
import ssg.legoflow.wamp.core.WampSerializer;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * WAMP interop tests against Crossbar router.
 *
 * <p>Tests: session establishment, pub/sub, RPC call.
 * Requires: docker compose -f interop-tests/docker-compose.wamp.yml up -d
 *
 * @since 0.1.0
 */
@Tag("interop-wamp")
@Disabled("Requires Crossbar router: docker compose -f interop-tests/docker-compose.wamp.yml up -d")
class WampInteropTest {

    private static final Logger LOG = LoggerFactory.getLogger(WampInteropTest.class);
    private static final String WAMP_URI = "ws://localhost:8081/ws";
    private static final String REALM = "realm0";

    private WampWebSocketClient client;
    private WampSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new WampSerializer();
        client = new WampWebSocketClient(WAMP_URI, REALM, serializer);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testSessionEstablishment() throws Exception {
        // Connect and establish session
        client.connect();

        // Should receive WELCOME
        WampMessage msg = client.receive();
        assertThat(msg).isInstanceOf(WampMessage.Welcome.class);
        WampMessage.Welcome welcome = (WampMessage.Welcome) msg;
        assertThat(welcome.sessionId()).isGreaterThan(0);
        LOG.info("Connected with session ID: {}", welcome.sessionId());

        // Clean disconnect
        client.close();
    }

    @Test
    void testPubSub() throws Exception {
        // Connect
        client.connect();
        WampMessage msg = client.receive();
        assertThat(msg).isInstanceOf(WampMessage.Welcome.class);

        // Subscribe to topic
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        AtomicReference<WampMessage> subAckRef = new AtomicReference<>();
        client.onMessage(m -> {
            if (m.type() == WampMessageType.SUBSCRIBED) {
                subAckRef.set(m);
                subscribeLatch.countDown();
            }
        });
        client.subscribe("test.topic");

        // Wait for SUBSCRIBED
        assertThat(subscribeLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(subAckRef.get()).isNotNull();

        // Publish event
        client.publish("test.topic", List.of("hello", 42));

        // Wait for event (should arrive at subscriber, which is us)
        CountDownLatch eventLatch = new CountDownLatch(1);
        AtomicReference<WampMessage> eventRef = new AtomicReference<>();
        client.onMessage(m -> {
            if (m.type() == WampMessageType.EVENT) {
                eventRef.set(m);
                eventLatch.countDown();
            }
        });

        // Publish again to trigger event
        client.publish("test.topic", List.of("world", 99));

        // Wait for EVENT
        assertThat(eventLatch.await(5, TimeUnit.SECONDS)).isTrue();
        WampMessage.Event event = (WampMessage.Event) eventRef.get();
        assertThat(event.args()).containsExactly("world", 99);

        // Unsubscribe
        client.unsubscribe(event.subscriptionId());

        client.close();
    }

    @Test
    void testRpcCall() throws Exception {
        // Connect
        client.connect();
        WampMessage msg = client.receive();
        assertThat(msg).isInstanceOf(WampMessage.Welcome.class);

        // Register procedure
        CountDownLatch registerLatch = new CountDownLatch(1);
        AtomicReference<WampMessage> regAckRef = new AtomicReference<>();
        client.onMessage(m -> {
            if (m.type() == WampMessageType.REGISTERED) {
                regAckRef.set(m);
                registerLatch.countDown();
            }
        });
        client.register("com.example.add");

        // Wait for REGISTERED
        assertThat(registerLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(regAckRef.get()).isNotNull();

        // Call procedure
        CountDownLatch callLatch = new CountDownLatch(1);
        AtomicReference<WampMessage> resultRef = new AtomicReference<>();
        client.onMessage(m -> {
            if (m.type() == WampMessageType.RESULT) {
                resultRef.set(m);
                callLatch.countDown();
            }
        });

        client.call("com.example.add", List.of(10, 20));

        // Wait for RESULT
        assertThat(callLatch.await(5, TimeUnit.SECONDS)).isTrue();
        WampMessage.Result result = (WampMessage.Result) resultRef.get();
        assertThat(result.args()).containsExactly(30);

        // Unregister
        client.unregister(((WampMessage.Registered) regAckRef.get()).registrationId());

        client.close();
    }

    @Test
    void testMultiRealm() throws Exception {
        // Connect to default realm
        client.connect();
        WampMessage msg = client.receive();
        assertThat(msg).isInstanceOf(WampMessage.Welcome.class);

        // Verify we're in the correct realm
        WampMessage.Welcome welcome = (WampMessage.Welcome) msg;
        assertThat(welcome.details()).containsKey("realm");
        assertThat(welcome.details().get("realm")).isEqualTo(REALM);

        client.close();
    }
}
