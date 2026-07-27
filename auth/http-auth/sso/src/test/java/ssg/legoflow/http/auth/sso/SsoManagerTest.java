package ssg.legoflow.http.auth.sso;

import ssg.legoflow.http.auth.AuthPrincipal;
import ssg.legoflow.http.auth.token.JwtTokenProvider;
import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class SsoManagerTest {

    private SsoManager manager;
    private SsoConfig config;
    private JwtTokenProvider jwtProvider;

    @BeforeEach
    void setUp() {
        config = new SsoConfig("example.com", "SSO_TOKEN", Duration.ofHours(1),
                Set.of("https://app1.example.com"), true);
        jwtProvider = JwtTokenProvider.hmac256(
                "this-is-a-very-long-secret-key-at-least-32-bytes!", "sso-issuer", Duration.ofHours(2));
        manager = new SsoManager(config, jwtProvider);
    }

    @Test
    void testLogin() {
        var principal = new AuthPrincipal("alice", Set.of("admin"), null);
        var response = HttpResponse.of(HttpStatus.OK);
        var session = manager.login(principal, response);

        assertThat(session).isNotNull();
        assertThat(session.getPrincipal().getName()).isEqualTo("alice");
        assertThat(manager.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    void testLoginSetsCookie() {
        var principal = new AuthPrincipal("alice", Set.of(), null);
        var response = HttpResponse.of(HttpStatus.OK);
        manager.login(principal, response);

        String cookie = response.getHeaders().get("set-cookie");
        assertThat(cookie).isNotNull();
        assertThat(cookie).contains("SSO_TOKEN=");
        assertThat(cookie).contains("Domain=example.com");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("Secure");
    }

    @Test
    void testLoginWithoutSecureCookies() {
        var insecureConfig = new SsoConfig("example.com", "SSO", Duration.ofHours(1), null, false);
        var insecureManager = new SsoManager(insecureConfig, jwtProvider);
        var principal = new AuthPrincipal("bob", Set.of(), null);
        var response = HttpResponse.of(HttpStatus.OK);
        insecureManager.login(principal, response);

        String cookie = response.getHeaders().get("set-cookie");
        assertThat(cookie).doesNotContain("Secure");
    }

    @Test
    void testValidateSession() {
        var principal = new AuthPrincipal("alice", Set.of("admin"), null);
        var loginResponse = HttpResponse.of(HttpStatus.OK);
        manager.login(principal, loginResponse);

        // Extract cookie token from set-cookie
        String setCookie = loginResponse.getHeaders().get("set-cookie");
        String token = extractTokenFromSetCookie(setCookie, "SSO_TOKEN");

        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set("cookie", "SSO_TOKEN=" + token);
        var session = manager.validateSession(request);

        assertThat(session).isPresent();
        assertThat(session.get().getPrincipal().getName()).isEqualTo("alice");
    }

    @Test
    void testValidateSessionNoCookie() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var session = manager.validateSession(request);
        assertThat(session).isEmpty();
    }

    @Test
    void testValidateSessionInvalidToken() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set("cookie", "SSO_TOKEN=invalid-jwt-token");
        var session = manager.validateSession(request);
        assertThat(session).isEmpty();
    }

    @Test
    void testValidateSessionWrongCookieName() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set("cookie", "OTHER_COOKIE=some-value");
        var session = manager.validateSession(request);
        assertThat(session).isEmpty();
    }

    @Test
    void testLogout() {
        var principal = new AuthPrincipal("alice", Set.of(), null);
        var loginResponse = HttpResponse.of(HttpStatus.OK);
        var session = manager.login(principal, loginResponse);
        session.addAuthenticatedService("https://app1.example.com");

        String setCookie = loginResponse.getHeaders().get("set-cookie");
        String token = extractTokenFromSetCookie(setCookie, "SSO_TOKEN");

        var logoutRequest = HttpRequest.of(HttpMethod.POST, "/logout");
        logoutRequest.getHeaders().set("cookie", "SSO_TOKEN=" + token);
        var logoutResponse = HttpResponse.of(HttpStatus.OK);

        Set<String> services = manager.logout(logoutRequest, logoutResponse);
        assertThat(services).contains("https://app1.example.com");
        assertThat(manager.getActiveSessionCount()).isEqualTo(0);
    }

    @Test
    void testLogoutClearsCookie() {
        var principal = new AuthPrincipal("alice", Set.of(), null);
        var loginResponse = HttpResponse.of(HttpStatus.OK);
        manager.login(principal, loginResponse);

        String setCookie = loginResponse.getHeaders().get("set-cookie");
        String token = extractTokenFromSetCookie(setCookie, "SSO_TOKEN");

        var logoutRequest = HttpRequest.of(HttpMethod.POST, "/logout");
        logoutRequest.getHeaders().set("cookie", "SSO_TOKEN=" + token);
        var logoutResponse = HttpResponse.of(HttpStatus.OK);

        manager.logout(logoutRequest, logoutResponse);

        String deleteCookie = logoutResponse.getHeaders().get("set-cookie");
        assertThat(deleteCookie).contains("Max-Age=0");
    }

    @Test
    void testLogoutNoCookie() {
        var request = HttpRequest.of(HttpMethod.POST, "/logout");
        var response = HttpResponse.of(HttpStatus.OK);
        var services = manager.logout(request, response);
        assertThat(services).isEmpty();
    }

    @Test
    void testGetActiveSessionCount() {
        assertThat(manager.getActiveSessionCount()).isEqualTo(0);
        var response1 = HttpResponse.of(HttpStatus.OK);
        manager.login(new AuthPrincipal("alice", Set.of(), null), response1);
        assertThat(manager.getActiveSessionCount()).isEqualTo(1);
        var response2 = HttpResponse.of(HttpStatus.OK);
        manager.login(new AuthPrincipal("bob", Set.of(), null), response2);
        assertThat(manager.getActiveSessionCount()).isEqualTo(2);
    }

    @Test
    void testCleanExpiredSessions() throws InterruptedException {
        var shortConfig = new SsoConfig("example.com", "SSO", Duration.ofMillis(1), null, false);
        var shortManager = new SsoManager(shortConfig, jwtProvider);
        var response = HttpResponse.of(HttpStatus.OK);
        shortManager.login(new AuthPrincipal("alice", Set.of(), null), response);
        Thread.sleep(10);
        int cleaned = shortManager.cleanExpiredSessions();
        assertThat(cleaned).isEqualTo(1);
        assertThat(shortManager.getActiveSessionCount()).isEqualTo(0);
    }

    @Test
    void testCleanExpiredSessionsNoneExpired() {
        var response = HttpResponse.of(HttpStatus.OK);
        manager.login(new AuthPrincipal("alice", Set.of(), null), response);
        int cleaned = manager.cleanExpiredSessions();
        assertThat(cleaned).isEqualTo(0);
        assertThat(manager.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    void testClose() {
        var response = HttpResponse.of(HttpStatus.OK);
        manager.login(new AuthPrincipal("alice", Set.of(), null), response);
        manager.login(new AuthPrincipal("bob", Set.of(), null), HttpResponse.of(HttpStatus.OK));
        manager.close();
        assertThat(manager.getActiveSessionCount()).isEqualTo(0);
    }

    @Test
    void testGetConfig() {
        assertThat(manager.getConfig()).isEqualTo(config);
    }

    @Test
    void testNullConfigThrows() {
        assertThatThrownBy(() -> new SsoManager(null, jwtProvider))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullTokenProviderThrows() {
        assertThatThrownBy(() -> new SsoManager(config, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testValidateInvalidatedSession() {
        var principal = new AuthPrincipal("alice", Set.of(), null);
        var loginResponse = HttpResponse.of(HttpStatus.OK);
        var session = manager.login(principal, loginResponse);
        session.invalidate();

        String setCookie = loginResponse.getHeaders().get("set-cookie");
        String token = extractTokenFromSetCookie(setCookie, "SSO_TOKEN");

        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set("cookie", "SSO_TOKEN=" + token);
        var result = manager.validateSession(request);
        assertThat(result).isEmpty();
    }

    private String extractTokenFromSetCookie(String setCookie, String cookieName) {
        String prefix = cookieName + "=";
        int start = setCookie.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        int end = setCookie.indexOf(';', start);
        return end < 0 ? setCookie.substring(start) : setCookie.substring(start, end);
    }
}
