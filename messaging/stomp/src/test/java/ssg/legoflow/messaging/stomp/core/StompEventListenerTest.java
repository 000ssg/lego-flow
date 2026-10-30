package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class StompEventListenerTest {

    @Test void testNoOpDefault() {
        var listener = StompEventListener.NO_OP;
        listener.onEvent(StompEventListener.EventType.SESSION_CONNECTED, "client1", "detail");
        listener.onEvent(StompEventListener.EventType.SESSION_DISCONNECTED, "client1", "detail");
        listener.onEvent(StompEventListener.EventType.MESSAGE_DELIVERED, "client1", "detail");
        listener.onEvent(StompEventListener.EventType.TRANSACTION_COMMITTED, "client1", "detail");
        listener.onEvent(StompEventListener.EventType.TRANSACTION_ABORTED, "client1", "detail");
    }

    @Test void testLatchOnFirstConnect() throws Exception {
        var latch = new CountDownLatch(1);
        var listener = StompEventListener.latchOnFirst(latch, StompEventListener.EventType.SESSION_CONNECTED);
        assertThat(latch.getCount()).isEqualTo(1);

        listener.onEvent(StompEventListener.EventType.SESSION_CONNECTED, "client1", "detail");
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test void testLatchOnFirstDisconnect() throws Exception {
        var latch = new CountDownLatch(1);
        var listener = StompEventListener.latchOnFirst(latch, StompEventListener.EventType.SESSION_DISCONNECTED);
        listener.onEvent(StompEventListener.EventType.SESSION_DISCONNECTED, "client1", "detail");
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test void testLatchIgnoresOtherEvents() throws Exception {
        var latch = new CountDownLatch(1);
        var listener = StompEventListener.latchOnFirst(latch, StompEventListener.EventType.SESSION_CONNECTED);
        listener.onEvent(StompEventListener.EventType.SESSION_DISCONNECTED, "client1", "detail");
        assertThat(latch.getCount()).isEqualTo(1);

        listener.onEvent(StompEventListener.EventType.SESSION_CONNECTED, "client1", "detail");
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test void testEventTypeEnumValues() {
        assertThat(StompEventListener.EventType.values()).containsExactly(
            StompEventListener.EventType.SESSION_CONNECTED,
            StompEventListener.EventType.SESSION_DISCONNECTED,
            StompEventListener.EventType.MESSAGE_DELIVERED,
            StompEventListener.EventType.TRANSACTION_COMMITTED,
            StompEventListener.EventType.TRANSACTION_ABORTED
        );
    }

    @Test void testNoOpFactory() {
        var noOp = StompEventListener.noOp();
        assertThat(noOp).isSameAs(StompEventListener.NO_OP);
    }
}
