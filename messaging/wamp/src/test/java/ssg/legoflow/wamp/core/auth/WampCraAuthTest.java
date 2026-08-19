package ssg.legoflow.wamp.core.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for WAMP-CRA authentication.
 */
class WampCraAuthTest {

    @Test
    void testAuthMethod() {
        assertThat(WampCraAuth.AUTH_METHOD).isEqualTo("wampcra");
    }

    @Test
    void testGenerateChallenge() {
        var challenge = WampCraAuth.generateChallenge(1L, "user1", "frontend");
        assertThat(challenge).containsKey("challenge");
        var challengeStr = (String) challenge.get("challenge");
        assertThat(challengeStr).contains("user1");
        assertThat(challengeStr).contains("frontend");
        assertThat(challengeStr).contains("wampcra");
    }

    @Test
    void testSignProducesBase64() {
        var signature = WampCraAuth.sign("test-challenge", "secret123");
        assertThat(signature).isNotEmpty();
        // Base64 characters only
        assertThat(signature).matches("[A-Za-z0-9+/=]+");
    }

    @Test
    void testVerifyCorrectSignature() {
        var challenge = "test-challenge-data";
        var secret = "my-secret";
        var signature = WampCraAuth.sign(challenge, secret);
        assertThat(WampCraAuth.verify(challenge, secret, signature)).isTrue();
    }

    @Test
    void testVerifyWrongSignature() {
        assertThat(WampCraAuth.verify("challenge", "secret", "wrong-signature")).isFalse();
    }

    @Test
    void testVerifyWrongSecret() {
        var signature = WampCraAuth.sign("challenge", "correct-secret");
        assertThat(WampCraAuth.verify("challenge", "wrong-secret", signature)).isFalse();
    }

    @Test
    void testDifferentChallengesProduceDifferentSignatures() {
        var sig1 = WampCraAuth.sign("challenge1", "secret");
        var sig2 = WampCraAuth.sign("challenge2", "secret");
        assertThat(sig1).isNotEqualTo(sig2);
    }
}
