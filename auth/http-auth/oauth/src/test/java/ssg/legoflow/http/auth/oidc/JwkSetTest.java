package ssg.legoflow.http.auth.oidc;

import org.junit.jupiter.api.Test;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
class JwkSetTest {

    @Test
    void testParseEmptyJwkSet() {
        var jwkSet = JwkSet.fromJson("{\"keys\":[]}");
        assertThat(jwkSet.size()).isZero();
        assertThat(jwkSet.getAllKeys()).isEmpty();
    }

    @Test
    void testParseNull() {
        var jwkSet = JwkSet.fromJson(null);
        assertThat(jwkSet.size()).isZero();
    }

    @Test
    void testParseBlank() {
        var jwkSet = JwkSet.fromJson("");
        assertThat(jwkSet.size()).isZero();
    }

    @Test
    void testParseNoKeysField() {
        var jwkSet = JwkSet.fromJson("{\"other\":\"value\"}");
        assertThat(jwkSet.size()).isZero();
    }

    @Test
    void testParseRsaKey() throws Exception {
        var kp = KeyPairGenerator.getInstance("RSA");
        kp.initialize(2048);
        var pair = kp.generateKeyPair();
        var rsaPub = (RSAPublicKey) pair.getPublic();

        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPub.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPub.getPublicExponent().toByteArray());

        String json = """
                {"keys":[{"kty":"RSA","kid":"key-1","n":"%s","e":"%s","use":"sig"}]}
                """.formatted(n, e);

        var jwkSet = JwkSet.fromJson(json);
        assertThat(jwkSet.size()).isEqualTo(1);
        assertThat(jwkSet.getKey("key-1")).isPresent();
        assertThat(jwkSet.getFirstKey()).isPresent();
        assertThat(jwkSet.getKeyIds()).containsExactly("key-1");
    }

    @Test
    void testParseMultipleKeys() throws Exception {
        var kp = KeyPairGenerator.getInstance("RSA");
        kp.initialize(2048);

        var pair1 = kp.generateKeyPair();
        var rsa1 = (RSAPublicKey) pair1.getPublic();
        String n1 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsa1.getModulus().toByteArray());
        String e1 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsa1.getPublicExponent().toByteArray());

        var pair2 = kp.generateKeyPair();
        var rsa2 = (RSAPublicKey) pair2.getPublic();
        String n2 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsa2.getModulus().toByteArray());
        String e2 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsa2.getPublicExponent().toByteArray());

        String json = """
                {"keys":[
                  {"kty":"RSA","kid":"key-1","n":"%s","e":"%s"},
                  {"kty":"RSA","kid":"key-2","n":"%s","e":"%s"}
                ]}
                """.formatted(n1, e1, n2, e2);

        var jwkSet = JwkSet.fromJson(json);
        assertThat(jwkSet.size()).isEqualTo(2);
        assertThat(jwkSet.getKey("key-1")).isPresent();
        assertThat(jwkSet.getKey("key-2")).isPresent();
        assertThat(jwkSet.getKey("key-3")).isEmpty();
    }

    @Test
    void testSkipNonRsaKeys() {
        String json = """
                {"keys":[{"kty":"EC","kid":"ec-1","crv":"P-256","x":"abc","y":"def"}]}
                """;
        var jwkSet = JwkSet.fromJson(json);
        assertThat(jwkSet.size()).isZero();
    }

    @Test
    void testKeyWithoutKid() throws Exception {
        var kp = KeyPairGenerator.getInstance("RSA");
        kp.initialize(2048);
        var pair = kp.generateKeyPair();
        var rsaPub = (RSAPublicKey) pair.getPublic();

        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPub.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPub.getPublicExponent().toByteArray());

        String json = "{\"keys\":[{\"kty\":\"RSA\",\"n\":\"%s\",\"e\":\"%s\"}]}".formatted(n, e);
        var jwkSet = JwkSet.fromJson(json);
        assertThat(jwkSet.size()).isEqualTo(1);
        assertThat(jwkSet.getFirstKey()).isPresent();
        assertThat(jwkSet.getKeyIds()).isEmpty();
    }

    @Test
    void testExtractJsonStringValue() {
        String json = "{\"kty\":\"RSA\",\"kid\":\"test-id\"}";
        assertThat(JwkSet.extractJsonStringValue(json, "kty")).isEqualTo("RSA");
        assertThat(JwkSet.extractJsonStringValue(json, "kid")).isEqualTo("test-id");
        assertThat(JwkSet.extractJsonStringValue(json, "missing")).isNull();
    }

    @Test
    void testGetAllKeys() throws Exception {
        var kp = KeyPairGenerator.getInstance("RSA");
        kp.initialize(2048);
        var pair = kp.generateKeyPair();
        var rsaPub = (RSAPublicKey) pair.getPublic();
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPub.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPub.getPublicExponent().toByteArray());

        String json = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\",\"n\":\"%s\",\"e\":\"%s\"}]}".formatted(n, e);
        var jwkSet = JwkSet.fromJson(json);
        assertThat(jwkSet.getAllKeys()).hasSize(1);
    }
}
