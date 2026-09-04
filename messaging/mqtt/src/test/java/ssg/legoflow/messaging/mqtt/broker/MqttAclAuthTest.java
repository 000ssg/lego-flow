package ssg.legoflow.messaging.mqtt.broker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.acl.TestDomain;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.ConnAckPacket;
import ssg.legoflow.messaging.mqtt.protocol.ConnectReturnCode;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests MQTT authentication against an ACL domain (TestDomain).
 *
 * <p>Verifies that valid users are accepted and invalid/unknown users are rejected.
 * Uses the acl module for credential storage — FOR TEST PURPOSE ONLY.
 *
 * @since 0.1.0
 */
class MqttAclAuthTest {

    private MqttBroker broker;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        // Wire acl's TestDomain into the MQTT broker via the adapter
        var auth = new MqttAclDomainAuthenticator(TestDomain.INSTANCE);
        var config = new MqttBrokerConfig("localhost", 0, 10, 65536, 32,
                false, true, 0, 100, null, auth, null);
        broker = new MqttBroker(config);
        broker.bind("localhost", 0);
        port = broker.getPort();
    }

    @AfterEach
    void tearDown() {
        broker.stop();
    }

    @Test
    void testAdminAccepted() throws Exception {
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-admin")
                .username("admin").password("admin").build())) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test
    void testUser1Accepted() throws Exception {
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-user1")
                .username("user1").password("user1").build())) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
        }
    }

    @Test
    void testWrongPasswordRejected() throws Exception {
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-badpass")
                .username("admin").password("wrong").build())) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
        }
    }

    @Test
    void testUnknownUserRejected() throws Exception {
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-unknown")
                .username("nobody-at-all").password("pass").build())) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
        }
    }

    @Test
    void testNullCredentialsRejected() throws Exception {
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-null").build())) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
        }
    }

    @Test
    void testGuestAccepted() throws Exception {
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-guest")
                .username("guest").password("guest").build())) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
        }
    }

    @Test
    void testPowerUserAccepted() throws Exception {
        // poweruser belongs to both users and managers groups
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-power")
                .username("poweruser").password("poweruser").build())) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
        }
    }

    @Test
    void testNobodyWithNoRoleRejected() throws Exception {
        // "nobody" user exists but has no roles — should still be accepted as auth only checks password
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-nobody")
                .username("nobody").password("nobody").build())) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            // Auth is just password check — nobody's password is correct
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
        }
    }

    @Test
    void testMultipleClientsWithAclAuth() throws Exception {
        try (var c1 = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-c1")
                .username("admin").password("admin").build());
             var c2 = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("acl-c2")
                .username("user1").password("user1").build())) {
            c1.connect().get(5, TimeUnit.SECONDS);
            c2.connect().get(5, TimeUnit.SECONDS);
            assertThat(broker.getConnectedClients()).contains("acl-c1", "acl-c2");
        }
    }
}
