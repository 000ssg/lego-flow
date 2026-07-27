package ssg.legoflow.http.auth.oidc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OidcDiscoveryTest {

    @Test
    void testFullConstructor() {
        var discovery = new OidcDiscovery(
                "https://accounts.google.com",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                "https://openidconnect.googleapis.com/v1/userinfo",
                "https://www.googleapis.com/oauth2/v3/certs",
                List.of("code", "token"),
                List.of("openid", "email", "profile"),
                List.of("RS256"));

        assertThat(discovery.getIssuer()).isEqualTo("https://accounts.google.com");
        assertThat(discovery.getAuthorizationEndpoint()).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(discovery.getTokenEndpoint()).isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(discovery.getUserInfoEndpoint()).isEqualTo("https://openidconnect.googleapis.com/v1/userinfo");
        assertThat(discovery.getJwksUri()).isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
        assertThat(discovery.getResponseTypesSupported()).containsExactly("code", "token");
        assertThat(discovery.getScopesSupported()).containsExactly("openid", "email", "profile");
        assertThat(discovery.getIdTokenSigningAlgValuesSupported()).containsExactly("RS256");
    }

    @Test
    void testMinimalConstructor() {
        var discovery = new OidcDiscovery("https://issuer.example.com",
                null, null, null, null, null, null, null);
        assertThat(discovery.getIssuer()).isEqualTo("https://issuer.example.com");
        assertThat(discovery.getAuthorizationEndpoint()).isNull();
        assertThat(discovery.getTokenEndpoint()).isNull();
        assertThat(discovery.getUserInfoEndpoint()).isNull();
        assertThat(discovery.getJwksUri()).isNull();
        assertThat(discovery.getResponseTypesSupported()).isEmpty();
        assertThat(discovery.getScopesSupported()).isEmpty();
        assertThat(discovery.getIdTokenSigningAlgValuesSupported()).isEmpty();
    }

    @Test
    void testNullIssuerThrows() {
        assertThatThrownBy(() -> new OidcDiscovery(null, null, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testDiscoveryUrl() {
        assertThat(OidcDiscovery.discoveryUrl("https://accounts.google.com"))
                .isEqualTo("https://accounts.google.com/.well-known/openid-configuration");
    }

    @Test
    void testDiscoveryUrlTrailingSlash() {
        assertThat(OidcDiscovery.discoveryUrl("https://accounts.google.com/"))
                .isEqualTo("https://accounts.google.com/.well-known/openid-configuration");
    }

    @Test
    void testToJson() {
        var discovery = new OidcDiscovery("https://issuer.example.com",
                "https://issuer.example.com/auth",
                "https://issuer.example.com/token",
                "https://issuer.example.com/userinfo",
                "https://issuer.example.com/jwks",
                null, null, null);
        String json = discovery.toJson();
        assertThat(json).contains("\"issuer\":\"https://issuer.example.com\"");
        assertThat(json).contains("\"authorization_endpoint\":");
        assertThat(json).contains("\"token_endpoint\":");
        assertThat(json).contains("\"userinfo_endpoint\":");
        assertThat(json).contains("\"jwks_uri\":");
    }

    @Test
    void testToJsonMinimal() {
        var discovery = new OidcDiscovery("https://issuer.example.com",
                null, null, null, null, null, null, null);
        String json = discovery.toJson();
        assertThat(json).contains("\"issuer\":");
        assertThat(json).doesNotContain("authorization_endpoint");
    }

    @Test
    void testListsImmutable() {
        var discovery = new OidcDiscovery("https://issuer.example.com",
                null, null, null, null, List.of("code"), List.of("openid"), List.of("RS256"));
        assertThatThrownBy(() -> discovery.getResponseTypesSupported().add("token"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> discovery.getScopesSupported().add("email"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> discovery.getIdTokenSigningAlgValuesSupported().add("HS256"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
