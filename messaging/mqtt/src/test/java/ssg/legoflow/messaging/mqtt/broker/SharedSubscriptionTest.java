package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.*;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MQTT 5.0 shared subscriptions (${@code $share/group/topic}).
 *
 * <p>Sync rules (per doc/AGENTS_test_patterns.md): subscribe() returns a
 * {@code CompletableFuture<SubAckPacket>} — await it, never Thread.sleep,
 * before publishing. Delivery assertions poll with a deadline.
 *
 * <p><b>Shared-subscription semantics:</b> a shared message is delivered to
 * exactly ONE group member, chosen by round-robin
 * ({@code SharedSubscriptionRegistry.selectNext}). A test must NOT assert a
 * <i>specific</i> member receives it — only that exactly one member does.
 *
 * @since 0.2.0
 */
class SharedSubscriptionTest {

    private MqttBroker broker;

    @BeforeEach
    void setUp() {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        broker.close();
    }

    @Test
    void testSharedSubscriptionDeliversToOneMember() throws Exception {
        var pub = createClient("pub");
        var sub1 = createClient("sub1");
        var sub2 = createClient("sub2");
        var sub3 = createClient("sub3");

        pub.connect().get(5, TimeUnit.SECONDS);
        sub1.connect().get(5, TimeUnit.SECONDS);
        sub2.connect().get(5, TimeUnit.SECONDS);
        sub3.connect().get(5, TimeUnit.SECONDS);

        var latch1 = new CountDownLatch(1);
        var latch2 = new CountDownLatch(1);
        var latch3 = new CountDownLatch(1);

        sub1.subscribe("$share/group/sensors/+", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> latch1.countDown()).get(5, TimeUnit.SECONDS);
        sub2.subscribe("$share/group/sensors/+", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> latch2.countDown()).get(5, TimeUnit.SECONDS);
        sub3.subscribe("$share/group/sensors/+", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> latch3.countDown()).get(5, TimeUnit.SECONDS);

        pub.publish("sensors/temp", "42".getBytes(StandardCharsets.UTF_8), QoS.AT_MOST_ONCE, false);

        // Round-robin picks exactly one member — poll until any of the three fires.
        assertEventually(() -> latch1.getCount() == 0 || latch2.getCount() == 0 || latch3.getCount() == 0,
                "shared message delivered to one group member");

        int receivedCount = 0;
        if (latch1.getCount() == 0) receivedCount++;
        if (latch2.getCount() == 0) receivedCount++;
        if (latch3.getCount() == 0) receivedCount++;
        assertThat(receivedCount).as("exactly one member receives a shared message")
                .isEqualTo(1);

        pub.disconnect();
        sub1.disconnect();
        sub2.disconnect();
        sub3.disconnect();
    }

    @Test
    void testRoundRobinDistribution() throws Exception {
        var pub = createClient("pub");
        var sub1 = createClient("sub1");
        var sub2 = createClient("sub2");
        var sub3 = createClient("sub3");

        pub.connect().get(5, TimeUnit.SECONDS);
        sub1.connect().get(5, TimeUnit.SECONDS);
        sub2.connect().get(5, TimeUnit.SECONDS);
        sub3.connect().get(5, TimeUnit.SECONDS);

        var msgs1 = new CopyOnWriteArrayList<String>();
        var msgs2 = new CopyOnWriteArrayList<String>();
        var msgs3 = new CopyOnWriteArrayList<String>();

        sub1.subscribe("$share/group/data/#", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> msgs1.add(new String(p, StandardCharsets.UTF_8)))
                .get(5, TimeUnit.SECONDS);
        sub2.subscribe("$share/group/data/#", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> msgs2.add(new String(p, StandardCharsets.UTF_8)))
                .get(5, TimeUnit.SECONDS);
        sub3.subscribe("$share/group/data/#", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> msgs3.add(new String(p, StandardCharsets.UTF_8)))
                .get(5, TimeUnit.SECONDS);

        // Publish 6 messages — round-robin across 3 members delivers each exactly 2,
        // but the distribution order is an implementation detail: assert only the
        // total, that all members participated, and that no member got more than one.
        for (int i = 0; i < 6; i++) {
            pub.publish("data/item", ("msg-" + i).getBytes(StandardCharsets.UTF_8),
                    QoS.AT_MOST_ONCE, false);
        }

        assertEventually(
                () -> msgs1.size() + msgs2.size() + msgs3.size() == 6,
                "all 6 shared messages delivered");
        assertThat(msgs1).as("sub1 participates in the shared group").isNotEmpty();
        assertThat(msgs2).as("sub2 participates in the shared group").isNotEmpty();
        assertThat(msgs3).as("sub3 participates in the shared group").isNotEmpty();
        // Even split across 3 members for 6 messages.
        assertThat(msgs1.size()).isEqualTo(2);
        assertThat(msgs2.size()).isEqualTo(2);
        assertThat(msgs3.size()).isEqualTo(2);
        // No duplicates: each of the 6 distinct payloads delivered exactly once.
        var all = new CopyOnWriteArrayList<String>(msgs1);
        all.addAll(msgs2);
        all.addAll(msgs3);
        assertThat(all).doesNotHaveDuplicates();

        pub.disconnect();
        sub1.disconnect();
        sub2.disconnect();
        sub3.disconnect();
    }

    @Test
    void testSharedAndRegularSubscriptionsCoexist() throws Exception {
        var pub = createClient("pub");
        var shared1 = createClient("shared1");
        var shared2 = createClient("shared2");
        var regular = createClient("regular");

        pub.connect().get(5, TimeUnit.SECONDS);
        shared1.connect().get(5, TimeUnit.SECONDS);
        shared2.connect().get(5, TimeUnit.SECONDS);
        regular.connect().get(5, TimeUnit.SECONDS);

        var shared1Latch = new CountDownLatch(1);
        var shared2Latch = new CountDownLatch(1);
        var regularLatch = new CountDownLatch(1);

        // subscribe() returns the SUBACK future — await it (latch-style sync)
        // instead of sleeping before publishing.
        shared1.subscribe("$share/group/t", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> shared1Latch.countDown()).get(5, TimeUnit.SECONDS);
        shared2.subscribe("$share/group/t", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> shared2Latch.countDown()).get(5, TimeUnit.SECONDS);
        regular.subscribe("t", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> regularLatch.countDown()).get(5, TimeUnit.SECONDS);

        pub.publish("t", "msg".getBytes(StandardCharsets.UTF_8), QoS.AT_MOST_ONCE, false);

        // Regular subscriber always gets it.
        assertEventually(() -> regularLatch.getCount() == 0,
                "regular subscription delivers");
        // Round-robin delivers the shared copy to exactly ONE of the group members —
        // which one is non-deterministic, so assert on the group, not a specific client.
        assertEventually(() -> shared1Latch.getCount() == 0 || shared2Latch.getCount() == 0,
                "shared subscription delivers to one group member");
        int sharedReceived = 0;
        if (shared1Latch.getCount() == 0) sharedReceived++;
        if (shared2Latch.getCount() == 0) sharedReceived++;
        assertThat(sharedReceived).as("exactly one shared member receives the message")
                .isEqualTo(1);

        pub.disconnect();
        shared1.disconnect();
        shared2.disconnect();
        regular.disconnect();
    }

    /**
     * Polls {@code condition} until true or a 10 s deadline (CI-safe per
     * doc/AGENTS_test_patterns.md §2).
     */
    private static void assertEventually(java.util.function.BooleanSupplier condition,
                                         String description) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) break;
            try { Thread.sleep(50); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(description + " — interrupted", e);
            }
        }
        assertThat(condition.getAsBoolean()).as(description).isTrue();
    }

    // --- Helpers ---

    private MqttClient createClient(String clientId) {
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);
        var config = MqttClientConfig.defaults()
                .host("localhost")
                .port(1883)
                .clientId(clientId)
                .cleanSession(true)
                .version(MqttVersion.V5_0)
                .build();
        return new MqttClient(config, transports[1]);
    }
}
