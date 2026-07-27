package ssg.legoflow.http.auth.oauth2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OAuth2ErrorTest {

    @Test
    void testCreateError() {
        var error = new OAuth2Error("invalid_request", "Missing parameter", "https://help.example.com");
        assertThat(error.error()).isEqualTo("invalid_request");
        assertThat(error.errorDescription()).isEqualTo("Missing parameter");
        assertThat(error.errorUri()).isEqualTo("https://help.example.com");
    }

    @Test
    void testErrorCodeOnly() {
        var error = new OAuth2Error("invalid_client");
        assertThat(error.error()).isEqualTo("invalid_client");
        assertThat(error.errorDescription()).isNull();
    }

    @Test
    void testToJson() {
        var error = new OAuth2Error("invalid_grant", "Expired code");
        String json = error.toJson();
        assertThat(json).contains("\"error\":\"invalid_grant\"");
        assertThat(json).contains("\"error_description\":\"Expired code\"");
    }

    @Test
    void testFromJson() {
        String json = "{\"error\":\"invalid_scope\",\"error_description\":\"Unknown scope\"}";
        var error = OAuth2Error.fromJson(json);
        assertThat(error.error()).isEqualTo("invalid_scope");
        assertThat(error.errorDescription()).isEqualTo("Unknown scope");
    }

    @Test
    void testConstants() {
        assertThat(OAuth2Error.INVALID_REQUEST).isEqualTo("invalid_request");
        assertThat(OAuth2Error.INVALID_CLIENT).isEqualTo("invalid_client");
        assertThat(OAuth2Error.INVALID_GRANT).isEqualTo("invalid_grant");
        assertThat(OAuth2Error.UNSUPPORTED_GRANT_TYPE).isEqualTo("unsupported_grant_type");
        assertThat(OAuth2Error.ACCESS_DENIED).isEqualTo("access_denied");
    }
}
