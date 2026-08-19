package ssg.legoflow.wamp.core.auth;

import ssg.legoflow.wamp.core.WampSession;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for WampAuthorizer interface and role-based authorization.
 */
class WampAuthorizerTest {

    @Test
    void testAllowAllPermitsEverything() {
        var session = new WampSession();
        session.establish(1L, "realm1");

        assertThat(WampAuthorizer.ALLOW_ALL.canPublish(session, "any.topic")).isTrue();
        assertThat(WampAuthorizer.ALLOW_ALL.canSubscribe(session, "any.topic")).isTrue();
        assertThat(WampAuthorizer.ALLOW_ALL.canCall(session, "any.proc")).isTrue();
        assertThat(WampAuthorizer.ALLOW_ALL.canRegister(session, "any.proc")).isTrue();
    }

    @Test
    void testCustomAuthorizerRestrictsPublish() {
        var authorizer = new WampAuthorizer() {
            @Override public boolean canPublish(WampSession session, String topic) {
                return "admin".equals(session.getAuthRole());
            }
            @Override public boolean canSubscribe(WampSession session, String topic) { return true; }
            @Override public boolean canCall(WampSession session, String procedure) { return true; }
            @Override public boolean canRegister(WampSession session, String procedure) { return true; }
        };

        var admin = new WampSession();
        admin.establish(1L, "realm1");
        admin.setAuthRole("admin");

        var user = new WampSession();
        user.establish(2L, "realm1");
        user.setAuthRole("user");

        assertThat(authorizer.canPublish(admin, "topic")).isTrue();
        assertThat(authorizer.canPublish(user, "topic")).isFalse();
    }

    @Test
    void testCustomAuthorizerRestrictsTopicPattern() {
        var authorizer = new WampAuthorizer() {
            @Override public boolean canPublish(WampSession session, String topic) {
                return topic.startsWith("public.");
            }
            @Override public boolean canSubscribe(WampSession session, String topic) {
                return topic.startsWith("public.");
            }
            @Override public boolean canCall(WampSession session, String procedure) { return true; }
            @Override public boolean canRegister(WampSession session, String procedure) { return true; }
        };

        var session = new WampSession();
        session.establish(1L, "realm1");

        assertThat(authorizer.canPublish(session, "public.events")).isTrue();
        assertThat(authorizer.canPublish(session, "private.events")).isFalse();
        assertThat(authorizer.canSubscribe(session, "public.data")).isTrue();
        assertThat(authorizer.canSubscribe(session, "private.data")).isFalse();
    }
}
