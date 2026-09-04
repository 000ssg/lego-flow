package ssg.legoflow.messaging.mqtt.broker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.acl.AclDomainBuilder;
import ssg.legoflow.acl.model.AclDomain;
import ssg.legoflow.acl.model.AclRule.AccessLevel;
import ssg.legoflow.acl.model.AclRule.Control;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.client.MqttMessageListener;
import ssg.legoflow.messaging.mqtt.protocol.ConnAckPacket;
import ssg.legoflow.messaging.mqtt.protocol.ConnectReturnCode;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.messaging.mqtt.protocol.ReasonCode;
import ssg.legoflow.messaging.mqtt.protocol.SubAckPacket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests topic-level ACL enforcement for MQTT.
 *
 * <p>Uses acl module rules to control publish/subscribe access per topic.
 * FOR TEST PURPOSE ONLY.
 *
 * @since 0.1.0
 */
class MqttAclTopicTest {

    private MqttBroker broker;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        var domain = new AclDomainBuilder()
                .name("MqttAclTest")
                .role("publisher", Set.of("write"))
                .role("subscriber", Set.of("read"))
                .role("admin", Set.of("read", "write"))
                .acl("allow-pub", "/sensors/**", "Allow publish to sensors", Control.ALLOW, AccessLevel.WRITE, Set.of("publisher", "admin"))
                .acl("allow-sub", "/sensors/**", "Allow subscribe to sensors", Control.ALLOW, AccessLevel.READ, Set.of("subscriber", "admin"))
                .acl("deny-secret", "/secret/**", "Deny access to secret", Control.DENY, AccessLevel.ALL, Set.of("publisher", "subscriber", "admin"))
                .user("pub-user", "pub", Set.of("publisher"), Set.of(), null)
                .user("sub-user", "sub", Set.of("subscriber"), Set.of(), null)
                .user("admin-user", "admin", Set.of("admin"), Set.of(), null)
                .user("no-role", "nobody", Set.of(), Set.of(), null)
                .build();
        var auth = new MqttAclDomainAuthenticator(domain);
        var aclChecker = new MqttAclDomainChecker(domain);
        var config = new MqttBrokerConfig("localhost", 0, 10, 65536, 32,
                false, true, 0, 100, null, auth, aclChecker);
        broker = new MqttBroker(config);
        broker.bind("localhost", 0);
        port = broker.getPort();
    }

    @AfterEach
    void tearDown() {
        broker.stop();
    }

    private void connectAdmin() throws Exception {
        try (var c = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-admin")
                .username("admin-user").password("admin").build())) {
            ConnAckPacket ack = c.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
        }
    }

    @Test
    void testAdminCanPublishAndSubscribe() throws Exception {
        var latch = new CountDownLatch(1);
        // Subscriber client (admin)
        try (var subClient = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-admin-sub")
                .username("admin-user").password("admin").build())) {
            subClient.connect().get(5, TimeUnit.SECONDS);
            var subAck = subClient.subscribe("sensors/temp", QoS.AT_LEAST_ONCE, (topic, payload, qos, retain) -> {
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);
            assertThat(subAck.reasonCodes()).contains(ReasonCode.GRANTED_QOS_1);
            // Separate publisher client (also admin) publishes
            try (var pubClient = new MqttClient(MqttClientConfig.defaults()
                    .host("localhost").port(port).clientId("acl-admin-pub")
                    .username("admin-user").password("admin").build())) {
                pubClient.connect().get(5, TimeUnit.SECONDS);
                pubClient.publish("sensors/temp", "22.5".getBytes(StandardCharsets.UTF_8), QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            }
            // Subscriber should receive the message
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void testPublisherCanPublishButNotSubscribe() throws Exception {
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-pub")
                .username("pub-user").password("pub").build())) {
            client.connect().get(5, TimeUnit.SECONDS);
            // Publisher can publish to sensors
            client.publish("sensors/temp", "22.5".getBytes(StandardCharsets.UTF_8), QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            // Publisher cannot subscribe — SUBACK returns NOT_AUTHORIZED
            var subAck = client.subscribe("sensors/temp", QoS.AT_LEAST_ONCE, (topic, payload, qos, retain) -> {}).get(5, TimeUnit.SECONDS);
            assertThat(subAck.reasonCodes()).contains(ReasonCode.NOT_AUTHORIZED);
        }
    }

    @Test
    void testSubscriberCanSubscribeButNotPublish() throws Exception {
        var latch = new CountDownLatch(1);
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-sub")
                .username("sub-user").password("sub").build())) {
            client.connect().get(5, TimeUnit.SECONDS);
            // Subscriber can subscribe
            var subAck = client.subscribe("sensors/temp", QoS.AT_LEAST_ONCE, (topic, payload, qos, retain) -> {
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);
            assertThat(subAck.reasonCodes()).contains(ReasonCode.GRANTED_QOS_1);
            // Subscriber cannot publish — publish completes but message is not delivered
            client.publish("sensors/temp", "22.5".getBytes(StandardCharsets.UTF_8), QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            // The publish future completes (broker sends NOT_AUTHORIZED PUBACK), but no message arrives
            assertThat(latch.await(1, TimeUnit.SECONDS)).isFalse();
        }
    }

    @Test
    void testDenyRuleBlocksSecretTopics() throws Exception {
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-secret")
                .username("admin-user").password("admin").build())) {
            client.connect().get(5, TimeUnit.SECONDS);
            // Even admin cannot access secret topics due to DENY rule
            var subAck = client.subscribe("secret/data", QoS.AT_LEAST_ONCE, (topic, payload, qos, retain) -> {}).get(5, TimeUnit.SECONDS);
            assertThat(subAck.reasonCodes()).contains(ReasonCode.NOT_AUTHORIZED);
        }
    }

    @Test
    void testNoRoleUserCannotPublishOrSubscribe() throws Exception {
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-norole")
                .username("no-role").password("nobody").build())) {
            client.connect().get(5, TimeUnit.SECONDS);
            // No role → no ACL rules match → denied
            var subAck = client.subscribe("sensors/temp", QoS.AT_LEAST_ONCE, (topic, payload, qos, retain) -> {}).get(5, TimeUnit.SECONDS);
            assertThat(subAck.reasonCodes()).contains(ReasonCode.NOT_AUTHORIZED);
            // Publish is denied (broker sends NOT_AUTHORIZED PUBACK, client future completes)
            client.publish("sensors/temp", "22.5".getBytes(StandardCharsets.UTF_8), QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
        }
    }
}
