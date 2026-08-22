package ssg.legoflow.wamp.core;

import ssg.legoflow.wamp.core.realm.Realm;
import ssg.legoflow.wamp.core.router.WampRouter;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for virtual session creation and session lifecycle hooks.
 */
class VirtualSessionTest {

    @Test
    void realm_addVirtualSession_createsSession() {
        var realm = new Realm("test");
        long sessionId = realm.addVirtualSession("user-1", "admin", "ticket");

        var session = realm.getSession(sessionId);
        assertThat(session).isNotNull();
        assertThat(session.getSessionId()).isEqualTo(sessionId);
        assertThat(session.getAuthId()).isEqualTo("user-1");
        assertThat(session.getAuthRole()).isEqualTo("admin");
        assertThat(session.getAuthMethod()).isEqualTo("ticket");
        assertThat(session.getState()).isEqualTo(SessionState.ESTABLISHED);
    }

    @Test
    void realm_addVirtualSession_appearsInActiveSessions() {
        var realm = new Realm("test");
        long id1 = realm.addVirtualSession("user-1", "role-a", "ticket");
        long id2 = realm.addVirtualSession("user-2", "role-b", null);

        var sessions = realm.getActiveSessions();
        assertThat(sessions).hasSize(2);
        assertThat(sessions.keySet()).containsExactlyInAnyOrder(id1, id2);
    }

    @Test
    void realm_addVirtualSession_withNullAuth() {
        var realm = new Realm("test");
        long sessionId = realm.addVirtualSession(null, null, null);

        var session = realm.getSession(sessionId);
        assertThat(session).isNotNull();
        assertThat(session.getAuthId()).isNull();
        assertThat(session.getAuthRole()).isNull();
        assertThat(session.getAuthMethod()).isNull();
    }

    @Test
    void realm_addVirtualSession_uniqueIds() {
        var realm = new Realm("test");
        long id1 = realm.addVirtualSession("a", null, null);
        long id2 = realm.addVirtualSession("b", null, null);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void realm_removeSession_removesVirtualSession() {
        var realm = new Realm("test");
        long sessionId = realm.addVirtualSession("user-1", "admin", "ticket");

        assertThat(realm.getSession(sessionId)).isNotNull();
        realm.removeSession(sessionId);
        assertThat(realm.getSession(sessionId)).isNull();
    }

    @Test
    void router_sessionLeaveConsumer_calledOnSessionLeft() {
        var router = new WampRouter();
        var leftSessions = new ArrayList<Long>();
        router.addSessionLeaveConsumer(id -> leftSessions.add(id));

        router.sessionJoined(new WampSession()); // dummy session
        var dummy = new WampSession();
        dummy.establish(42L, "test");
        router.sessionJoined(dummy);

        router.sessionLeft(42L);
        assertThat(leftSessions).containsExactly(42L);
    }

    @Test
    void router_sessionLeaveConsumer_multipleListeners() {
        var router = new WampRouter();
        var listener1 = new AtomicLong(-1);
        var listener2 = new AtomicLong(-1);

        router.addSessionLeaveConsumer(id -> listener1.set(id));
        router.addSessionLeaveConsumer(id -> listener2.set(id));

        var dummy = new WampSession();
        dummy.establish(99L, "test");
        router.sessionJoined(dummy);

        router.sessionLeft(99L);
        assertThat(listener1.get()).isEqualTo(99L);
        assertThat(listener2.get()).isEqualTo(99L);
    }
}
