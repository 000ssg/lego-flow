package ssg.legoflow.http.auth.session;

import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SessionManagerTest {

    private SessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new SessionManager(new InMemorySessionStore());
    }

    @Test
    void testCreateSession() {
        var response = HttpResponse.of(HttpStatus.OK);
        var session = manager.createSession(response);
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotEmpty();
        assertThat(response.getHeaders().get("set-cookie")).contains("LFSESSION=");
    }

    @Test
    void testGetSessionFromCookie() {
        var response = HttpResponse.of(HttpStatus.OK);
        var session = manager.createSession(response);
        String setCookie = response.getHeaders().get("set-cookie");
        // Extract just the session ID value
        String sessionId = setCookie.split(";")[0].split("=", 2)[1];

        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set("cookie", "LFSESSION=" + sessionId);
        var retrieved = manager.getSession(request);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getId()).isEqualTo(session.getId());
    }

    @Test
    void testGetSessionNoCookie() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        assertThat(manager.getSession(request)).isEmpty();
    }

    @Test
    void testGetOrCreateSession() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        var response = HttpResponse.of(HttpStatus.OK);
        var session = manager.getOrCreateSession(request, response);
        assertThat(session).isNotNull();
    }

    @Test
    void testDestroySession() {
        var response = HttpResponse.of(HttpStatus.OK);
        var session = manager.createSession(response);
        String setCookie = response.getHeaders().get("set-cookie");
        String sessionId = setCookie.split(";")[0].split("=", 2)[1];

        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set("cookie", "LFSESSION=" + sessionId);

        var destroyResponse = HttpResponse.of(HttpStatus.OK);
        manager.destroySession(request, destroyResponse);

        assertThat(manager.getSession(request)).isEmpty();
    }

    @Test
    void testActiveSessionCount() {
        var r1 = HttpResponse.of(HttpStatus.OK);
        var r2 = HttpResponse.of(HttpStatus.OK);
        manager.createSession(r1);
        manager.createSession(r2);
        assertThat(manager.getActiveSessionCount()).isEqualTo(2);
    }

    @Test
    void testSessionTimeout() {
        assertThat(manager.getSessionTimeoutSeconds()).isEqualTo(1800);
    }

    @Test
    void testCookieConfig() {
        assertThat(manager.getCookieConfig().getName()).isEqualTo("LFSESSION");
    }

    @Test
    void testCleanExpiredSessions() {
        // Default timeout is 30 min, newly created sessions are not expired
        var r = HttpResponse.of(HttpStatus.OK);
        manager.createSession(r);
        manager.cleanExpiredSessions();
        assertThat(manager.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    void testClose() {
        manager.close();
        // No exception expected
    }
}
