package ssg.legoflow.http.auth.digest;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DigestChallengeTest {

    @Test
    void testToHeaderValue() {
        var challenge = new DigestChallenge("test-realm", "nonce123", "opaque456",
                "MD5", "auth", false);
        String header = challenge.toHeaderValue();
        assertThat(header).startsWith("Digest ");
        assertThat(header).contains("realm=\"test-realm\"");
        assertThat(header).contains("nonce=\"nonce123\"");
        assertThat(header).contains("opaque=\"opaque456\"");
        assertThat(header).contains("algorithm=MD5");
        assertThat(header).contains("qop=\"auth\"");
        assertThat(header).doesNotContain("stale");
    }

    @Test
    void testStaleChallenge() {
        var challenge = new DigestChallenge("realm", "nonce", null, "SHA-256", "auth", true);
        String header = challenge.toHeaderValue();
        assertThat(header).contains("stale=true");
        assertThat(header).contains("algorithm=SHA-256");
    }

    @Test
    void testNoOpaque() {
        var challenge = new DigestChallenge("realm", "nonce", null, "MD5", "auth", false);
        String header = challenge.toHeaderValue();
        assertThat(header).doesNotContain("opaque");
    }

    @Test
    void testGetters() {
        var challenge = new DigestChallenge("r", "n", "o", "MD5", "auth,auth-int", false);
        assertThat(challenge.getRealm()).isEqualTo("r");
        assertThat(challenge.getNonce()).isEqualTo("n");
        assertThat(challenge.getOpaque()).isEqualTo("o");
        assertThat(challenge.getAlgorithm()).isEqualTo("MD5");
        assertThat(challenge.getQop()).isEqualTo("auth,auth-int");
        assertThat(challenge.isStale()).isFalse();
    }

    @Test
    void testDefaultAlgorithm() {
        var challenge = new DigestChallenge("realm", "nonce", null, null, null, false);
        assertThat(challenge.getAlgorithm()).isEqualTo("MD5");
        assertThat(challenge.getQop()).isEqualTo("auth");
    }
}
