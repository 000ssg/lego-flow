package ssg.legoflow.http.auth.oauth2.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive OAuth demo and verifies all feature sections.
 *
 * @since 1.0.0
 */
class DemoOAuthAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoOAuthAll.runAll();

        assertThat(results.oauthConfig())
                .as("OAuth2Config builder with all endpoints")
                .isTrue();

        assertThat(results.pkceChallenge())
                .as("PKCE S256 and plain generation and verification")
                .isTrue();

        assertThat(results.authorizationServer())
                .as("Authorization server (client registry, token store, code store)")
                .isTrue();

        assertThat(results.tokenResponse())
                .as("OAuth2TokenResponse JSON serialization and expiration")
                .isTrue();

        assertThat(results.oauthProviders())
                .as("Pre-configured OAuth providers (Google, GitHub)")
                .isTrue();

        assertThat(results.oidcDiscovery())
                .as("OIDC discovery metadata, ID token, UserInfo")
                .isTrue();

        assertThat(results.oauthError())
                .as("OAuth2Error standard error codes")
                .isTrue();
    }
}
