package ssg.legoflow.messaging.amqp.common;

/**
 * Lightweight listener for AMQP protocol lifecycle events.
 *
 * <p>Used for testing and debugging — when null (default), all callbacks are
 * no-ops with zero overhead. Only invoked at protocol transition points, never
 * in the hot data path (frame reads/writes).
 *
 * <p><b>Thread safety:</b> callbacks may be invoked from any thread.
 * Implementations must be thread-safe.
 *
 * @since 0.1.0
 */
public interface AmqpEventListener {

    /** Event types for the protocol lifecycle. */
    enum EventType {
        /** Connection established — transport ready for protocol exchange. */
        CONNECTION_STARTED,

        /** Protocol handshake complete — SASL + Open/Begin exchange done. */
        CONNECTION_OPENED,

        /** Session created and mapped. */
        SESSION_CREATED,

        /** Link attached. */
        LINK_ATTACHED,

        /** Link detached. */
        LINK_DETACHED,

        /** Connection closing. */
        CONNECTION_CLOSING,

        /** Message received (before disposition). */
        MESSAGE_RECEIVED,

        /** Message sent (after transfer written). */
        MESSAGE_SENT,
    }

    /**
     * Called when a protocol lifecycle event occurs.
     *
     * @param event the event type
     * @param connectionId the connection identifier (may be null for client-side events)
     * @param detail optional detail string (e.g. link name, session channel)
     */
    void onEvent(EventType event, String connectionId, String detail);

    /**
     * Returns an empty listener that does nothing.
     */
    static AmqpEventListener noOp() {
        return NO_OP;
    }

    /**
     * Convenience: creates a listener that fires a CountDownLatch on the first event.
     */
    static AmqpEventListener latchOnFirst(java.util.concurrent.CountDownLatch latch, EventType event) {
        return (e, connId, detail) -> {
            if (e == event) latch.countDown();
        };
    }

    /** No-op listener — zero overhead when no listener is set. */
    AmqpEventListener NO_OP = (event, connId, detail) -> {};
}
