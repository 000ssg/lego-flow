package ssg.legoflow.wamp.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class SessionStateTest {

    @Test
    void testInitialStateIsPending() {
        var session = new WampSession();
        assertThat(session.getState()).isEqualTo(SessionState.PENDING);
    }

    @Test
    void testEstablishTransitionsToEstablished() {
        var session = new WampSession();
        session.establish(42L, "realm1");
        assertThat(session.getState()).isEqualTo(SessionState.ESTABLISHED);
    }

    @Test
    void testCloseTransitionsToClosed() {
        var session = new WampSession();
        session.establish(1L, "test");
        session.close();
        assertThat(session.getState()).isEqualTo(SessionState.CLOSED);
    }

    @Test
    void testIsActiveForEstablished() {
        assertThat(SessionState.ESTABLISHED.isActive()).isTrue();
        assertThat(SessionState.CLOSING.isActive()).isTrue();
        assertThat(SessionState.PENDING.isActive()).isFalse();
        assertThat(SessionState.CLOSED.isActive()).isFalse();
    }

    @Test
    void testIsEstablishedBackwardCompatibility() {
        var session = new WampSession();
        assertThat(session.isEstablished()).isFalse();

        session.establish(1L, "test");
        assertThat(session.isEstablished()).isTrue();

        session.close();
        assertThat(session.isEstablished()).isFalse();
    }

    @Test
    void testStateOrder() {
        // Verify enum ordering matches lifecycle: PENDING → ESTABLISHED → CLOSING → CLOSED
        assertThat(SessionState.PENDING.ordinal()).isZero();
        assertThat(SessionState.ESTABLISHED.ordinal()).isEqualTo(1);
        assertThat(SessionState.CLOSING.ordinal()).isEqualTo(2);
        assertThat(SessionState.CLOSED.ordinal()).isEqualTo(3);
    }
}
