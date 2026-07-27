package ssg.legoflow.http.auth.sso;

import ssg.legoflow.http.auth.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class SsoSessionTest {

    private AuthPrincipal principal;
    private SsoSession session;

    @BeforeEach
    void setUp() {
        principal = new AuthPrincipal("alice", Set.of("admin"), null);
        session = new SsoSession("sess-001", principal);
    }

    @Test
    void testGetId() {
        assertThat(session.getId()).isEqualTo("sess-001");
    }

    @Test
    void testGetPrincipal() {
        assertThat(session.getPrincipal()).isEqualTo(principal);
        assertThat(session.getPrincipal().getName()).isEqualTo("alice");
    }

    @Test
    void testCreatedAt() {
        assertThat(session.getCreatedAt()).isNotNull();
    }

    @Test
    void testLastAccessedAt() {
        assertThat(session.getLastAccessedAt()).isNotNull();
        assertThat(session.getLastAccessedAt()).isEqualTo(session.getCreatedAt());
    }

    @Test
    void testTouch() throws InterruptedException {
        var before = session.getLastAccessedAt();
        Thread.sleep(5);
        session.touch();
        assertThat(session.getLastAccessedAt()).isAfter(before);
    }

    @Test
    void testInvalidate() {
        assertThat(session.isInvalidated()).isFalse();
        session.invalidate();
        assertThat(session.isInvalidated()).isTrue();
    }

    @Test
    void testIsExpiredWhenInvalidated() {
        session.invalidate();
        assertThat(session.isExpired(3600)).isTrue();
    }

    @Test
    void testIsExpiredWhenTimedOut() throws InterruptedException {
        Thread.sleep(5);
        assertThat(session.isExpired(0)).isTrue();
    }

    @Test
    void testIsNotExpired() {
        assertThat(session.isExpired(3600)).isFalse();
    }

    @Test
    void testAddAuthenticatedService() {
        session.addAuthenticatedService("https://app1.example.com");
        assertThat(session.getAuthenticatedServices()).containsExactly("https://app1.example.com");
    }

    @Test
    void testAddMultipleServices() {
        session.addAuthenticatedService("https://app1.example.com");
        session.addAuthenticatedService("https://app2.example.com");
        assertThat(session.getAuthenticatedServices()).hasSize(2);
    }

    @Test
    void testAuthenticatedServicesUnmodifiable() {
        session.addAuthenticatedService("svc1");
        assertThatThrownBy(() -> session.getAuthenticatedServices().add("svc2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testSetAttribute() {
        session.setAttribute("key", "value");
        assertThat(session.<String>getAttribute("key")).isEqualTo("value");
    }

    @Test
    void testGetAttributeNull() {
        assertThat(session.<String>getAttribute("nonexistent")).isNull();
    }

    @Test
    void testInvalidateClearsAttributes() {
        session.setAttribute("key", "value");
        session.invalidate();
        assertThat(session.<String>getAttribute("key")).isNull();
    }

    @Test
    void testNullIdThrows() {
        assertThatThrownBy(() -> new SsoSession(null, principal))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullPrincipalThrows() {
        assertThatThrownBy(() -> new SsoSession("id", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testAddServiceTouchesSession() throws InterruptedException {
        var before = session.getLastAccessedAt();
        Thread.sleep(5);
        session.addAuthenticatedService("svc");
        assertThat(session.getLastAccessedAt()).isAfter(before);
    }
}
