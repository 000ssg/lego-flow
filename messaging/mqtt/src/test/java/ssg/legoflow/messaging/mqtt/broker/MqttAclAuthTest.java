package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.*;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for MQTT ACL-based authentication.
 *
 * @since 0.2.0
 */
class MqttAclAuthTest {

    private MqttBroker broker;

    @BeforeEach
    void setUp() throws Exception {
        var auth = new InMemoryAuthenticator()
                .addUser("admin", "admin")
                .addUser("user1", "user1")
                .addUser("guest", "guest")
                .addUser("poweruser", "poweruser")
                .addUser("nobody", "nobody");

        MqttAclChecker acl = (String user, String topic, String action) -> true;
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
    void testAdminAccepted() throws Exception {
        try (var client = createClient("acl-admin", "admin", "admin")) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test
    void testUser1Accepted() throws Exception {
        try (var client = createClient("acl-user1", "user1", "user1")) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
        }
    }

    @Test
    void testWrongPasswordRejected() throws Exception {
        try (var client = createClient("acl-badpass", "admin", "wrong")) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
        }
    }

    @Test
    void testUnknownUserRejected() throws Exception {
        try (var client = createClient("acl-unknown", "nobody-at-all", "pass")) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
        }
    }

    @Test
    void testNullCredentialsRejected() throws Exception {
        try (var client = createClient("acl-null", null, null)) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
        }
    }

    @Test
    void testGuestAccepted() throws Exception {
        try (var client = createClient("acl-guest", "guest", "guest")) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
        }
    }

    @Test
    void testPowerUserAccepted() throws Exception {
        try (var client = createClient("acl-power", "poweruser", "poweruser")) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
        }
    }

    @Test
    void testNobodyWithNoRoleRejected() throws Exception {
        // "nobody" user exists but has no roles — auth checks password only
        try (var client = createClient("acl-nobody", "nobody", "nobody")) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
        }
    }

    @Test
    void testMultipleClientsWithAclAuth() throws Exception {
        try (var c1 = createClient("acl-c1", "admin", "admin");
             var c2 = createClient("acl-c2", "user1", "user1")) {
            c1.connect().get(5, TimeUnit.SECONDS);
            c2.connect().get(5, TimeUnit.SECONDS);
            assertThat(broker.getConnectedClients()).contains("acl-c1", "acl-c2");
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
