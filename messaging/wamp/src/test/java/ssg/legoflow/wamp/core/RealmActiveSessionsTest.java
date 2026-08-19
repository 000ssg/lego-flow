package ssg.legoflow.wamp.core;

import ssg.legoflow.wamp.core.realm.Realm;
import ssg.legoflow.wamp.core.transport.InMemoryTransport;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class RealmActiveSessionsTest {

    @Test
    void testGetActiveSessionsEmpty() {
        var realm = new Realm("test");
        assertThat(realm.getActiveSessions()).isEmpty();
    }

    @Test
    void testGetActiveSessionsAfterAdd() {
        var realm = new Realm("test");
        var pair = InMemoryTransport.createPair();
        var welcome = realm.addSession(pair[0]);

        var sessions = realm.getActiveSessions();
        assertThat(sessions).hasSize(1);
        assertThat(sessions).containsKey(welcome.sessionId());
        assertThat(sessions.get(welcome.sessionId()).getRealm()).isEqualTo("test");
    }

    @Test
    void testGetActiveSessionsAfterRemove() {
        var realm = new Realm("test");
        var pair = InMemoryTransport.createPair();
        var welcome = realm.addSession(pair[0]);

        realm.removeSession(welcome.sessionId());
        assertThat(realm.getActiveSessions()).isEmpty();
    }

    @Test
    void testGetActiveSessionsReturnsSnapshot() {
        var realm = new Realm("test");
        var pair1 = InMemoryTransport.createPair();
        var pair2 = InMemoryTransport.createPair();
        var w1 = realm.addSession(pair1[0]);
        var w2 = realm.addSession(pair2[0]);

        var sessions = realm.getActiveSessions();
        assertThat(sessions).hasSize(2);

        // Remove a session after getting the snapshot
        realm.removeSession(w1.sessionId());

        // Snapshot is unaffected (it's a copy)
        assertThat(sessions).hasSize(2);
        // But the live count is updated
        assertThat(realm.getSessionCount()).isEqualTo(1);
    }

    @Test
    void testGetActiveSessionsIsUnmodifiable() {
        var realm = new Realm("test");
        var pair = InMemoryTransport.createPair();
        realm.addSession(pair[0]);

        var sessions = realm.getActiveSessions();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> sessions.put(999L, new WampSession()));
    }
}
