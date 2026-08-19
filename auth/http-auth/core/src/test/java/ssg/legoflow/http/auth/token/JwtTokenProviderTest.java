package ssg.legoflow.http.auth.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class JwtTokenProviderTest {

    private JwtTokenProvider hmacProvider;

    @BeforeEach
    void setUp() {
        hmacProvider = JwtTokenProvider.hmac256(
                "this-is-a-very-long-secret-key-at-least-32-bytes!", "test-issuer", Duration.ofHours(1));
    }

    @Test
    void testGenerateAndValidateHmacToken() {
        String token = hmacProvider.generateToken("user123");
        var claims = hmacProvider.validateToken(token);
        assertThat(claims).isPresent();
        assertThat(claims.get()).containsEntry("sub", "user123");
        assertThat(claims.get()).containsEntry("iss", "test-issuer");
    }

    @Test
    void testGenerateWithCustomClaims() {
        String token = hmacProvider.generateToken("user", Map.of("role", "admin", "level", 5L));
        var claims = hmacProvider.validateToken(token);
        assertThat(claims).isPresent();
        assertThat(claims.get()).containsEntry("role", "admin");
    }

    @Test
    void testGetSubject() {
        String token = hmacProvider.generateToken("alice");
        assertThat(hmacProvider.getSubject(token)).contains("alice");
    }

    @Test
    void testIsExpired() {
        String token = hmacProvider.generateToken("user");
        assertThat(hmacProvider.isExpired(token)).isFalse();
    }

    @Test
    void testExpiredToken() {
        var shortLived = JwtTokenProvider.hmac256(
                "this-is-a-very-long-secret-key-at-least-32-bytes!", "test", Duration.ofSeconds(-1));
        String token = shortLived.generateToken("user");
        assertThat(shortLived.validateToken(token)).isEmpty();
        assertThat(shortLived.isExpired(token)).isTrue();
    }

    @Test
    void testTamperedToken() {
        String token = hmacProvider.generateToken("user");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(hmacProvider.validateToken(tampered)).isEmpty();
    }

    @Test
    void testWrongSecret() {
        String token = hmacProvider.generateToken("user");
        var otherProvider = JwtTokenProvider.hmac256(
                "different-secret-key-at-least-32-bytes-long-here!", "test-issuer", Duration.ofHours(1));
        assertThat(otherProvider.validateToken(token)).isEmpty();
    }

    @Test
    void testIssuerMismatch() {
        String token = hmacProvider.generateToken("user");
        var otherIssuer = JwtTokenProvider.hmac256(
                "this-is-a-very-long-secret-key-at-least-32-bytes!", "other-issuer", Duration.ofHours(1));
        assertThat(otherIssuer.validateToken(token)).isEmpty();
    }

    @Test
    void testParseHeader() {
        String token = hmacProvider.generateToken("user");
        var header = hmacProvider.parseHeader(token);
        assertThat(header).isPresent();
        assertThat(header.get().alg()).isEqualTo("HS256");
        assertThat(header.get().typ()).isEqualTo("JWT");
    }

    @Test
    void testParseClaims() {
        String token = hmacProvider.generateToken("user");
        var claims = hmacProvider.parseClaims(token);
        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("user");
    }

    @Test
    void testInvalidTokenFormat() {
        assertThat(hmacProvider.validateToken(null)).isEmpty();
        assertThat(hmacProvider.validateToken("")).isEmpty();
        assertThat(hmacProvider.validateToken("not.a.valid.jwt.token")).isEmpty();
        assertThat(hmacProvider.validateToken("just-a-string")).isEmpty();
    }

    @Test
    void testGetSubjectInvalidToken() {
        assertThat(hmacProvider.getSubject(null)).isEmpty();
        assertThat(hmacProvider.getSubject("bad")).isEmpty();
    }

    @Test
    void testIsExpiredInvalidToken() {
        assertThat(hmacProvider.isExpired(null)).isTrue();
        assertThat(hmacProvider.isExpired("bad")).isTrue();
    }

    @Test
    void testBase64UrlEncodeAndDecode() {
        byte[] data = "Hello, World!".getBytes();
        String encoded = JwtTokenProvider.base64UrlEncode(data);
        byte[] decoded = JwtTokenProvider.base64UrlDecode(encoded);
        assertThat(decoded).isEqualTo(data);
    }

    @Test
    void testRsaTokenGeneration() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();

        var rsaProvider = JwtTokenProvider.rsa256(
                keyPair.getPrivate(), keyPair.getPublic(), "rsa-issuer", Duration.ofHours(1));

        String token = rsaProvider.generateToken("rsaUser", Map.of("scope", "read"));
        var claims = rsaProvider.validateToken(token);
        assertThat(claims).isPresent();
        assertThat(claims.get()).containsEntry("sub", "rsaUser");
        assertThat(rsaProvider.getAlgorithm()).isEqualTo("RS256");
    }

    @Test
    void testRsaTamperedToken() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();

        var rsaProvider = JwtTokenProvider.rsa256(
                keyPair.getPrivate(), keyPair.getPublic(), "rsa-issuer", Duration.ofHours(1));

        String token = rsaProvider.generateToken("user");
        String tampered = token.substring(0, token.length() - 5) + "ZZZZZ";
        assertThat(rsaProvider.validateToken(tampered)).isEmpty();
    }

    @Test
    void testShortHmacSecretThrows() {
        assertThatThrownBy(() -> JwtTokenProvider.hmac256("short", "iss", Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void testNullSubjectThrows() {
        assertThatThrownBy(() -> hmacProvider.generateToken(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testTokenContainsJti() {
        String token = hmacProvider.generateToken("user");
        var claims = hmacProvider.parseClaims(token);
        assertThat(claims).isPresent();
        assertThat(claims.get().getJwtId()).isNotNull();
    }

    @Test
    void testGetAlgorithm() {
        assertThat(hmacProvider.getAlgorithm()).isEqualTo("HS256");
    }

    @Test
    void testParseHeaderNull() {
        assertThat(hmacProvider.parseHeader(null)).isEmpty();
    }

    @Test
    void testParseClaimsNull() {
        assertThat(hmacProvider.parseClaims(null)).isEmpty();
    }
}
