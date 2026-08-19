package ssg.legoflow.http.auth.oauth2;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class TokenRequestTest {

    private OAuth2Config config = OAuth2Config.builder()
            .clientId("app")
            .clientSecret("secret")
            .redirectUri("http://localhost/cb")
            .scopes(Set.of("read", "write"))
            .tokenEndpoint("https://auth.example.com/token")
            .build();

    @Test
    void testAuthorizationCode() {
        var req = TokenRequest.authorizationCode("code123", "http://localhost/cb", config);
        assertThat(req.getGrantType()).isEqualTo("authorization_code");
        assertThat(req.toFormBody()).contains("code=code123");
    }

    @Test
    void testAuthorizationCodeWithPkce() {
        var req = TokenRequest.authorizationCodeWithPkce("code", "http://localhost/cb", "verifier", config);
        assertThat(req.toFormBody()).contains("code_verifier=verifier");
    }

    @Test
    void testClientCredentials() {
        var req = TokenRequest.clientCredentials(config);
        assertThat(req.getGrantType()).isEqualTo("client_credentials");
        String body = req.toFormBody();
        assertThat(body).contains("client_id=app");
        assertThat(body).contains("client_secret=secret");
    }

    @Test
    void testPassword() {
        var req = TokenRequest.password("user", "pass", config);
        assertThat(req.getGrantType()).isEqualTo("password");
        String body = req.toFormBody();
        assertThat(body).contains("username=user");
        assertThat(body).contains("password=pass");
    }

    @Test
    void testRefreshToken() {
        var req = TokenRequest.refreshToken("refresh123", config);
        assertThat(req.getGrantType()).isEqualTo("refresh_token");
        assertThat(req.toFormBody()).contains("refresh_token=refresh123");
    }

    @Test
    void testCustomParameter() {
        var req = new TokenRequest("custom_grant");
        req.parameter("custom_param", "value");
        assertThat(req.toFormBody()).contains("custom_param=value");
    }

    @Test
    void testGetParameters() {
        var req = TokenRequest.clientCredentials(config);
        var params = req.getParameters();
        assertThat(params).containsKey("grant_type");
        assertThat(params).containsKey("client_id");
    }

    @Test
    void testFormBodyEncoding() {
        var req = new TokenRequest("test");
        req.parameter("key", "value with spaces");
        assertThat(req.toFormBody()).contains("value+with+spaces");
    }
}
