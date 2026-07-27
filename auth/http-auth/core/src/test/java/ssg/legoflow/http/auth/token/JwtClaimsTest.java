package ssg.legoflow.http.auth.token;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class JwtClaimsTest {

    @Test
    void testBuilderAndGetters() {
        var claims = new JwtClaims()
                .issuer("test-issuer")
                .subject("user123")
                .audience("my-app")
                .expiresAt(1700000000L)
                .issuedAt(1699999000L)
                .jwtId("jti-123");

        assertThat(claims.getIssuer()).isEqualTo("test-issuer");
        assertThat(claims.getSubject()).isEqualTo("user123");
        assertThat(claims.getAudience()).isEqualTo("my-app");
        assertThat(claims.getExpiresAt()).isEqualTo(1700000000L);
        assertThat(claims.getIssuedAt()).isEqualTo(1699999000L);
        assertThat(claims.getJwtId()).isEqualTo("jti-123");
    }

    @Test
    void testCustomClaim() {
        var claims = new JwtClaims().claim("role", "admin");
        assertThat(claims.getStringClaim("role")).isEqualTo("admin");
    }

    @Test
    void testToJsonAndBack() {
        var original = new JwtClaims()
                .issuer("iss")
                .subject("sub")
                .expiresAt(1700000000L)
                .claim("custom", "value");

        String json = original.toJson();
        var parsed = JwtClaims.fromJson(json);

        assertThat(parsed.getIssuer()).isEqualTo("iss");
        assertThat(parsed.getSubject()).isEqualTo("sub");
        assertThat(parsed.getExpiresAt()).isEqualTo(1700000000L);
        assertThat(parsed.getStringClaim("custom")).isEqualTo("value");
    }

    @Test
    void testIsExpired() {
        var expired = new JwtClaims().expiresAt(Instant.now().minusSeconds(100));
        assertThat(expired.isExpired()).isTrue();

        var valid = new JwtClaims().expiresAt(Instant.now().plusSeconds(3600));
        assertThat(valid.isExpired()).isFalse();

        var noExp = new JwtClaims();
        assertThat(noExp.isExpired()).isFalse();
    }

    @Test
    void testIsNotYetValid() {
        var future = new JwtClaims().notBefore(Instant.now().plusSeconds(3600));
        assertThat(future.isNotYetValid()).isTrue();

        var past = new JwtClaims().notBefore(Instant.now().minusSeconds(100));
        assertThat(past.isNotYetValid()).isFalse();
    }

    @Test
    void testToMap() {
        var claims = new JwtClaims().subject("test").claim("a", 1L);
        var map = claims.toMap();
        assertThat(map).containsEntry("sub", "test");
        assertThat(map).containsEntry("a", 1L);
    }

    @Test
    void testFromJsonWithBooleans() {
        var claims = JwtClaims.fromJson("{\"flag\":true,\"other\":false}");
        assertThat(claims.getStringClaim("flag")).isEqualTo("true");
    }

    @Test
    void testFromJsonWithNumbers() {
        var claims = JwtClaims.fromJson("{\"count\":42,\"ratio\":3.14}");
        assertThat(claims.getLongClaim("count")).isEqualTo(42L);
    }

    @Test
    void testEmptyClaims() {
        var claims = new JwtClaims();
        assertThat(claims.toJson()).isEqualTo("{}");
        assertThat(claims.toMap()).isEmpty();
    }

    @Test
    void testExpiresAtWithInstant() {
        var now = Instant.now();
        var claims = new JwtClaims().expiresAt(now);
        assertThat(claims.getExpiresAt()).isEqualTo(now.getEpochSecond());
    }

    @Test
    void testIssuedAtWithInstant() {
        var now = Instant.now();
        var claims = new JwtClaims().issuedAt(now);
        assertThat(claims.getIssuedAt()).isEqualTo(now.getEpochSecond());
    }

    @Test
    void testNotBeforeWithInstant() {
        var now = Instant.now();
        var claims = new JwtClaims().notBefore(now);
        assertThat(claims.getNotBefore()).isEqualTo(now.getEpochSecond());
    }
}
