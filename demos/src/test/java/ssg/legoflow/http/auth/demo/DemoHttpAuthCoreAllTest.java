package ssg.legoflow.http.auth.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive HTTP Auth Core demo and verifies all feature sections.
 *
 * @since 0.1.0
 */
class DemoHttpAuthCoreAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoHttpAuthCoreAll.runAll();

        assertThat(results.authCredentials())
                .as("AuthCredentials sealed interface (Basic, Bearer, Digest, None)")
                .isTrue();

        assertThat(results.authResults())
                .as("AuthResult sealed interface (Success, Failure, Challenge)")
                .isTrue();

        assertThat(results.authPrincipal())
                .as("AuthPrincipal with roles and attributes")
                .isTrue();

        assertThat(results.schemeRegistry())
                .as("AuthSchemeRegistry registration, lookup, and removal")
                .isTrue();

        assertThat(results.sessionManagement())
                .as("HTTP session lifecycle (create, attributes, invalidate)")
                .isTrue();

        assertThat(results.jwtHs256())
                .as("JWT HS256 (HMAC-SHA256) generation and validation")
                .isTrue();

        assertThat(results.jwtRs256())
                .as("JWT RS256 (RSA-SHA256) generation and validation")
                .isTrue();

        assertThat(results.jwtClaims())
                .as("JWT claims (standard and custom, expiration)")
                .isTrue();
    }
}
