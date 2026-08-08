package ssg.legoflow.http.auth.basic.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive Basic/Digest demo and verifies all feature sections.
 *
 * @since 0.1.0
 */
class DemoBasicDigestAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoBasicDigestAll.runAll();

        assertThat(results.basicAuth())
                .as("HTTP Basic authentication (encode, authenticate, challenge)")
                .isTrue();

        assertThat(results.inMemoryUserStore())
                .as("InMemoryUserStore (add, authenticate, lookup, remove)")
                .isTrue();

        assertThat(results.hashedPasswordStore())
                .as("HashedPasswordStore (SHA-256 with per-user salt)")
                .isTrue();

        assertThat(results.digestAuthMd5())
                .as("HTTP Digest MD5 (nonce, response computation)")
                .isTrue();

        assertThat(results.digestAuthSha256())
                .as("HTTP Digest SHA-256 (algorithm variants, session detection)")
                .isTrue();

        assertThat(results.digestChallenge())
                .as("Digest challenge header (realm, nonce, opaque, stale)")
                .isTrue();
    }
}
