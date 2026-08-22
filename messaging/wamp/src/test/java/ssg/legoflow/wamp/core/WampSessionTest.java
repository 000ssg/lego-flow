package ssg.legoflow.wamp.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class WampSessionTest {

    @Test
    void testInitialState() {
        var session = new WampSession();

        assertThat(session.isEstablished()).isFalse();
        assertThat(session.getSubscriptions()).isEmpty();
        assertThat(session.getRegistrations()).isEmpty();
    }

    @Test
    void testEstablishSession() {
        var session = new WampSession();
        session.establish(42L, "realm1");

        assertThat(session.isEstablished()).isTrue();
        assertThat(session.getSessionId()).isEqualTo(42L);
        assertThat(session.getRealm()).isEqualTo("realm1");
    }

    @Test
    void testSubscribeAndUnsubscribe() {
        var session = new WampSession();
        session.establish(1L, "test");

        session.subscribe(100L, "topic.a");
        session.subscribe(101L, "topic.b");

        assertThat(session.getSubscriptions()).hasSize(2);
        assertThat(session.getSubscriptions()).containsEntry(100L, "topic.a");

        session.unsubscribe(100L);
        assertThat(session.getSubscriptions()).hasSize(1);
        assertThat(session.getSubscriptions()).doesNotContainKey(100L);
    }

    @Test
    void testRegisterAndUnregister() {
        var session = new WampSession();
        session.establish(1L, "test");

        session.register(200L, "com.example.proc");
        session.register(201L, "com.example.proc2");

        assertThat(session.getRegistrations()).hasSize(2);
        assertThat(session.getRegistrations()).containsEntry(200L, "com.example.proc");

        session.unregister(200L);
        assertThat(session.getRegistrations()).hasSize(1);
    }

    @Test
    void testCloseSession() {
        var session = new WampSession();
        session.establish(1L, "test");
        session.subscribe(100L, "topic.a");
        session.register(200L, "proc.a");

        session.close();

        assertThat(session.isEstablished()).isFalse();
        assertThat(session.getSubscriptions()).isEmpty();
        assertThat(session.getRegistrations()).isEmpty();
    }

    @Test
    void testSubscriptionsAreUnmodifiable() {
        var session = new WampSession();
        session.subscribe(1L, "topic");

        var subs = session.getSubscriptions();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> subs.put(2L, "other"));
    }
}
