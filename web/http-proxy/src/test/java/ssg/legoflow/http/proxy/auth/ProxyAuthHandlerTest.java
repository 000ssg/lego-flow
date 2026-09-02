package ssg.legoflow.http.proxy.auth;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.ProxyHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class ProxyAuthHandlerTest {

    private ProxyAuthHandler handler;
    private BasicProxyAuth auth;

    @BeforeEach
    void setUp() {
        auth = new BasicProxyAuth("test-realm");
        auth.addUser("admin", "secret");
        handler = new ProxyAuthHandler(auth);
    }

    @Test
    void testIsAuthRequired407() {
        var response = HttpResponse.of(HttpStatus.PROXY_AUTHENTICATION_REQUIRED);
        assertThat(handler.isAuthRequired(response)).isTrue();
    }

    @Test
    void testIsAuthRequiredNot407() {
        var response = HttpResponse.of(HttpStatus.OK);
        assertThat(handler.isAuthRequired(response)).isFalse();
    }

    @Test
    void testHandleAuthSuccess() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(ProxyHeaders.PROXY_AUTHORIZATION,
                BasicProxyAuth.encodeCredentials("admin", "secret"));
        assertThat(handler.handleAuth(request)).isTrue();
    }

    @Test
    void testHandleAuthFailure() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        assertThat(handler.handleAuth(request)).isFalse();
    }

    @Test
    void testCreateChallenge() {
        var response = handler.createChallenge();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PROXY_AUTHENTICATION_REQUIRED);
    }

    @Test
    void testGetScheme() {
        assertThat(handler.getScheme()).isEqualTo("Basic");
    }

    @Test
    void testMaxRetries() {
        assertThat(handler.getMaxRetries()).isEqualTo(3);
        handler.setMaxRetries(5);
        assertThat(handler.getMaxRetries()).isEqualTo(5);
    }

    @Test
    void testGetAuthenticator() {
        assertThat(handler.getAuthenticator()).isSameAs(auth);
    }

    @Test
    void testIsAuthRequiredUnauthorized() {
        var response = HttpResponse.of(HttpStatus.UNAUTHORIZED);
        assertThat(handler.isAuthRequired(response)).isFalse(); // 401 != 407
    }

    @Test
    void testIsAuthRequiredForbidden() {
        var response = HttpResponse.of(HttpStatus.FORBIDDEN);
        assertThat(handler.isAuthRequired(response)).isFalse();
    }
}
