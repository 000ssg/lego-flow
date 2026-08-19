package ssg.legoflow.wamp.demo.base;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSession;
import ssg.legoflow.wamp.core.realm.Realm;
import ssg.legoflow.wamp.core.realm.RealmManager;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
class SessionLifecycleDemoTest {

    @Test
    void testFullSessionLifecycle() {
        var pair = InMemoryTransport.createPair();
        var clientTransport = pair[0];
        var routerTransport = pair[1];

        // Client sends HELLO
        clientTransport.send(new WampMessage.Hello("realm1", Map.of("roles", Map.of())));

        // Router receives HELLO and creates session
        var hello = (WampMessage.Hello) routerTransport.receive();
        assertThat(hello.realm()).isEqualTo("realm1");

        var realm = new Realm("realm1");
        var welcome = realm.addSession(routerTransport);
        routerTransport.send(welcome);

        // Client receives WELCOME
        var welcomeMsg = (WampMessage.Welcome) clientTransport.receive();
        assertThat(welcomeMsg.sessionId()).isPositive();

        // Track session on client side
        var session = new WampSession();
        session.establish(welcomeMsg.sessionId(), "realm1");
        assertThat(session.isEstablished()).isTrue();

        // Client sends GOODBYE
        clientTransport.send(new WampMessage.Goodbye(Map.of(), "wamp.close.normal"));
        var goodbye = (WampMessage.Goodbye) routerTransport.receive();
        assertThat(goodbye.reason()).isEqualTo("wamp.close.normal");

        // Router responds with GOODBYE
        realm.removeSession(welcomeMsg.sessionId());
        routerTransport.send(new WampMessage.Goodbye(Map.of(), "wamp.close.normal"));

        var goodbyeResponse = (WampMessage.Goodbye) clientTransport.receive();
        session.close();

        assertThat(session.isEstablished()).isFalse();
        assertThat(realm.getSessionCount()).isZero();
    }

    @Test
    void testMultipleSessionsInRealm() {
        var realm = new Realm("multi");

        var pair1 = InMemoryTransport.createPair();
        var pair2 = InMemoryTransport.createPair();

        var welcome1 = realm.addSession(pair1[0]);
        var welcome2 = realm.addSession(pair2[0]);

        assertThat(realm.getSessionCount()).isEqualTo(2);
        assertThat(welcome1.sessionId()).isNotEqualTo(welcome2.sessionId());

        realm.removeSession(welcome1.sessionId());
        assertThat(realm.getSessionCount()).isEqualTo(1);
    }

    @Test
    void testSessionWithRealmManager() {
        var manager = new RealmManager();
        var realm = manager.createRealm("managed.realm");

        var pair = InMemoryTransport.createPair();
        var welcome = realm.addSession(pair[0]);

        assertThat(manager.getRealm("managed.realm")).isPresent();
        assertThat(realm.getSessionCount()).isEqualTo(1);

        realm.removeSession(welcome.sessionId());
        assertThat(realm.getSessionCount()).isZero();
    }

    @Test
    void testAbortDuringHandshake() {
        var pair = InMemoryTransport.createPair();
        var clientTransport = pair[0];
        var routerTransport = pair[1];

        // Client sends HELLO to non-existent realm
        clientTransport.send(new WampMessage.Hello("nonexistent", Map.of()));

        var hello = (WampMessage.Hello) routerTransport.receive();

        // Router aborts
        routerTransport.send(new WampMessage.Abort(Map.of(), "wamp.error.no_such_realm"));

        var abort = (WampMessage.Abort) clientTransport.receive();
        assertThat(abort.reason()).isEqualTo("wamp.error.no_such_realm");
    }
}
