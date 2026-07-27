package ssg.legoflow.wamp.core.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Cryptosign (Ed25519) authentication.
 */
class CryptosignAuthTest {

    @Test
    void testAuthMethod() {
        assertThat(CryptosignAuth.AUTH_METHOD).isEqualTo("cryptosign");
    }

    @Test
    void testGenerateChallenge() {
        var challenge = CryptosignAuth.generateChallenge();
        assertThat(challenge).containsKey("challenge");
        var hex = (String) challenge.get("challenge");
        // 32 bytes = 64 hex chars
        assertThat(hex).hasSize(64);
    }

    @Test
    void testGenerateKeyPair() {
        var keyPair = CryptosignAuth.generateKeyPair();
        assertThat(keyPair).isNotNull();
        assertThat(keyPair.getPrivate()).isNotNull();
        assertThat(keyPair.getPublic()).isNotNull();
    }

    @Test
    void testSignAndVerify() {
        var keyPair = CryptosignAuth.generateKeyPair();
        var challenge = CryptosignAuth.generateChallenge();
        var challengeHex = (String) challenge.get("challenge");

        var signature = CryptosignAuth.sign(challengeHex, keyPair.getPrivate());
        assertThat(signature).isNotEmpty();
        // Signature is 64 bytes (128 hex) + challenge (64 hex) = 192 chars
        assertThat(signature.length()).isEqualTo(192);

        assertThat(CryptosignAuth.verify(signature, challengeHex, keyPair.getPublic())).isTrue();
    }

    @Test
    void testVerifyWithWrongKey() {
        var keyPair1 = CryptosignAuth.generateKeyPair();
        var keyPair2 = CryptosignAuth.generateKeyPair();
        var challenge = CryptosignAuth.generateChallenge();
        var challengeHex = (String) challenge.get("challenge");

        var signature = CryptosignAuth.sign(challengeHex, keyPair1.getPrivate());
        assertThat(CryptosignAuth.verify(signature, challengeHex, keyPair2.getPublic())).isFalse();
    }

    @Test
    void testVerifyWithShortSignature() {
        var keyPair = CryptosignAuth.generateKeyPair();
        assertThat(CryptosignAuth.verify("short", "challenge", keyPair.getPublic())).isFalse();
    }
}
