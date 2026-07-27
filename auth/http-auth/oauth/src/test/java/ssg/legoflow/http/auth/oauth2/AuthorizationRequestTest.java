package ssg.legoflow.http.auth.oauth2;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class AuthorizationRequestTest {

    private OAuth2Config config = OAuth2Config.builder()
            .clientId("app")
            .redirectUri("http://localhost/callback")
            .authorizationEndpoint("https://auth.example.com/authorize")
            .scopes(Set.of("openid", "profile"))
            .build();

    @Test
    void testBuildUrl() {
        var req = AuthorizationRequest.authorizationCode(config);
        String url = req.buildUrl();
        assertThat(url).startsWith("https://auth.example.com/authorize?");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=app");
        assertThat(url).contains("redirect_uri=");
        assertThat(url).contains("state=");
    }

    @Test
    void testWithPkce() {
        var pkce = PkceChallenge.generateS256();
        var req = AuthorizationRequest.authorizationCode(config).withPkce(pkce);
        String url = req.buildUrl();
        assertThat(url).contains("code_challenge=");
        assertThat(url).contains("code_challenge_method=S256");
    }

    @Test
    void testWithNonce() {
        var req = AuthorizationRequest.authorizationCode(config).withNonce("nonce123");
        String url = req.buildUrl();
        assertThat(url).contains("nonce=nonce123");
    }

    @Test
    void testWithAdditionalScopes() {
        var req = AuthorizationRequest.authorizationCode(config).withAdditionalScopes("email");
        String url = req.buildUrl();
        assertThat(url).contains("scope=");
    }

    @Test
    void testGetState() {
        var req = AuthorizationRequest.authorizationCode(config);
        assertThat(req.getState()).isNotNull().isNotEmpty();
    }

    @Test
    void testGetResponseType() {
        var req = AuthorizationRequest.authorizationCode(config);
        assertThat(req.getResponseType()).isEqualTo("code");
    }

    @Test
    void testUniqueState() {
        var r1 = AuthorizationRequest.authorizationCode(config);
        var r2 = AuthorizationRequest.authorizationCode(config);
        assertThat(r1.getState()).isNotEqualTo(r2.getState());
    }
}
