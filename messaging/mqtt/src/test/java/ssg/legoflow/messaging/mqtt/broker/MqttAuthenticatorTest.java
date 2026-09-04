package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.ConnAckPacket;
import ssg.legoflow.messaging.mqtt.protocol.ConnectReturnCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link MqttAuthenticator} and {@link InMemoryAuthenticator}.
 *
 * @since 0.1.0
 */
class MqttAuthenticatorTest {

    private MqttBroker broker;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        var auth = new InMemoryAuthenticator()
                .addUser("admin", "secret")
                .addUser("user1", "pass1");

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
    void testValidCredentialsAccepted() throws Exception {
        // Given: client with valid credentials
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("auth-valid")
                .username("admin").password("secret").build())) {

            // When: connect
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);

            // Then: accepted
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test
    void testInvalidPasswordRejected() throws Exception {
        // Given: client with wrong password
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("auth-badpass")
                .username("admin").password("wrong").build())) {

            // When: connect
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);

            // Then: rejected with BAD_CREDENTIALS
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
            assertThat(client.isConnected()).isFalse();
        }
    }

    @Test
    void testUnknownUserRejected() throws Exception {
        // Given: client with unknown username
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("auth-unknown")
                .username("nobody").password("pass").build())) {

            // When: connect
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);

            // Then: rejected
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
        }
    }

    @Test
    void testNullCredentialsRejected() throws Exception {
        // Given: client without credentials
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("auth-null").build())) {

            // When: connect
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);

            // Then: rejected
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
        }
    }

    @Test
    void testInMemoryAuthenticatorAddRemoveUser() {
        // Given: authenticator
        var auth = new InMemoryAuthenticator();

        // When: add user
        auth.addUser("test", "pass");

        // Then: authenticated
        assertThat(auth.authenticate("test", "pass")).isTrue();
        assertThat(auth.userCount()).isEqualTo(1);

        // When: remove user
        auth.removeUser("test");

        // Then: no longer authenticated
        assertThat(auth.authenticate("test", "pass")).isFalse();
        assertThat(auth.userCount()).isEqualTo(0);
    }

    @Test
    void testInMemoryAuthenticatorFromMap() {
        // Given: authenticator from map
        var auth = new InMemoryAuthenticator(Map.of("a", "1", "b", "2"));

        // Then: both users authenticate
        assertThat(auth.authenticate("a", "1")).isTrue();
        assertThat(auth.authenticate("b", "2")).isTrue();
        assertThat(auth.authenticate("a", "2")).isFalse();
        assertThat(auth.userCount()).isEqualTo(2);
    }

    @Test
    void testCustomAuthenticatorLambda() {
        // Given: custom authenticator as lambda
        MqttAuthenticator auth = (user, pass) -> "token".equals(pass);

        // Then: works with matching password
        assertThat(auth.authenticate("anyone", "token")).isTrue();
        assertThat(auth.authenticate("anyone", "wrong")).isFalse();
    }

    @Test
    void testMultipleClientsWithAuth() throws Exception {
        // Given: two clients with valid credentials
        try (var c1 = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("auth-c1")
                .username("admin").password("secret").build());
             var c2 = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("auth-c2")
                .username("user1").password("pass1").build())) {

            // When: both connect
            c1.connect().get(5, TimeUnit.SECONDS);
            c2.connect().get(5, TimeUnit.SECONDS);

            // Then: both connected
            assertThat(broker.getConnectedClients()).contains("auth-c1", "auth-c2");
        }
    }
}
