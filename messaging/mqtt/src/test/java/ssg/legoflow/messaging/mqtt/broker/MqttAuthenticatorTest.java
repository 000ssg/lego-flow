package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.ConnAckPacket;
import ssg.legoflow.messaging.mqtt.protocol.ConnectReturnCode;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link MqttAuthenticator} and {@link InMemoryAuthenticator}.
 *
 * @since 0.2.0
 */
class MqttAuthenticatorTest {

    private MqttBroker broker;

    @BeforeEach
    void setUp() throws Exception {
        var auth = new InMemoryAuthenticator()
                .addUser("admin", "secret")
                .addUser("user1", "pass1");

        var config = new MqttBrokerConfig("localhost", 0, 10, 65536, 32,
                true, true, 0, 100, null, auth, null);
        broker = new MqttBroker(config);
        broker.start();
    }

    @AfterEach
    void tearDown() {
        broker.stop();
    }

    @Test
    void testValidCredentialsAccepted() throws Exception {
        try (var client = createClient("auth-valid", "admin", "secret")) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test
    void testInvalidPasswordRejected() throws Exception {
        try (var client = createClient("auth-badpass", "admin", "wrong")) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
            assertThat(client.isConnected()).isFalse();
        }
    }

    @Test
    void testUnknownUserRejected() throws Exception {
        try (var client = createClient("auth-unknown", "nobody", "pass")) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
        }
    }

    @Test
    void testNullCredentialsRejected() throws Exception {
        try (var client = createClient("auth-null", null, null)) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
        }
    }

    @Test
    void testInMemoryAuthenticatorAddRemoveUser() {
        var auth = new InMemoryAuthenticator();
        auth.addUser("test", "pass");
        assertThat(auth.authenticate("test", "pass")).isTrue();
        assertThat(auth.userCount()).isEqualTo(1);
        auth.removeUser("test");
        assertThat(auth.authenticate("test", "pass")).isFalse();
        assertThat(auth.userCount()).isEqualTo(0);
    }

    @Test
    void testInMemoryAuthenticatorFromMap() {
        var auth = new InMemoryAuthenticator(Map.of("a", "1", "b", "2"));
        assertThat(auth.authenticate("a", "1")).isTrue();
        assertThat(auth.authenticate("b", "2")).isTrue();
        assertThat(auth.authenticate("a", "2")).isFalse();
        assertThat(auth.userCount()).isEqualTo(2);
    }

    @Test
    void testCustomAuthenticatorLambda() {
        MqttAuthenticator auth = (user, pass) -> "token".equals(pass);
        assertThat(auth.authenticate("anyone", "token")).isTrue();
        assertThat(auth.authenticate("anyone", "wrong")).isFalse();
    }

    @Test
    void testMultipleClientsWithAuth() throws Exception {
        try (var c1 = createClient("auth-c1", "admin", "secret");
             var c2 = createClient("auth-c2", "user1", "pass1")) {
            c1.connect().get(5, TimeUnit.SECONDS);
            c2.connect().get(5, TimeUnit.SECONDS);
            assertThat(broker.getConnectedClients()).contains("auth-c1", "auth-c2");
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
