package ssg.legoflow.messaging.mqtt.broker;

import java.util.concurrent.CountDownLatch;

/**
 * Lightweight listener for MQTT protocol lifecycle events.
 *
 * <p>Used for testing and debugging — when {@code null} (default), all callbacks are
 * no-ops with zero overhead. Only invoked at protocol transition points, never
 * in the hot data path (packet reads/writes).
 *
 * <p><b>Thread safety:</b> callbacks may be invoked from any thread.
 * Implementations must be thread-safe.
 *
 * @since 0.2.0
 */
public interface MqttEventListener {

    /** Event types for the MQTT protocol lifecycle. */
    enum EventType {
        /** Client successfully connected — CONNACK sent. */
        CLIENT_CONNECTED,

        /** Client disconnected — session cleanup in progress. */
        CLIENT_DISCONNECTED,

        /** New session created (clean start). */
        SESSION_CREATED,

        /** Existing session resumed (clean start = false). */
        SESSION_RESUMED,

        /** Subscription added to topic tree. */
        SUBSCRIPTION_ADDED,

        /** Will message delivered due to ungraceful disconnect. */
        WILL_DELIVERED,

        /** Session expired and removed by sweep. */
        SESSION_EXPIRED,

        /** Client disconnected due to keep-alive timeout. */
        KEEP_ALIVE_TIMEOUT,
    }

    /**
     * Called when a protocol lifecycle event occurs.
     *
     * @param event     the event type
     * @param clientId  the client identifier
     * @param detail    optional detail string (e.g. topic filter, reason code)
     */
    void onEvent(EventType event, String clientId, String detail);

    /**
     * Returns a no-op listener — zero overhead when no listener is set.
     */
    static MqttEventListener noOp() {
        return NO_OP;
    }

    /**
     * Creates a listener that fires a {@link CountDownLatch} on the first matching event.
     *
     * @param latch  the latch to count down
     * @param event  the event type to match
     * @return a listener that counts down the latch once
     */
    static MqttEventListener latchOnFirst(CountDownLatch latch, EventType event) {
        return (e, clientId, detail) -> {
            if (e == event) latch.countDown();
        };
    }

    /** No-op listener — zero overhead when no listener is set. */
    MqttEventListener NO_OP = (event, clientId, detail) -> {};
}
