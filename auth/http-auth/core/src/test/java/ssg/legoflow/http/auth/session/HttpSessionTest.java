package ssg.legoflow.http.auth.session;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class HttpSessionTest {

    @Test
    void testCreateSession() {
        var session = new HttpSession("test-id");
        assertThat(session.getId()).isEqualTo("test-id");
        assertThat(session.isInvalidated()).isFalse();
        assertThat(session.getCreationTime()).isNotNull();
        assertThat(session.getLastAccessTime()).isNotNull();
    }

    @Test
    void testSetAndGetAttribute() {
        var session = new HttpSession("id");
        session.setAttribute("key", "value");
        assertThat(session.<String>getAttribute("key")).isEqualTo("value");
    }

    @Test
    void testRemoveAttribute() {
        var session = new HttpSession("id");
        session.setAttribute("key", "value");
        session.removeAttribute("key");
        assertThat(session.<String>getAttribute("key")).isNull();
    }

    @Test
    void testGetAttributeNames() {
        var session = new HttpSession("id");
        session.setAttribute("a", 1);
        session.setAttribute("b", 2);
        assertThat(session.getAttributeNames()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void testInvalidate() {
        var session = new HttpSession("id");
        session.setAttribute("key", "value");
        session.invalidate();
        assertThat(session.isInvalidated()).isTrue();
    }

    @Test
    void testInvalidatedSessionThrows() {
        var session = new HttpSession("id");
        session.invalidate();
        assertThatThrownBy(() -> session.setAttribute("key", "value"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testIsExpired() {
        var session = new HttpSession("id");
        // Session should not be expired with a reasonable timeout
        assertThat(session.isExpired(3600)).isFalse();
        // Should be expired with 0 timeout (effectively immediately)
        assertThat(session.isExpired(0)).isTrue();
    }

    @Test
    void testTouch() {
        var session = new HttpSession("id");
        var before = session.getLastAccessTime();
        session.touch();
        assertThat(session.getLastAccessTime()).isAfterOrEqualTo(before);
    }

    @Test
    void testToStringContainsId() {
        var session = new HttpSession("my-session");
        assertThat(session.toString()).contains("my-session");
    }

    @Test
    void testNullIdThrows() {
        assertThatThrownBy(() -> new HttpSession(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInvalidatedSessionExpired() {
        var session = new HttpSession("id");
        session.invalidate();
        assertThat(session.isExpired(Long.MAX_VALUE)).isTrue();
    }
}
