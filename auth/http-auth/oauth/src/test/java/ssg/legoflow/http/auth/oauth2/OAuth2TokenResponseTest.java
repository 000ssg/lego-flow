package ssg.legoflow.http.auth.oauth2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OAuth2TokenResponseTest {

    @Test
    void testCreateResponse() {
        var response = new OAuth2TokenResponse("access123", "Bearer", 3600, "refresh456", "openid");
        assertThat(response.accessToken()).isEqualTo("access123");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.refreshToken()).isEqualTo("refresh456");
        assertThat(response.scope()).isEqualTo("openid");
    }

    @Test
    void testIsExpired() {
        var response = new OAuth2TokenResponse("t", "Bearer", 3600, null, null);
        assertThat(response.isExpired()).isFalse();
    }

    @Test
    void testExpiresAt() {
        var response = new OAuth2TokenResponse("t", "Bearer", 3600, null, null);
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    void testToJson() {
        var response = new OAuth2TokenResponse("access", "Bearer", 3600, "refresh", "read");
        String json = response.toJson();
        assertThat(json).contains("\"access_token\":\"access\"");
        assertThat(json).contains("\"token_type\":\"Bearer\"");
        assertThat(json).contains("\"expires_in\":3600");
        assertThat(json).contains("\"refresh_token\":\"refresh\"");
        assertThat(json).contains("\"scope\":\"read\"");
    }

    @Test
    void testFromJson() {
        String json = "{\"access_token\":\"abc\",\"token_type\":\"Bearer\",\"expires_in\":7200}";
        var response = OAuth2TokenResponse.fromJson(json);
        assertThat(response.accessToken()).isEqualTo("abc");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(7200);
    }

    @Test
    void testFromJsonWithRefresh() {
        String json = "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"refresh_token\":\"r\",\"scope\":\"s\"}";
        var response = OAuth2TokenResponse.fromJson(json);
        assertThat(response.refreshToken()).isEqualTo("r");
        assertThat(response.scope()).isEqualTo("s");
    }

    @Test
    void testRoundTrip() {
        var original = new OAuth2TokenResponse("tok", "Bearer", 1800, "ref", "openid email");
        var parsed = OAuth2TokenResponse.fromJson(original.toJson());
        assertThat(parsed.accessToken()).isEqualTo("tok");
        assertThat(parsed.tokenType()).isEqualTo("Bearer");
        assertThat(parsed.expiresIn()).isEqualTo(1800);
        assertThat(parsed.refreshToken()).isEqualTo("ref");
    }
}
