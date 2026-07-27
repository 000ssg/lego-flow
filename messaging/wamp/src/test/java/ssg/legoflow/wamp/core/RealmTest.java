package ssg.legoflow.wamp.core;

import ssg.legoflow.wamp.core.realm.Realm;
import ssg.legoflow.wamp.core.realm.RealmManager;
import ssg.legoflow.wamp.demo.base.InMemoryTransport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RealmTest {

    @Test
    void testRealmCreation() {
        var realm = new Realm("test.realm");

        assertThat(realm.getName()).isEqualTo("test.realm");
        assertThat(realm.getBroker()).isNotNull();
        assertThat(realm.getDealer()).isNotNull();
        assertThat(realm.getSessionCount()).isZero();
    }

    @Test
    void testAddAndRemoveSession() {
        var realm = new Realm("test.realm");
        var pair = InMemoryTransport.createPair();

        var welcome = realm.addSession(pair[0]);

        assertThat(welcome.sessionId()).isPositive();
        assertThat(realm.getSessionCount()).isEqualTo(1);
        assertThat(realm.getSession(welcome.sessionId())).isNotNull();
        assertThat(realm.getSession(welcome.sessionId()).isEstablished()).isTrue();

        realm.removeSession(welcome.sessionId());
        assertThat(realm.getSessionCount()).isZero();
    }

    @Test
    void testRealmManagerCreatesAndRemoves() {
        var manager = new RealmManager();

        var realm1 = manager.createRealm("realm1");
        var realm2 = manager.createRealm("realm2");

        assertThat(manager.getRealmCount()).isEqualTo(2);
        assertThat(manager.getRealm("realm1")).isPresent();
        assertThat(manager.getRealm("realm1").get()).isSameAs(realm1);

        manager.removeRealm("realm1");
        assertThat(manager.getRealmCount()).isEqualTo(1);
        assertThat(manager.getRealm("realm1")).isEmpty();
    }

    @Test
    void testRealmManagerIdempotentCreate() {
        var manager = new RealmManager();

        var first = manager.createRealm("realm1");
        var second = manager.createRealm("realm1");

        assertThat(first).isSameAs(second);
        assertThat(manager.getRealmCount()).isEqualTo(1);
    }
}
