package ssg.legoflow.messaging.mqtt.broker;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class MqttEventListenerTest {

    @Test
    void testNoOpDefault() {
        var listener = MqttEventListener.NO_OP;
        // All methods should be no-op (no exception)
        listener.onEvent(MqttEventListener.EventType.CLIENT_CONNECTED, "client-1", null);
        listener.onEvent(MqttEventListener.EventType.CLIENT_DISCONNECTED, "client-1", null);
        listener.onEvent(MqttEventListener.EventType.SUBSCRIPTION_ADDED, "client-1", null);
        listener.onEvent(MqttEventListener.EventType.SESSION_CREATED, "client-1", null);
        listener.onEvent(MqttEventListener.EventType.SESSION_RESUMED, "client-1", null);
        listener.onEvent(MqttEventListener.EventType.WILL_DELIVERED, "client-1", null);
        listener.onEvent(MqttEventListener.EventType.SESSION_EXPIRED, "client-1", null);
        listener.onEvent(MqttEventListener.EventType.KEEP_ALIVE_TIMEOUT, "client-1", null);
    }

    @Test
    void testEventTypeEnumValues() {
        assertThat(MqttEventListener.EventType.values()).containsExactly(
                MqttEventListener.EventType.CLIENT_CONNECTED,
                MqttEventListener.EventType.CLIENT_DISCONNECTED,
                MqttEventListener.EventType.SESSION_CREATED,
                MqttEventListener.EventType.SESSION_RESUMED,
                MqttEventListener.EventType.SUBSCRIPTION_ADDED,
                MqttEventListener.EventType.WILL_DELIVERED,
                MqttEventListener.EventType.SESSION_EXPIRED,
                MqttEventListener.EventType.KEEP_ALIVE_TIMEOUT
        );
    }

    @Test
    void testLatchOnFirstFactory() throws Exception {
        var latch = new CountDownLatch(1);
        var listener = MqttEventListener.latchOnFirst(latch, MqttEventListener.EventType.CLIENT_CONNECTED);
        assertThat(latch.getCount()).isEqualTo(1);

        listener.onEvent(MqttEventListener.EventType.CLIENT_CONNECTED, "s1", "payload");
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void testLatchOnFirstDisconnect() throws Exception {
        var latch = new CountDownLatch(1);
        var listener = MqttEventListener.latchOnFirst(latch, MqttEventListener.EventType.CLIENT_DISCONNECTED);
        assertThat(latch.getCount()).isEqualTo(1);

        listener.onEvent(MqttEventListener.EventType.CLIENT_DISCONNECTED, "s1", "payload");
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void testLatchIgnoresOtherEvents() throws Exception {
        var latch = new CountDownLatch(1);
        var listener = MqttEventListener.latchOnFirst(latch, MqttEventListener.EventType.CLIENT_CONNECTED);
        // Fire a different event — should not count down
        listener.onEvent(MqttEventListener.EventType.CLIENT_DISCONNECTED, "s1", "payload");
        assertThat(latch.getCount()).isEqualTo(1);

        // Fire the target event
        listener.onEvent(MqttEventListener.EventType.CLIENT_CONNECTED, "s1", "payload");
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void testLatchOnlyCountsDownOnce() throws Exception {
        var latch = new CountDownLatch(1);
        var listener = MqttEventListener.latchOnFirst(latch, MqttEventListener.EventType.SESSION_CREATED);
        listener.onEvent(MqttEventListener.EventType.SESSION_CREATED, "s1", "msg1");
        listener.onEvent(MqttEventListener.EventType.SESSION_CREATED, "s2", "msg2"); // second fire still counts down
        assertThat(latch.getCount()).isZero();
    }

    @Test
    void testNoOpFactory() {
        var noOp = MqttEventListener.noOp();
        assertThat(noOp).isSameAs(MqttEventListener.NO_OP);
    }
}
