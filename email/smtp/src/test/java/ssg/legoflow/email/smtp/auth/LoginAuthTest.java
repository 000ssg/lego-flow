package ssg.legoflow.email.smtp.auth;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link LoginAuth}.
 */
class LoginAuthTest {

    @Test
    void testMechanism() {
        var auth = new LoginAuth("user", "pass");
        assertThat(auth.mechanism()).isEqualTo("LOGIN");
    }

    @Test
    void testInitialResponseIsNull() {
        var auth = new LoginAuth("user", "pass");
        assertThat(auth.initialResponse()).isNull();
    }

    @Test
    void testRespondUsername() throws SmtpAuthException {
        var auth = new LoginAuth("testuser", "testpass");
        String response = auth.respond(LoginAuth.usernameChallenge());
        String decoded = new String(Base64.getDecoder().decode(response));
        assertThat(decoded).isEqualTo("testuser");
        assertThat(auth.isComplete()).isFalse();
    }

    @Test
    void testRespondPassword() throws SmtpAuthException {
        var auth = new LoginAuth("testuser", "testpass");
        auth.respond(LoginAuth.usernameChallenge()); // username
        String response = auth.respond(LoginAuth.passwordChallenge());
        String decoded = new String(Base64.getDecoder().decode(response));
        assertThat(decoded).isEqualTo("testpass");
        assertThat(auth.isComplete()).isTrue();
    }

    @Test
    void testRespondTooManySteps() throws SmtpAuthException {
        var auth = new LoginAuth("user", "pass");
        auth.respond(""); // step 0
        auth.respond(""); // step 1
        assertThatThrownBy(() -> auth.respond(""))
                .isInstanceOf(SmtpAuthException.class);
    }

    @Test
    void testUsernameChallenge() {
        String challenge = LoginAuth.usernameChallenge();
        String decoded = new String(Base64.getDecoder().decode(challenge));
        assertThat(decoded).isEqualTo("Username:");
    }

    @Test
    void testPasswordChallenge() {
        String challenge = LoginAuth.passwordChallenge();
        String decoded = new String(Base64.getDecoder().decode(challenge));
        assertThat(decoded).isEqualTo("Password:");
    }

    @Test
    void testDecodeResponse() {
        String encoded = Base64.getEncoder().encodeToString("testvalue".getBytes());
        assertThat(LoginAuth.decodeResponse(encoded)).isEqualTo("testvalue");
    }

    @Test
    void testDecodeResponseWithWhitespace() {
        String encoded = Base64.getEncoder().encodeToString("testvalue".getBytes());
        assertThat(LoginAuth.decodeResponse("  " + encoded + "  ")).isEqualTo("testvalue");
    }

    @Test
    void testFullHandshake() throws SmtpAuthException {
        var auth = new LoginAuth("alice", "secret");

        // Step 1: Server sends Username: challenge
        String usernameResponse = auth.respond(LoginAuth.usernameChallenge());
        assertThat(LoginAuth.decodeResponse(usernameResponse)).isEqualTo("alice");
        assertThat(auth.isComplete()).isFalse();

        // Step 2: Server sends Password: challenge
        String passwordResponse = auth.respond(LoginAuth.passwordChallenge());
        assertThat(LoginAuth.decodeResponse(passwordResponse)).isEqualTo("secret");
        assertThat(auth.isComplete()).isTrue();
    }
}
