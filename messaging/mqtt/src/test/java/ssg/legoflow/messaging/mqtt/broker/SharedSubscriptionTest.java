package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.*;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MQTT 5.0 shared subscriptions (${@code $share/group/topic}).
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
        var msgs = new CopyOnWriteArrayList<String>();

        sub1.subscribe("$share/group/sensors/+", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> { msgs.add("sub1:" + new String(p, StandardCharsets.UTF_8)); latch1.countDown(); });
        sub2.subscribe("$share/group/sensors/+", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> { msgs.add("sub2:" + new String(p, StandardCharsets.UTF_8)); latch2.countDown(); });
        sub3.subscribe("$share/group/sensors/+", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> { msgs.add("sub3:" + new String(p, StandardCharsets.UTF_8)); latch3.countDown(); });

        // Wait for SUBACKs
        Thread.sleep(500);

        pub.publish("sensors/temp", "42".getBytes(StandardCharsets.UTF_8), QoS.AT_MOST_ONCE, false);
        Thread.sleep(500);

        // Only one subscriber should have received
        int receivedCount = 0;
        if (latch1.getCount() == 0) receivedCount++;
        if (latch2.getCount() == 0) receivedCount++;
        if (latch3.getCount() == 0) receivedCount++;

        assertThat(receivedCount).isEqualTo(1);

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
                (t, p, q, r) -> msgs1.add(new String(p, StandardCharsets.UTF_8)));
        sub2.subscribe("$share/group/data/#", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> msgs2.add(new String(p, StandardCharsets.UTF_8)));
        sub3.subscribe("$share/group/data/#", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> msgs3.add(new String(p, StandardCharsets.UTF_8)));

        Thread.sleep(500);

        // Publish 6 messages — each subscriber should get ~2
        for (int i = 0; i < 6; i++) {
            pub.publish("data/item", ("msg-" + i).getBytes(StandardCharsets.UTF_8),
                    QoS.AT_MOST_ONCE, false);
        }

        Thread.sleep(1000);

        assertThat(msgs1).isNotEmpty();
        assertThat(msgs2).isNotEmpty();
        assertThat(msgs3).isNotEmpty();
        assertThat(msgs1.size() + msgs2.size() + msgs3.size()).isEqualTo(6);

        pub.disconnect();
        sub1.disconnect();
        sub2.disconnect();
        sub3.disconnect();
    }

    @Disabled("Timing-dependent with in-memory transport — needs MqttClientService driving")
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

        var sharedLatch = new CountDownLatch(1);
        var regularLatch = new CountDownLatch(1);

        shared1.subscribe("$share/group/t", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> sharedLatch.countDown());
        shared2.subscribe("$share/group/t", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> {});
        regular.subscribe("t", QoS.AT_MOST_ONCE,
                (t, p, q, r) -> regularLatch.countDown());

        Thread.sleep(500);

        pub.publish("t", "msg".getBytes(StandardCharsets.UTF_8), QoS.AT_MOST_ONCE, false);
        Thread.sleep(500);

        // Regular subscriber always gets it, plus exactly 1 shared subscriber
        assertThat(regularLatch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(sharedLatch.await(1, TimeUnit.SECONDS)).isTrue();

        pub.disconnect();
        shared1.disconnect();
        shared2.disconnect();
        regular.disconnect();
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
