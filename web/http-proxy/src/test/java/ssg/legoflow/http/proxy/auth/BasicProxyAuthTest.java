package ssg.legoflow.http.proxy.auth;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.ProxyHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.assertj.core.api.Assertions.assertThat;
class BasicProxyAuthTest {

    private BasicProxyAuth auth;

    @BeforeEach
    void setUp() {
        auth = new BasicProxyAuth("test-realm");
        auth.addUser("admin", "secret");
        auth.addUser("user", "pass");
    }

    @Test
    void testValidCredentials() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(ProxyHeaders.PROXY_AUTHORIZATION,
                BasicProxyAuth.encodeCredentials("admin", "secret"));
        assertThat(auth.authenticate(request)).isTrue();
    }

    @Test
    void testInvalidPassword() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(ProxyHeaders.PROXY_AUTHORIZATION,
                BasicProxyAuth.encodeCredentials("admin", "wrong"));
        assertThat(auth.authenticate(request)).isFalse();
    }

    @Test
    void testUnknownUser() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(ProxyHeaders.PROXY_AUTHORIZATION,
                BasicProxyAuth.encodeCredentials("unknown", "pass"));
        assertThat(auth.authenticate(request)).isFalse();
    }

    @Test
    void testMissingHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(auth.authenticate(request)).isFalse();
    }

    @Test
    void testNonBasicScheme() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(ProxyHeaders.PROXY_AUTHORIZATION, "Bearer token123");
        assertThat(auth.authenticate(request)).isFalse();
    }

    @Test
    void testInvalidBase64() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(ProxyHeaders.PROXY_AUTHORIZATION, "Basic !!!invalid!!!");
        assertThat(auth.authenticate(request)).isFalse();
    }

    @Test
    void testCreateChallenge() {
        var response = auth.createChallenge();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PROXY_AUTHENTICATION_REQUIRED);
        assertThat(response.getHeaders().get(ProxyHeaders.PROXY_AUTHENTICATE))
                .contains("Basic realm=\"test-realm\"");
    }

    @Test
    void testGetScheme() {
        assertThat(auth.getScheme()).isEqualTo("Basic");
    }

    @Test
    void testGetRealm() {
        assertThat(auth.getRealm()).isEqualTo("test-realm");
    }

    @Test
    void testAddAndRemoveUser() {
        auth.addUser("newuser", "newpass");
        assertThat(auth.getUserCount()).isEqualTo(3);
        auth.removeUser("newuser");
        assertThat(auth.getUserCount()).isEqualTo(2);
    }

    @Test
    void testEncodeCredentials() {
        String encoded = BasicProxyAuth.encodeCredentials("admin", "secret");
        assertThat(encoded).startsWith("Basic ");
        String decoded = new String(Base64.getDecoder().decode(encoded.substring(6)),
                StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("admin:secret");
    }

    @Test
    void testCredentialsWithColonInPassword() {
        auth.addUser("user", "pass:word:with:colons");
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(ProxyHeaders.PROXY_AUTHORIZATION,
                BasicProxyAuth.encodeCredentials("user", "pass:word:with:colons"));
        assertThat(auth.authenticate(request)).isTrue();
    }

    @Test
    void testMissingColonInCredentials() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        String noColon = Base64.getEncoder().encodeToString("nocolon".getBytes(StandardCharsets.UTF_8));
        request.getHeaders().set(ProxyHeaders.PROXY_AUTHORIZATION, "Basic " + noColon);
        assertThat(auth.authenticate(request)).isFalse();
    }
}
