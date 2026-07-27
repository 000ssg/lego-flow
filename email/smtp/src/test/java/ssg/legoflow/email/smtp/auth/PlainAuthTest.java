package ssg.legoflow.email.smtp.auth;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PlainAuth}.
 */
class PlainAuthTest {

    @Test
    void testMechanism() {
        var auth = new PlainAuth("user", "pass");
        assertThat(auth.mechanism()).isEqualTo("PLAIN");
    }

    @Test
    void testInitialResponse() {
        var auth = new PlainAuth("testuser", "testpass");
        String response = auth.initialResponse();
        assertThat(response).isNotNull();

        // Decode and verify format: \0authcid\0password
        byte[] decoded = Base64.getDecoder().decode(response);
        String creds = new String(decoded);
        assertThat(creds).isEqualTo("\0testuser\0testpass");
    }

    @Test
    void testInitialResponseWithAuthzId() {
        var auth = new PlainAuth("testuser", "testpass", "admin");
        String response = auth.initialResponse();
        byte[] decoded = Base64.getDecoder().decode(response);
        String creds = new String(decoded);
        assertThat(creds).isEqualTo("admin\0testuser\0testpass");
    }

    @Test
    void testIsCompleteAfterInitialResponse() {
        var auth = new PlainAuth("user", "pass");
        assertThat(auth.isComplete()).isFalse();
        auth.initialResponse();
        assertThat(auth.isComplete()).isTrue();
    }

    @Test
    void testRespondWhenNotComplete() throws SmtpAuthException {
        var auth = new PlainAuth("user", "pass");
        String response = auth.respond("");
        assertThat(response).isNotNull();
        assertThat(auth.isComplete()).isTrue();
    }

    @Test
    void testRespondAfterCompleteThrows() {
        var auth = new PlainAuth("user", "pass");
        auth.initialResponse();
        assertThatThrownBy(() -> auth.respond("challenge"))
                .isInstanceOf(SmtpAuthException.class);
    }

    @Test
    void testDecodeCredentials() throws SmtpAuthException {
        String encoded = Base64.getEncoder().encodeToString(
                "\0testuser\0testpass".getBytes());
        String[] creds = PlainAuth.decodeCredentials(encoded);
        assertThat(creds).hasSize(3);
        assertThat(creds[0]).isEmpty(); // authzid
        assertThat(creds[1]).isEqualTo("testuser");
        assertThat(creds[2]).isEqualTo("testpass");
    }

    @Test
    void testDecodeCredentialsWithAuthzId() throws SmtpAuthException {
        String encoded = Base64.getEncoder().encodeToString(
                "admin\0testuser\0testpass".getBytes());
        String[] creds = PlainAuth.decodeCredentials(encoded);
        assertThat(creds[0]).isEqualTo("admin");
        assertThat(creds[1]).isEqualTo("testuser");
        assertThat(creds[2]).isEqualTo("testpass");
    }

    @Test
    void testDecodeCredentialsInvalidBase64() {
        assertThatThrownBy(() -> PlainAuth.decodeCredentials("!!!invalid!!!"))
                .isInstanceOf(SmtpAuthException.class);
    }

    @Test
    void testDecodeCredentialsNoSeparator() {
        String encoded = Base64.getEncoder().encodeToString("noseparator".getBytes());
        assertThatThrownBy(() -> PlainAuth.decodeCredentials(encoded))
                .isInstanceOf(SmtpAuthException.class);
    }

    @Test
    void testDecodeCredentialsOneSeparator() {
        String encoded = Base64.getEncoder().encodeToString("only\0one".getBytes());
        assertThatThrownBy(() -> PlainAuth.decodeCredentials(encoded))
                .isInstanceOf(SmtpAuthException.class);
    }

    @Test
    void testRoundTrip() throws SmtpAuthException {
        var auth = new PlainAuth("alice", "secret123");
        String encoded = auth.initialResponse();
        String[] decoded = PlainAuth.decodeCredentials(encoded);
        assertThat(decoded[1]).isEqualTo("alice");
        assertThat(decoded[2]).isEqualTo("secret123");
    }
}
