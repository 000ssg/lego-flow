package ssg.legoflow.http.auth.basic;

import ssg.legoflow.http.auth.*;
import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class BasicAuthSchemeTest {

    private BasicAuthScheme scheme;
    private InMemoryUserStore store;
    private AuthContext context;

    @BeforeEach
    void setUp() {
        store = new InMemoryUserStore()
                .addUser("alice", "password123", Set.of("admin"))
                .addUser("bob", "secret", Set.of("user"));
        scheme = new BasicAuthScheme(store);
        context = new AuthContext("test-realm", store, null);
    }

    @Test
    void testSchemeName() {
        assertThat(scheme.schemeName()).isEqualTo("Basic");
    }

    @Test
    void testSuccessfulAuthentication() {
        var request = createRequestWithBasicAuth("alice", "password123");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
        assertThat(((AuthResult.Success) result).principal().getName()).isEqualTo("alice");
    }

    @Test
    void testFailedAuthentication() {
        var request = createRequestWithBasicAuth("alice", "wrong");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void testNoAuthorizationHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test
    void testWrongSchemeHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer token123");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test
    void testExtractCredentials() {
        var request = createRequestWithBasicAuth("alice", "pass");
        var creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(AuthCredentials.Basic.class);
        var basic = (AuthCredentials.Basic) creds;
        assertThat(basic.username()).isEqualTo("alice");
        assertThat(basic.password()).isEqualTo("pass");
    }

    @Test
    void testExtractCredentialsNoHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        assertThat(scheme.extractCredentials(request)).isInstanceOf(AuthCredentials.None.class);
    }

    @Test
    void testExtractCredentialsMalformedBase64() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic !!!invalid!!!");
        assertThat(scheme.extractCredentials(request)).isInstanceOf(AuthCredentials.None.class);
    }

    @Test
    void testExtractCredentialsNoColon() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        String encoded = Base64.getEncoder().encodeToString("nocolon".getBytes(StandardCharsets.UTF_8));
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        assertThat(scheme.extractCredentials(request)).isInstanceOf(AuthCredentials.None.class);
    }

    @Test
    void testChallenge() {
        var response = HttpResponse.of(HttpStatus.UNAUTHORIZED);
        scheme.challenge(response, context);
        String wwwAuth = response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE);
        assertThat(wwwAuth).contains("Basic").contains("realm=\"test-realm\"").contains("charset=\"UTF-8\"");
    }

    @Test
    void testEncodeCredentials() {
        String encoded = BasicAuthScheme.encodeCredentials("user", "pass");
        assertThat(encoded).startsWith("Basic ");
        byte[] decoded = Base64.getDecoder().decode(encoded.substring(6));
        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo("user:pass");
    }

    @Test
    void testPasswordWithColon() {
        store.addUser("test", "pass:word");
        var request = createRequestWithBasicAuth("test", "pass:word");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    @Test
    void testEmptyPassword() {
        store.addUser("nopass", "");
        var request = createRequestWithBasicAuth("nopass", "");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    @Test
    void testUnknownUser() {
        var request = createRequestWithBasicAuth("unknown", "pass");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void testUnicodeCredentials() {
        store.addUser("user", "pässwörd");
        var request = createRequestWithBasicAuth("user", "pässwörd");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    private HttpRequest createRequestWithBasicAuth(String username, String password) {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION,
                BasicAuthScheme.encodeCredentials(username, password));
        return request;
    }
}
