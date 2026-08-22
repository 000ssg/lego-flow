package ssg.legoflow.email.smtp.auth;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link CramMd5Auth}.
 */
class CramMd5AuthTest {

    @Test
    void testMechanism() {
        var auth = new CramMd5Auth("user", "pass");
        assertThat(auth.mechanism()).isEqualTo("CRAM-MD5");
    }

    @Test
    void testInitialResponseIsNull() {
        var auth = new CramMd5Auth("user", "pass");
        assertThat(auth.initialResponse()).isNull();
    }

    @Test
    void testRespondWithChallenge() throws Exception {
        var auth = new CramMd5Auth("alice", "secret");
        String challenge = Base64.getEncoder().encodeToString("<test.challenge>".getBytes());
        String response = auth.respond(challenge);
        assertThat(response).isNotNull();
        assertThat(auth.isComplete()).isTrue();

        boolean valid = CramMd5Auth.verify(response, "<test.challenge>", "alice", "secret");
        assertThat(valid).isTrue();
    }

    @Test
    void testRespondTwiceThrowsException() throws Exception {
        var auth = new CramMd5Auth("user", "pass");
        String challenge = Base64.getEncoder().encodeToString("<first>".getBytes());
        auth.respond(challenge);
        assertThatThrownBy(() -> auth.respond(challenge))
                .isInstanceOf(SmtpAuthException.class)
                .hasMessageContaining("additional challenges");
    }

    @Test
    void testComputeHmacMd5() throws Exception {
        String digest = CramMd5Auth.computeHmacMd5("key", "data");
        assertThat(digest).isNotBlank();
        assertThat(digest).hasSize(32); // 16 bytes * 2 hex chars
    }

    @Test
    void testComputeHmacMd5Consistent() throws Exception {
        String d1 = CramMd5Auth.computeHmacMd5("key", "data");
        String d2 = CramMd5Auth.computeHmacMd5("key", "data");
        assertThat(d1).isEqualTo(d2);
    }

    @Test
    void testVerifyCorrectCredentials() throws Exception {
        String challenge = "<challenge.data>";
        String expectedDigest = CramMd5Auth.computeHmacMd5("secret", challenge);
        String responseStr = "alice " + expectedDigest;
        String base64Response = Base64.getEncoder().encodeToString(responseStr.getBytes());

        assertThat(CramMd5Auth.verify(base64Response, challenge, "alice", "secret")).isTrue();
    }

    @Test
    void testVerifyWrongPassword() throws Exception {
        String challenge = "<challenge.data>";
        String expectedDigest = CramMd5Auth.computeHmacMd5("secret", challenge);
        String responseStr = "alice " + expectedDigest;
        String base64Response = Base64.getEncoder().encodeToString(responseStr.getBytes());

        assertThat(CramMd5Auth.verify(base64Response, challenge, "alice", "wrong")).isFalse();
    }

    @Test
    void testVerifyWrongUser() throws Exception {
        String challenge = "<challenge.data>";
        String expectedDigest = CramMd5Auth.computeHmacMd5("secret", challenge);
        String responseStr = "alice " + expectedDigest;
        String base64Response = Base64.getEncoder().encodeToString(responseStr.getBytes());

        assertThat(CramMd5Auth.verify(base64Response, challenge, "bob", "secret")).isFalse();
    }

    @Test
    void testVerifyMissingSpace() throws Exception {
        String noSpace = Base64.getEncoder().encodeToString("alicedigest".getBytes());
        assertThat(CramMd5Auth.verify(noSpace, "challenge", "alice", "secret")).isFalse();
    }

    @Test
    void testGenerateChallenge() {
        String challenge = CramMd5Auth.generateChallenge("mail.example.com");
        String decoded = new String(Base64.getDecoder().decode(challenge));
        assertThat(decoded).startsWith("<");
        assertThat(decoded).endsWith(">");
        assertThat(decoded).contains("@mail.example.com");
    }

    @Test
    void testConstructorRejectsNullUsername() {
        assertThatThrownBy(() -> new CramMd5Auth(null, "pass"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructorRejectsNullPassword() {
        assertThatThrownBy(() -> new CramMd5Auth("user", null))
                .isInstanceOf(NullPointerException.class);
    }
}
