package ssg.legoflow.email.smtp.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link XOAuth2Auth}.
 */
class XOAuth2AuthTest {

    @Test
    void testMechanism() {
        var auth = new XOAuth2Auth("alice@example.com", "bearer-token");
        assertThat(auth.mechanism()).isEqualTo("XOAUTH2");
    }

    @Test
    void testInitialResponseContainsCredentials() throws SmtpAuthException {
        var auth = new XOAuth2Auth("alice@example.com", "myAccessToken");
        String response = auth.initialResponse();
        assertThat(response).isNotBlank();

        String[] decoded = XOAuth2Auth.decodeCredentials(response);
        assertThat(decoded[0]).isEqualTo("alice@example.com");
        assertThat(decoded[1]).isEqualTo("myAccessToken");
    }

    @Test
    void testIsCompleteTrueAfterInit() {
        var auth = new XOAuth2Auth("user", "token");
        assertThat(auth.isComplete()).isFalse();
        auth.initialResponse();
        assertThat(auth.isComplete()).isTrue();
    }

    @Test
    void testRespondAfterCompleteReturnsEmpty() throws SmtpAuthException {
        var auth = new XOAuth2Auth("user", "token");
        auth.initialResponse(); // triggers complete
        // XOAUTH2 returns empty string for error challenges (not exception)
        String resp = auth.respond("challenge");
        assertThat(resp).isEmpty();
    }

    @Test
    void testConstructorRejectsNullEmail() {
        assertThatThrownBy(() -> new XOAuth2Auth(null, "token"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructorRejectsNullToken() {
        assertThatThrownBy(() -> new XOAuth2Auth("user", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testDecodeCredentialsInvalidBase64() {
        assertThatThrownBy(() -> XOAuth2Auth.decodeCredentials("not-valid-base64!!!"))
                .isInstanceOf(SmtpAuthException.class)
                .hasMessageContaining("Invalid Base64");
    }

    @Test
    void testDecodeCredentialsMissingUser() throws SmtpAuthException {
        String bad = java.util.Base64.getEncoder().encodeToString("wrongformat".getBytes());
        assertThatThrownBy(() -> XOAuth2Auth.decodeCredentials(bad))
                .isInstanceOf(SmtpAuthException.class)
                .hasMessageContaining("must start with");
    }

    @Test
    void testDecodeCredentialsInvalidFormat() throws SmtpAuthException {
        String bad = java.util.Base64.getEncoder().encodeToString(
                "user=alice@example.com".getBytes()); // missing auth= and trailing SOH
        assertThatThrownBy(() -> XOAuth2Auth.decodeCredentials(bad))
                .isInstanceOf(SmtpAuthException.class);
    }

    @Test
    void testValidCredentialsRoundTrip() throws SmtpAuthException {
        var auth = new XOAuth2Auth("test@gmail.com", "ya29.access");
        String encoded = auth.initialResponse();
        String[] parts = XOAuth2Auth.decodeCredentials(encoded);
        assertThat(parts[0]).isEqualTo("test@gmail.com");
        assertThat(parts[1]).isEqualTo("ya29.access");
    }
}
