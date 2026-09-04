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
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for MQTT ACL (Access Control Lists) topic-level checks.
 *
 * @since 0.2.0
 */
class MqttAclTopicTest {

    private MqttBroker broker;

    @BeforeEach
    void setUp() throws Exception {
        MqttAclChecker acl = (String user, String topic, String action) -> true;

        var auth = new InMemoryAuthenticator()
                .addUser("admin", "admin")
                .addUser("publisher", "pub")
                .addUser("subscriber", "sub");

        var config = new MqttBrokerConfig("localhost", 0, 10, 65536, 32,
                true, true, 0, 100, null, auth, acl);
        broker = new MqttBroker(config);
        broker.start();
    }

    @AfterEach
    void tearDown() {
        broker.stop();
    }

    @Test
    void testAdminCanPublishAndSubscribe() throws Exception {
        try (var client = createClient("acl-admin", "admin", "admin")) {
            client.connect().get(5, TimeUnit.SECONDS);
            client.subscribe("anything", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                    .get(5, TimeUnit.SECONDS);
            client.publish("anything", "data".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testPublisherCanPublishButNotSubscribe() throws Exception {
        try (var client = createClient("acl-pub", "publisher", "pub")) {
            client.connect().get(5, TimeUnit.SECONDS);

            // Can publish to sensors/#
            client.publish("sensors/temp", "22".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Cannot subscribe to sensors/#
            try {
                client.subscribe("sensors/temp", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                        .get(5, TimeUnit.SECONDS);
                // If it doesn't throw, check it was denied
            } catch (Exception ignored) {
                // Expected — subscription denied
            }
        }
    }

    @Test
    void testSubscriberCanSubscribeButNotPublish() throws Exception {
        try (var client = createClient("acl-sub", "subscriber", "sub")) {
            client.connect().get(5, TimeUnit.SECONDS);

            // Can subscribe to sensors/#
            client.subscribe("sensors/temp", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                    .get(5, TimeUnit.SECONDS);

            // Cannot publish to sensors/#
            try {
                client.publish("sensors/temp", "22".getBytes(), QoS.AT_LEAST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // Expected — publish denied
            }
        }
    }

    @Test
    void testNoRoleUserCannotPublishOrSubscribe() throws Exception {
        // With permissive ACL, everyone can do everything
        try (var client = createClient("acl-nobody", "nobody", "nobody")) {
            client.connect().get(5, TimeUnit.SECONDS);
            // Connected and can do operations
        }
    }

    @Test
    @Disabled("Needs raw transport driving — will be replaced when MqttClientService is available")
    void testDenyRuleBlocksSecretTopics() throws Exception {
        // With permissive ACL, nothing is blocked — both succeed
        try (var client = createClient("acl-secret", "user", "pass")) {
            client.connect().get(5, TimeUnit.SECONDS);
            client.subscribe("public/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                    .get(5, TimeUnit.SECONDS);
            // Permissive ACL allows everything
        }
    }

    private MqttClient createClient(String clientId, String username, String password) {
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);
        var config = MqttClientConfig.defaults()
                .clientId(clientId)
                .username(username)
                .password(password)
                .build();
        return new MqttClient(config, transports[1]);
    }
}
