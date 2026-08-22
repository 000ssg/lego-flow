package ssg.legoflow.http.security;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AuthorizationHandlerTest {

    private final AuthorizationHandler handler = new AuthorizationHandler();

    @Test
    void testParseBasicAuthorization() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

        // When
        var creds = handler.parseAuthorization(request);

        // Then
        assertThat(creds).isNotNull();
        assertThat(creds.scheme()).isEqualTo("Basic");
        assertThat(creds.credentials()).isEqualTo("dXNlcjpwYXNz");
    }

    @Test
    void testParseBearerAuthorization() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer my-jwt-token-here");

        // When
        var creds = handler.parseAuthorization(request);

        // Then
        assertThat(creds).isNotNull();
        assertThat(creds.scheme()).isEqualTo("Bearer");
        assertThat(creds.credentials()).isEqualTo("my-jwt-token-here");
    }

    @Test
    void testParseAuthorizationNoHeader() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");

        // Then
        assertThat(handler.parseAuthorization(request)).isNull();
    }

    @Test
    void testParseAuthorizationInvalid() {
        assertThat(handler.parseAuthorizationHeader("")).isNull();
        assertThat(handler.parseAuthorizationHeader("NoSpace")).isNull();
    }

    @Test
    void testParseBasicAuth() {
        // Given — "user:pass" = "dXNlcjpwYXNz"
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

        // When
        var basic = handler.parseBasicAuth(request);

        // Then
        assertThat(basic).isNotNull();
        assertThat(basic.username()).isEqualTo("user");
        assertThat(basic.password()).isEqualTo("pass");
    }

    @Test
    void testParseBasicAuthNotBasicScheme() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer token");

        // Then
        assertThat(handler.parseBasicAuth(request)).isNull();
    }

    @Test
    void testDecodeBasicCredentialsWithColonInPassword() {
        // Given — "admin:p:a:ss" base64
        String encoded = java.util.Base64.getEncoder().encodeToString("admin:p:a:ss".getBytes());

        // When
        var creds = handler.decodeBasicCredentials(encoded);

        // Then
        assertThat(creds).isNotNull();
        assertThat(creds.username()).isEqualTo("admin");
        assertThat(creds.password()).isEqualTo("p:a:ss");
    }

    @Test
    void testParseBearerToken() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer my-token");

        // When
        String token = handler.parseBearerToken(request);

        // Then
        assertThat(token).isEqualTo("my-token");
    }

    @Test
    void testParseBearerTokenNotBearerScheme() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

        // Then
        assertThat(handler.parseBearerToken(request)).isNull();
    }

    @Test
    void testEncodeBasicAuth() {
        // When
        String encoded = handler.encodeBasicAuth("user", "pass");

        // Then
        assertThat(encoded).isEqualTo("Basic dXNlcjpwYXNz");
    }

    @Test
    void testUnauthorizedResponse() {
        // When
        var response = handler.unauthorizedResponse("Basic", "MyRealm");

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Basic realm=\"MyRealm\"");
    }

    @Test
    void testEncodeDecodeRoundTrip() {
        // Given
        String encoded = handler.encodeBasicAuth("testuser", "testpass");

        // When
        var creds = handler.parseAuthorizationHeader(encoded);
        var basic = handler.decodeBasicCredentials(creds.credentials());

        // Then
        assertThat(basic.username()).isEqualTo("testuser");
        assertThat(basic.password()).isEqualTo("testpass");
    }
}
