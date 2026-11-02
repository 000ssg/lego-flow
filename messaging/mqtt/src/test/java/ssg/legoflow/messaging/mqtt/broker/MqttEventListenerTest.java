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
    void testLatchOnConnectionFiresOnlyForMatchingClient() throws Exception {
        var latch = new CountDownLatch(1);
        var listener = MqttEventListener.latchOnConnection(latch, MqttEventListener.EventType.CLIENT_CONNECTED, "client-2");

        // Event from different client — latch should NOT count down
        listener.onEvent(MqttEventListener.EventType.CLIENT_CONNECTED, "client-1", "payload");
        assertThat(latch.getCount()).isEqualTo(1);

        // Event from matching client — latch counts down
        listener.onEvent(MqttEventListener.EventType.CLIENT_CONNECTED, "client-2", "payload");
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void testLatchOnConnectionIgnoresWrongEventType() throws Exception {
        var latch = new CountDownLatch(1);
        var listener = MqttEventListener.latchOnConnection(latch, MqttEventListener.EventType.CLIENT_CONNECTED, "client-1");

        // Matching client but wrong event — latch should NOT count down
        listener.onEvent(MqttEventListener.EventType.CLIENT_DISCONNECTED, "client-1", "payload");
        assertThat(latch.getCount()).isEqualTo(1);
    }

    @Test
    void testLatchOnConnectionsDispatchesToMatchingClient() throws Exception {
        var latch1 = new CountDownLatch(1);
        var latch2 = new CountDownLatch(1);
        var latchMap = new java.util.HashMap<String, CountDownLatch>();
        latchMap.put("client-1", latch1);
        latchMap.put("client-2", latch2);

        var listener = MqttEventListener.latchOnConnections(latchMap, MqttEventListener.EventType.CLIENT_CONNECTED);

        // Event from client-1
        listener.onEvent(MqttEventListener.EventType.CLIENT_CONNECTED, "client-1", "payload");
        assertThat(latch1.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(latch2.getCount()).isEqualTo(1);

        // Event from client-2
        listener.onEvent(MqttEventListener.EventType.CLIENT_CONNECTED, "client-2", "payload");
        assertThat(latch2.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void testLatchOnConnectionsIgnoresUnknownClient() throws Exception {
        var latch1 = new CountDownLatch(1);
        var latchMap = new java.util.HashMap<String, CountDownLatch>();
        latchMap.put("client-1", latch1);

        var listener = MqttEventListener.latchOnConnections(latchMap, MqttEventListener.EventType.CLIENT_CONNECTED);

        // Event from unknown client — no latch to count down
        listener.onEvent(MqttEventListener.EventType.CLIENT_CONNECTED, "client-99", "payload");
        assertThat(latch1.getCount()).isEqualTo(1);
    }

    @Test
    void testNoOpFactory() {
        var noOp = MqttEventListener.noOp();
        assertThat(noOp).isSameAs(MqttEventListener.NO_OP);
    }
}
