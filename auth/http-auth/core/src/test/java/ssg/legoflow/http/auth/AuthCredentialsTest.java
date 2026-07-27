package ssg.legoflow.http.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AuthCredentialsTest {

    @Test
    void testBasicCredentials() {
        var creds = new AuthCredentials.Basic("user", "pass");
        assertThat(creds.username()).isEqualTo("user");
        assertThat(creds.password()).isEqualTo("pass");
    }

    @Test
    void testBearerCredentials() {
        var creds = new AuthCredentials.Bearer("token123");
        assertThat(creds.token()).isEqualTo("token123");
    }

    @Test
    void testDigestCredentials() {
        var creds = new AuthCredentials.Digest("user", "realm", "nonce", "/uri",
                "response", "MD5", "cnonce", "00000001", "auth", "opaque");
        assertThat(creds.username()).isEqualTo("user");
        assertThat(creds.realm()).isEqualTo("realm");
        assertThat(creds.nonce()).isEqualTo("nonce");
        assertThat(creds.qop()).isEqualTo("auth");
    }

    @Test
    void testNoneCredentials() {
        var creds = new AuthCredentials.None();
        assertThat(creds).isInstanceOf(AuthCredentials.None.class);
    }

    @Test
    void testSealedInterfaceCoversAllTypes() {
        AuthCredentials creds = new AuthCredentials.Basic("u", "p");
        String type = switch (creds) {
            case AuthCredentials.Basic b -> "basic";
            case AuthCredentials.Bearer b -> "bearer";
            case AuthCredentials.Digest d -> "digest";
            case AuthCredentials.None n -> "none";
        };
        assertThat(type).isEqualTo("basic");
    }

    @Test
    void testBasicCredentialsEquality() {
        var a = new AuthCredentials.Basic("user", "pass");
        var b = new AuthCredentials.Basic("user", "pass");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void testBearerCredentialsEquality() {
        var a = new AuthCredentials.Bearer("token");
        var b = new AuthCredentials.Bearer("token");
        assertThat(a).isEqualTo(b);
    }
}
