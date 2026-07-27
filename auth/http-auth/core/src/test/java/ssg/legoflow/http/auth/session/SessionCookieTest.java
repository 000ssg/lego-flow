package ssg.legoflow.http.auth.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SessionCookieTest {

    @Test
    void testDefaults() {
        var cookie = SessionCookie.defaults();
        assertThat(cookie.getName()).isEqualTo("LFSESSION");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo(SessionCookie.SameSite.LAX);
    }

    @Test
    void testBuildSetCookieHeader() {
        var cookie = SessionCookie.defaults();
        String header = cookie.buildSetCookieHeader("abc123");
        assertThat(header).contains("LFSESSION=abc123");
        assertThat(header).contains("Path=/");
        assertThat(header).contains("Secure");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("SameSite=Lax");
    }

    @Test
    void testBuildSetCookieHeaderWithDomain() {
        var cookie = new SessionCookie("SID", "/", "example.com", true, true,
                SessionCookie.SameSite.STRICT, 3600);
        String header = cookie.buildSetCookieHeader("xyz");
        assertThat(header).contains("Domain=example.com");
        assertThat(header).contains("Max-Age=3600");
        assertThat(header).contains("SameSite=Strict");
    }

    @Test
    void testBuildDeleteCookieHeader() {
        var cookie = SessionCookie.defaults();
        String header = cookie.buildDeleteCookieHeader();
        assertThat(header).contains("LFSESSION=");
        assertThat(header).contains("Max-Age=0");
    }

    @Test
    void testExtractSessionId() {
        var cookie = SessionCookie.defaults();
        assertThat(cookie.extractSessionId("LFSESSION=abc123")).isEqualTo("abc123");
        assertThat(cookie.extractSessionId("other=x; LFSESSION=abc123; more=y")).isEqualTo("abc123");
        assertThat(cookie.extractSessionId("other=x")).isNull();
        assertThat(cookie.extractSessionId(null)).isNull();
    }

    @Test
    void testNoneCookieNotSecure() {
        var cookie = new SessionCookie("SID", "/", null, false, false,
                SessionCookie.SameSite.NONE, -1);
        String header = cookie.buildSetCookieHeader("test");
        assertThat(header).doesNotContain("Secure");
        assertThat(header).doesNotContain("HttpOnly");
        assertThat(header).contains("SameSite=None");
    }

    @Test
    void testSameSiteValues() {
        assertThat(SessionCookie.SameSite.STRICT.value()).isEqualTo("Strict");
        assertThat(SessionCookie.SameSite.LAX.value()).isEqualTo("Lax");
        assertThat(SessionCookie.SameSite.NONE.value()).isEqualTo("None");
    }

    @Test
    void testSessionCookieWithMaxAge() {
        var cookie = new SessionCookie("SID", "/app", null, true, true,
                SessionCookie.SameSite.LAX, 7200);
        String header = cookie.buildSetCookieHeader("sess123");
        assertThat(header).contains("Max-Age=7200");
        assertThat(header).contains("Path=/app");
    }
}
