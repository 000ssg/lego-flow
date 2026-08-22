package ssg.legoflow.http.auth.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class OAuth2ClientTest {

    private OAuth2Config config;
    private OAuth2Client client;

    @BeforeEach
    void setUp() {
        config = OAuth2Config.builder()
                .clientId("myapp")
                .clientSecret("secret")
                .redirectUri("http://localhost:8080/callback")
                .authorizationEndpoint("https://auth.example.com/authorize")
                .tokenEndpoint("https://auth.example.com/token")
                .scopes(Set.of("openid", "profile"))
                .build();
        client = new OAuth2Client(config);
    }

    @Test
    void testStartAuthorizationCodeFlow() {
        var authRequest = client.startAuthorizationCodeFlow();
        String url = authRequest.buildUrl();
        assertThat(url).startsWith("https://auth.example.com/authorize");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=myapp");
        assertThat(url).contains("state=");
    }

    @Test
    void testStartAuthorizationCodeFlowWithPkce() {
        var authRequest = client.startAuthorizationCodeFlowWithPkce();
        String url = authRequest.buildUrl();
        assertThat(url).contains("code_challenge=");
        assertThat(url).contains("code_challenge_method=S256");
        assertThat(authRequest.getPkceChallenge()).isNotNull();
    }

    @Test
    void testExchangeAuthorizationCode() {
        var tokenRequest = client.exchangeAuthorizationCode("code123", "http://localhost:8080/callback");
        assertThat(tokenRequest.getGrantType()).isEqualTo("authorization_code");
        String body = tokenRequest.toFormBody();
        assertThat(body).contains("grant_type=authorization_code");
        assertThat(body).contains("code=code123");
        assertThat(body).contains("client_id=myapp");
    }

    @Test
    void testExchangeAuthorizationCodeWithPkce() {
        var tokenRequest = client.exchangeAuthorizationCodeWithPkce(
                "code123", "http://localhost:8080/callback", "verifier123");
        String body = tokenRequest.toFormBody();
        assertThat(body).contains("code_verifier=verifier123");
    }

    @Test
    void testClientCredentialsGrant() {
        var tokenRequest = client.clientCredentialsGrant();
        assertThat(tokenRequest.getGrantType()).isEqualTo("client_credentials");
        String body = tokenRequest.toFormBody();
        assertThat(body).contains("client_id=myapp");
        assertThat(body).contains("client_secret=secret");
    }

    @Test
    void testPasswordGrant() {
        var tokenRequest = client.passwordGrant("user", "pass");
        assertThat(tokenRequest.getGrantType()).isEqualTo("password");
        String body = tokenRequest.toFormBody();
        assertThat(body).contains("username=user");
        assertThat(body).contains("password=pass");
    }

    @Test
    void testRefreshTokenGrant() {
        var tokenRequest = client.refreshTokenGrant("refresh123");
        assertThat(tokenRequest.getGrantType()).isEqualTo("refresh_token");
        String body = tokenRequest.toFormBody();
        assertThat(body).contains("refresh_token=refresh123");
    }

    @Test
    void testParseTokenResponse() {
        String json = "{\"access_token\":\"at\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
        var response = client.parseTokenResponse(json);
        assertThat(response).isPresent();
        assertThat(response.get().accessToken()).isEqualTo("at");
    }

    @Test
    void testParseTokenResponseError() {
        String json = "{\"error\":\"invalid_grant\",\"error_description\":\"Bad code\"}";
        var response = client.parseTokenResponse(json);
        assertThat(response).isEmpty();
    }

    @Test
    void testParseTokenResponseNull() {
        assertThat(client.parseTokenResponse(null)).isEmpty();
        assertThat(client.parseTokenResponse("")).isEmpty();
    }

    @Test
    void testGetConfig() {
        assertThat(client.getConfig()).isEqualTo(config);
    }
}
