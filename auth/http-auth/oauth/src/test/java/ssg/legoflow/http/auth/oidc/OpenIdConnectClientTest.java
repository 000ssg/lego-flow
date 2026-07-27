package ssg.legoflow.http.auth.oidc;

import ssg.legoflow.http.auth.oauth2.OAuth2Client;
import ssg.legoflow.http.auth.oauth2.OAuth2Config;
import ssg.legoflow.http.auth.token.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class OpenIdConnectClientTest {

    private OAuth2Client oauthClient;
    private JwtTokenProvider jwtProvider;
    private OpenIdConnectClient oidcClient;

    @BeforeEach
    void setUp() {
        var config = OAuth2Config.builder()
                .clientId("client1")
                .clientSecret("secret")
                .redirectUri("http://localhost/callback")
                .authorizationEndpoint("https://auth.example.com/authorize")
                .tokenEndpoint("https://auth.example.com/token")
                .scopes(Set.of("openid", "email"))
                .build();
        oauthClient = new OAuth2Client(config);
        jwtProvider = JwtTokenProvider.hmac256(
                "this-is-a-very-long-secret-key-at-least-32-bytes!", "test-issuer", Duration.ofHours(1));
        oidcClient = new OpenIdConnectClient(oauthClient, jwtProvider);
    }

    @Test
    void testGetOAuthClient() {
        assertThat(oidcClient.getOAuthClient()).isEqualTo(oauthClient);
    }

    @Test
    void testSetAndGetDiscoveryConfig() {
        assertThat(oidcClient.getDiscoveryConfig()).isNull();
        var discovery = new OidcDiscovery("https://issuer.example.com",
                null, null, null, null, null, null, null);
        oidcClient.setDiscoveryConfig(discovery);
        assertThat(oidcClient.getDiscoveryConfig()).isEqualTo(discovery);
    }

    @Test
    void testGetDiscoveryUrl() {
        assertThat(oidcClient.getDiscoveryUrl("https://accounts.google.com"))
                .isEqualTo("https://accounts.google.com/.well-known/openid-configuration");
    }

    @Test
    void testStartAuthenticationFlow() {
        var authRequest = oidcClient.startAuthenticationFlow();
        assertThat(authRequest).isNotNull();
        String url = authRequest.buildUrl();
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=client1");
        assertThat(url).contains("openid");
    }

    @Test
    void testValidateIdToken() {
        String token = jwtProvider.generateToken("alice", Map.of("nonce", "test-nonce"));
        var result = oidcClient.validateIdToken(token);
        assertThat(result).isPresent();
        assertThat(result.get()).containsEntry("sub", "alice");
    }

    @Test
    void testValidateIdTokenInvalid() {
        var result = oidcClient.validateIdToken("invalid-token");
        assertThat(result).isEmpty();
    }

    @Test
    void testValidateIdTokenNoProvider() {
        var clientNoProvider = new OpenIdConnectClient(oauthClient);
        var result = clientNoProvider.validateIdToken("some-token");
        assertThat(result).isEmpty();
    }

    @Test
    void testParseIdToken() {
        String token = jwtProvider.generateToken("alice");
        var parsed = oidcClient.parseIdToken(token);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().getSubject()).isEqualTo("alice");
    }

    @Test
    void testParseIdTokenInvalid() {
        var parsed = oidcClient.parseIdToken("not-a-jwt");
        assertThat(parsed).isEmpty();
    }

    @Test
    void testParseUserInfo() {
        String json = "{\"sub\":\"user123\",\"name\":\"Alice\",\"email\":\"alice@example.com\"}";
        var userInfo = oidcClient.parseUserInfo(json);
        assertThat(userInfo.getSubject()).isEqualTo("user123");
        assertThat(userInfo.getName()).isEqualTo("Alice");
        assertThat(userInfo.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void testConstructorWithoutTokenProvider() {
        var client = new OpenIdConnectClient(oauthClient);
        assertThat(client.getOAuthClient()).isEqualTo(oauthClient);
    }
}
