package ssg.legoflow.http.auth.bearer;

import ssg.legoflow.http.auth.*;
import ssg.legoflow.http.auth.oauth2.server.TokenStore;
import ssg.legoflow.http.auth.token.JwtTokenProvider;
import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class BearerAuthSchemeTest {

    private final JwtTokenProvider jwtProvider = JwtTokenProvider.hmac256(
            "this-is-a-very-long-secret-key-at-least-32-bytes!", "test", Duration.ofHours(1));
    private final AuthContext context = AuthContext.ofRealm("api");

    @Test
    void testSchemeName() {
        var scheme = new BearerAuthScheme(jwtProvider);
        assertThat(scheme.schemeName()).isEqualTo("Bearer");
    }

    @Test
    void testAuthenticateWithJwt() {
        var scheme = new BearerAuthScheme(jwtProvider);
        String token = jwtProvider.generateToken("alice");
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
        assertThat(((AuthResult.Success) result).principal().getName()).isEqualTo("alice");
    }

    @Test
    void testAuthenticateWithTokenStore() {
        var tokenStore = new TokenStore();
        var stored = tokenStore.issueAccessToken("client", "bob", Set.of("read"));
        var scheme = new BearerAuthScheme(tokenStore);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + stored.token());
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    @Test
    void testAuthenticateNoHeader() {
        var scheme = new BearerAuthScheme(jwtProvider);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test
    void testAuthenticateInvalidToken() {
        var scheme = new BearerAuthScheme(jwtProvider);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void testExtractCredentials() {
        var scheme = new BearerAuthScheme(jwtProvider);
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer mytoken");
        var creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(AuthCredentials.Bearer.class);
        assertThat(((AuthCredentials.Bearer) creds).token()).isEqualTo("mytoken");
    }

    @Test
    void testExtractCredentialsEmpty() {
        var scheme = new BearerAuthScheme(jwtProvider);
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer ");
        var creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(AuthCredentials.None.class);
    }

    @Test
    void testChallenge() {
        var scheme = new BearerAuthScheme(jwtProvider);
        var response = HttpResponse.of(HttpStatus.UNAUTHORIZED);
        scheme.challenge(response, context);
        assertThat(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE))
                .contains("Bearer").contains("realm=\"api\"");
    }

    @Test
    void testEncodeToken() {
        assertThat(BearerAuthScheme.encodeToken("tok123")).isEqualTo("Bearer tok123");
    }

    @Test
    void testJwtFallsBackToTokenStore() {
        var tokenStore = new TokenStore();
        var stored = tokenStore.issueAccessToken("c", "charlie", Set.of());
        // JWT provider that won't validate this opaque token, but token store will
        var scheme = new BearerAuthScheme(jwtProvider, tokenStore);
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + stored.token());
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    @Test
    void testWrongScheme() {
        var scheme = new BearerAuthScheme(jwtProvider);
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
        var creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(AuthCredentials.None.class);
    }
}
