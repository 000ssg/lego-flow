package ssg.legoflow.wamp.core;

import ssg.legoflow.wamp.core.router.WampRouter;
import ssg.legoflow.wamp.core.transport.InMemoryTransport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for session meta events and meta procedures.
 */
class SessionMetaTest {

    @Test
    void testSessionJoinedTracksSession() {
        var router = new WampRouter();
        var session = new WampSession();
        session.establish(1L, "realm1");
        session.setAuthId("user1");
        session.setAuthRole("admin");

        router.sessionJoined(session);

        assertThat(router.getActiveSessionCount()).isEqualTo(1);
        assertThat(router.getActiveSessionIds()).containsExactly(1L);
    }

    @Test
    void testSessionLeftRemovesSession() {
        var router = new WampRouter();
        var session = new WampSession();
        session.establish(1L, "realm1");
        router.sessionJoined(session);

        router.sessionLeft(1L);

        assertThat(router.getActiveSessionCount()).isZero();
    }

    @Test
    void testMetaSessionCountProcedure() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        var s1 = new WampSession(); s1.establish(1L, "r");
        var s2 = new WampSession(); s2.establish(2L, "r");
        router.sessionJoined(s1);
        router.sessionJoined(s2);

        router.route(new WampMessage.Call(10L, Map.of(), "wamp.session.count", List.of()), pair[0]);

        var result = (WampMessage.Result) pair[1].receive();
        assertThat(((Number) result.args().getFirst()).intValue()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMetaSessionListProcedure() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        var s1 = new WampSession(); s1.establish(10L, "r");
        var s2 = new WampSession(); s2.establish(20L, "r");
        router.sessionJoined(s1);
        router.sessionJoined(s2);

        router.route(new WampMessage.Call(1L, Map.of(), "wamp.session.list", List.of()), pair[0]);

        var result = (WampMessage.Result) pair[1].receive();
        var sessionIds = (List<Object>) result.args().getFirst();
        assertThat(sessionIds).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMetaSessionGetProcedure() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        var session = new WampSession();
        session.establish(42L, "realm1");
        session.setAuthId("testuser");
        session.setAuthRole("frontend");
        session.setAuthMethod("ticket");
        router.sessionJoined(session);

        router.route(new WampMessage.Call(1L, Map.of(), "wamp.session.get", List.of(42L)), pair[0]);

        var result = (WampMessage.Result) pair[1].receive();
        var details = (Map<String, Object>) result.args().getFirst();
        assertThat(((Number) details.get("session")).longValue()).isEqualTo(42L);
        assertThat(details.get("authid")).isEqualTo("testuser");
        assertThat(details.get("authrole")).isEqualTo("frontend");
    }

    @Test
    void testMetaOnJoinEventPublished() {
        var router = new WampRouter();
        var subPair = InMemoryTransport.createPair();

        // Subscribe to meta events
        router.route(new WampMessage.Subscribe(1L, Map.of(), "wamp.session.on_join"), subPair[0]);
        subPair[1].receive(); // consume Subscribed

        // Trigger join
        var session = new WampSession();
        session.establish(99L, "realm1");
        session.setAuthId("newuser");
        router.sessionJoined(session);

        // Should receive meta event
        var event = subPair[1].receive();
        assertThat(event).isInstanceOf(WampMessage.Event.class);
    }

    @Test
    void testMetaOnLeaveEventPublished() {
        var router = new WampRouter();
        var subPair = InMemoryTransport.createPair();

        router.route(new WampMessage.Subscribe(1L, Map.of(), "wamp.session.on_leave"), subPair[0]);
        subPair[1].receive(); // consume Subscribed

        var session = new WampSession();
        session.establish(99L, "realm1");
        router.sessionJoined(session);
        router.sessionLeft(99L);

        var event = subPair[1].receive();
        assertThat(event).isInstanceOf(WampMessage.Event.class);
    }
}
