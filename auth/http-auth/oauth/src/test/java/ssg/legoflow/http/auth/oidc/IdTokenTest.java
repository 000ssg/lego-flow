package ssg.legoflow.http.auth.oidc;

import ssg.legoflow.http.auth.token.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class IdTokenTest {

    private JwtTokenProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = JwtTokenProvider.hmac256(
                "this-is-a-very-long-secret-key-at-least-32-bytes!", "test-issuer", Duration.ofHours(1));
    }

    @Test
    void testParseValidToken() {
        String token = jwtProvider.generateToken("alice", Map.of(
                "name", "Alice Smith",
                "email", "alice@example.com",
                "nonce", "test-nonce"));
        var idToken = IdToken.parse(token);
        assertThat(idToken).isPresent();
        assertThat(idToken.get().getSubject()).isEqualTo("alice");
    }

    @Test
    void testParseNull() {
        assertThat(IdToken.parse(null)).isEmpty();
    }

    @Test
    void testParseBlank() {
        assertThat(IdToken.parse("  ")).isEmpty();
    }

    @Test
    void testParseMalformed() {
        assertThat(IdToken.parse("not.a.valid-jwt")).isEmpty();
    }

    @Test
    void testParseTwoParts() {
        assertThat(IdToken.parse("part1.part2")).isEmpty();
    }

    @Test
    void testGetSubject() {
        String token = jwtProvider.generateToken("alice");
        var idToken = IdToken.parse(token).orElseThrow();
        assertThat(idToken.getSubject()).isEqualTo("alice");
    }

    @Test
    void testGetIssuer() {
        String token = jwtProvider.generateToken("alice");
        var idToken = IdToken.parse(token).orElseThrow();
        assertThat(idToken.getIssuer()).isEqualTo("test-issuer");
    }

    @Test
    void testGetExpiresAt() {
        String token = jwtProvider.generateToken("alice");
        var idToken = IdToken.parse(token).orElseThrow();
        assertThat(idToken.getExpiresAt()).isNotNull();
        assertThat(idToken.getExpiresAt()).isGreaterThan(0);
    }

    @Test
    void testGetIssuedAt() {
        String token = jwtProvider.generateToken("alice");
        var idToken = IdToken.parse(token).orElseThrow();
        assertThat(idToken.getIssuedAt()).isNotNull();
    }

    @Test
    void testGetName() {
        String token = jwtProvider.generateToken("alice", Map.of("name", "Alice Smith"));
        var idToken = IdToken.parse(token).orElseThrow();
        assertThat(idToken.getName()).isEqualTo("Alice Smith");
    }

    @Test
    void testGetEmail() {
        String token = jwtProvider.generateToken("alice", Map.of("email", "alice@example.com"));
        var idToken = IdToken.parse(token).orElseThrow();
        assertThat(idToken.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void testGetNonce() {
        String token = jwtProvider.generateToken("alice", Map.of("nonce", "abc123"));
        var idToken = IdToken.parse(token).orElseThrow();
        assertThat(idToken.getNonce()).isEqualTo("abc123");
    }

    @Test
    void testGetPicture() {
        String token = jwtProvider.generateToken("alice", Map.of("picture", "https://example.com/pic.jpg"));
        var idToken = IdToken.parse(token).orElseThrow();
        assertThat(idToken.getPicture()).isEqualTo("https://example.com/pic.jpg");
    }

    @Test
    void testValidateWithProvider() {
        String token = jwtProvider.generateToken("alice");
        var idToken = IdToken.parse(token).orElseThrow();
        var validated = idToken.validate(jwtProvider);
        assertThat(validated).isPresent();
        assertThat(validated.get()).containsKey("sub");
    }

    @Test
    void testValidateWithWrongProvider() {
        var otherProvider = JwtTokenProvider.hmac256(
                "another-secret-key-that-is-at-least-32-bytes!!", "other", Duration.ofHours(1));
        String token = jwtProvider.generateToken("alice");
        var idToken = IdToken.parse(token).orElseThrow();
        var validated = idToken.validate(otherProvider);
        assertThat(validated).isEmpty();
    }

    @Test
    void testGetRawToken() {
        String token = jwtProvider.generateToken("alice");
        var idToken = IdToken.parse(token).orElseThrow();
        assertThat(idToken.getRawToken()).isEqualTo(token);
    }

    @Test
    void testGetClaims() {
        String token = jwtProvider.generateToken("alice");
        var idToken = IdToken.parse(token).orElseThrow();
        assertThat(idToken.getClaims()).isNotNull();
    }
}
