package ssg.legoflow.http.auth.oauth2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PkceChallengeTest {

    @Test
    void testGenerateS256() {
        var pkce = PkceChallenge.generateS256();
        assertThat(pkce.getCodeVerifier()).isNotEmpty();
        assertThat(pkce.getCodeVerifier()).hasSize(43);
        assertThat(pkce.getCodeChallenge()).isNotEmpty();
        assertThat(pkce.getChallengeMethod()).isEqualTo("S256");
        // Challenge should be different from verifier
        assertThat(pkce.getCodeChallenge()).isNotEqualTo(pkce.getCodeVerifier());
    }

    @Test
    void testGenerateS256CustomLength() {
        var pkce = PkceChallenge.generateS256(128);
        assertThat(pkce.getCodeVerifier()).hasSize(128);
    }

    @Test
    void testGeneratePlain() {
        var pkce = PkceChallenge.generatePlain();
        assertThat(pkce.getCodeVerifier()).isNotEmpty();
        assertThat(pkce.getChallengeMethod()).isEqualTo("plain");
        assertThat(pkce.getCodeChallenge()).isEqualTo(pkce.getCodeVerifier());
    }

    @Test
    void testVerifyS256() {
        var pkce = PkceChallenge.generateS256();
        assertThat(PkceChallenge.verify(pkce.getCodeVerifier(), pkce.getCodeChallenge(), "S256")).isTrue();
    }

    @Test
    void testVerifyPlain() {
        var pkce = PkceChallenge.generatePlain();
        assertThat(PkceChallenge.verify(pkce.getCodeVerifier(), pkce.getCodeChallenge(), "plain")).isTrue();
    }

    @Test
    void testVerifyWrongVerifier() {
        var pkce = PkceChallenge.generateS256();
        assertThat(PkceChallenge.verify("wrong-verifier-that-is-long-enough-for-check",
                pkce.getCodeChallenge(), "S256")).isFalse();
    }

    @Test
    void testVerifyNullArgs() {
        assertThat(PkceChallenge.verify(null, "challenge", "S256")).isFalse();
        assertThat(PkceChallenge.verify("verifier", null, "S256")).isFalse();
    }

    @Test
    void testVerifyUnknownMethod() {
        assertThat(PkceChallenge.verify("v", "c", "unknown")).isFalse();
    }

    @Test
    void testInvalidVerifierLength() {
        assertThatThrownBy(() -> PkceChallenge.generateS256(42))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PkceChallenge.generateS256(129))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testComputeS256Challenge() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = PkceChallenge.computeS256Challenge(verifier);
        assertThat(challenge).isNotEmpty();
        // Challenge is base64url encoded SHA-256 hash
        assertThat(challenge).doesNotContain("+").doesNotContain("/").doesNotContain("=");
    }

    @Test
    void testCodeVerifierUsesUnreservedChars() {
        String verifier = PkceChallenge.generateCodeVerifier(128);
        assertThat(verifier).matches("[A-Za-z0-9\\-._~]+");
    }

    @Test
    void testUniquePkce() {
        var p1 = PkceChallenge.generateS256();
        var p2 = PkceChallenge.generateS256();
        assertThat(p1.getCodeVerifier()).isNotEqualTo(p2.getCodeVerifier());
    }
}
