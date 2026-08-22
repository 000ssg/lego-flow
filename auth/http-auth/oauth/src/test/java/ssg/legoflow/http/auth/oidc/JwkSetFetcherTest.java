package ssg.legoflow.http.auth.oidc;

import org.junit.jupiter.api.Test;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
class JwkSetFetcherTest {

    private String generateJwksJson() throws Exception {
        var kp = KeyPairGenerator.getInstance("RSA");
        kp.initialize(2048);
        var pair = kp.generateKeyPair();
        var rsaPub = (RSAPublicKey) pair.getPublic();
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPub.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPub.getPublicExponent().toByteArray());
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"test-kid\",\"n\":\"%s\",\"e\":\"%s\"}]}".formatted(n, e);
    }

    @Test
    void testFetchAndCacheJwkSet() throws Exception {
        String json = generateJwksJson();
        var fetcher = new JwkSetFetcher("https://example.com/.well-known/jwks.json");
        var jwkSet = fetcher.getJwkSet(url -> json);
        assertThat(jwkSet.size()).isEqualTo(1);
        assertThat(jwkSet.getKey("test-kid")).isPresent();
    }

    @Test
    void testCacheDoesNotRefetchWithinDuration() throws Exception {
        String json = generateJwksJson();
        var fetcher = new JwkSetFetcher("https://example.com/jwks", Duration.ofHours(1));

        int[] fetchCount = {0};
        JwkSetFetcher.JsonFetcher counter = url -> {
            fetchCount[0]++;
            return json;
        };

        fetcher.getJwkSet(counter);
        fetcher.getJwkSet(counter);
        fetcher.getJwkSet(counter);

        assertThat(fetchCount[0]).isEqualTo(1);
    }

    @Test
    void testInvalidateCache() throws Exception {
        String json = generateJwksJson();
        var fetcher = new JwkSetFetcher("https://example.com/jwks");

        int[] fetchCount = {0};
        JwkSetFetcher.JsonFetcher counter = url -> {
            fetchCount[0]++;
            return json;
        };

        fetcher.getJwkSet(counter);
        fetcher.invalidateCache();
        fetcher.getJwkSet(counter);

        assertThat(fetchCount[0]).isEqualTo(2);
    }

    @Test
    void testLoadFromJson() throws Exception {
        String json = generateJwksJson();
        var fetcher = new JwkSetFetcher("https://example.com/jwks");
        var jwkSet = fetcher.loadFromJson(json);
        assertThat(jwkSet.size()).isEqualTo(1);
    }

    @Test
    void testGetKeyWithRotation() throws Exception {
        String json = generateJwksJson();
        var fetcher = new JwkSetFetcher("https://example.com/jwks");

        var key = fetcher.getKey("test-kid", url -> json);
        assertThat(key).isPresent();

        var missing = fetcher.getKey("missing-kid", url -> json);
        assertThat(missing).isEmpty();
    }

    @Test
    void testFetchFailureReturnsEmpty() {
        var fetcher = new JwkSetFetcher("https://example.com/jwks");
        var jwkSet = fetcher.getJwkSet(url -> { throw new RuntimeException("network error"); });
        assertThat(jwkSet.size()).isZero();
    }

    @Test
    void testGetters() {
        var fetcher = new JwkSetFetcher("https://example.com/jwks", Duration.ofMinutes(30));
        assertThat(fetcher.getJwksUri()).isEqualTo("https://example.com/jwks");
        assertThat(fetcher.getCacheDuration()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void testNullUriThrows() {
        assertThatThrownBy(() -> new JwkSetFetcher(null))
                .isInstanceOf(NullPointerException.class);
    }
}
