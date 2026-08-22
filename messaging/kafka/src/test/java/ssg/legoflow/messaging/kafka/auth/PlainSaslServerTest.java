package ssg.legoflow.messaging.kafka.auth;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link PlainSaslServer}.
 */
class PlainSaslServerTest {

    @Test
    void testSuccessfulAuthentication() throws Exception {
        var store = new CredentialStore();
        store.addPlainUser("alice", "secret");
        var server = new PlainSaslServer(store);

        // PLAIN format: authzid \0 authcid \0 password
        byte[] message = "\0alice\0secret".getBytes(StandardCharsets.UTF_8);
        byte[] response = server.evaluateResponse(message);

        assertThat(response).isEmpty();
        assertThat(server.isComplete()).isTrue();
        assertThat(server.authenticatedUser()).isEqualTo("alice");
        assertThat(server.mechanismName()).isEqualTo("PLAIN");
    }

    @Test
    void testWrongPassword() {
        var store = new CredentialStore();
        store.addPlainUser("alice", "secret");
        var server = new PlainSaslServer(store);

        byte[] message = "\0alice\0wrongpassword".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> server.evaluateResponse(message))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Authentication failed");
        assertThat(server.isComplete()).isFalse();
    }

    @Test
    void testMalformedMessage() {
        var store = new CredentialStore();
        store.addPlainUser("alice", "secret");
        var server = new PlainSaslServer(store);

        byte[] message = "noNulSeparators".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> server.evaluateResponse(message))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Malformed");
    }

    @Test
    void testEmptyMessage() {
        var store = new CredentialStore();
        var server = new PlainSaslServer(store);

        assertThatThrownBy(() -> server.evaluateResponse(new byte[0]))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Empty");
    }
}
