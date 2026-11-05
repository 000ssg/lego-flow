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

    @Test void testLatchOnConnectionFiresOnlyForMatchingSession() throws Exception {
        var latch = new CountDownLatch(1);
        var listener = StompEventListener.latchOnConnection(latch, StompEventListener.EventType.SESSION_CONNECTED, "session-2");

        // Event from different session — latch should NOT count down
        listener.onEvent(StompEventListener.EventType.SESSION_CONNECTED, "session-1", "detail");
        assertThat(latch.getCount()).isEqualTo(1);

        // Event from matching session — latch counts down
        listener.onEvent(StompEventListener.EventType.SESSION_CONNECTED, "session-2", "detail");
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test void testLatchOnConnectionIgnoresWrongEventType() throws Exception {
        var latch = new CountDownLatch(1);
        var listener = StompEventListener.latchOnConnection(latch, StompEventListener.EventType.SESSION_CONNECTED, "session-1");

        // Matching session but wrong event — latch should NOT count down
        listener.onEvent(StompEventListener.EventType.SESSION_DISCONNECTED, "session-1", "detail");
        assertThat(latch.getCount()).isEqualTo(1);
    }

    @Test void testLatchOnConnectionsDispatchesToMatchingSession() throws Exception {
        var latch1 = new CountDownLatch(1);
        var latch2 = new CountDownLatch(1);
        var latchMap = new java.util.HashMap<String, CountDownLatch>();
        latchMap.put("session-1", latch1);
        latchMap.put("session-2", latch2);

        var listener = StompEventListener.latchOnConnections(latchMap, StompEventListener.EventType.SESSION_CONNECTED);

        // Event from session-1
        listener.onEvent(StompEventListener.EventType.SESSION_CONNECTED, "session-1", "detail");
        assertThat(latch1.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(latch2.getCount()).isEqualTo(1);

        // Event from session-2
        listener.onEvent(StompEventListener.EventType.SESSION_CONNECTED, "session-2", "detail");
        assertThat(latch2.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test void testLatchOnConnectionsIgnoresUnknownSession() throws Exception {
        var latch1 = new CountDownLatch(1);
        var latchMap = new java.util.HashMap<String, CountDownLatch>();
        latchMap.put("session-1", latch1);

        var listener = StompEventListener.latchOnConnections(latchMap, StompEventListener.EventType.SESSION_CONNECTED);

        // Event from unknown session — no latch to count down
        listener.onEvent(StompEventListener.EventType.SESSION_CONNECTED, "session-99", "detail");
        assertThat(latch1.getCount()).isEqualTo(1);
    }
}
