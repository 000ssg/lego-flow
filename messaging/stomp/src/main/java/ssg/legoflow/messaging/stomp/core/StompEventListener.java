package ssg.legoflow.messaging.stomp.core;

import java.util.concurrent.CountDownLatch;

/**
 * Lightweight listener for STOMP protocol lifecycle events.
 *
 * <p>Used for testing and debugging — when {@code null} (default), all callbacks are
 * no-ops with zero overhead. Only invoked at protocol transition points, never
 * in the hot data path (frame reads/writes).
 *
 * <p><b>Thread safety:</b> callbacks may be invoked from any thread.
 * Implementations must be thread-safe.
 *
 * @since 0.2.0
 */
public interface StompEventListener {

    /** Event types for the STOMP protocol lifecycle. */
    enum EventType {
        /** Session connected — CONNECTED frame sent. */
        SESSION_CONNECTED,

        /** Session disconnected — cleanup in progress. */
        SESSION_DISCONNECTED,

        /** Message delivered to subscriber. */
        MESSAGE_DELIVERED,

        /** Transaction committed successfully. */
        TRANSACTION_COMMITTED,

        /** Transaction aborted. */
        TRANSACTION_ABORTED,
    }

    /**
     * Called when a protocol lifecycle event occurs.
     *
     * @param event      the event type
     * @param sessionId  the session identifier
     * @param detail     optional detail string (e.g. destination, transaction ID)
     */
    void onEvent(EventType event, String sessionId, String detail);

    /**
     * Returns a no-op listener — zero overhead when no listener is set.
     */
    static StompEventListener noOp() {
        return NO_OP;
    }

    /**
     * Creates a listener that fires a {@link CountDownLatch} on the first matching event.
     * Use for single-connection tests.
     *
     * @param latch  the latch to count down
     * @param event  the event type to match
     * @return a listener that counts down the latch once
     */
    static StompEventListener latchOnFirst(CountDownLatch latch, EventType event) {
        return (e, id, detail) -> {
            if (e == event) latch.countDown();
        };
    }

    /**
     * Creates a listener that fires a {@link CountDownLatch} only when the specified
     * session ID triggers the event. Use for multi-connection tests.
     *
     * @param latch     the latch to count down
     * @param event     the event type to match
     * @param sessionId the session identifier to filter on
     * @return a listener that counts down the latch when the matching session fires
     */
    static StompEventListener latchOnConnection(CountDownLatch latch, EventType event, String sessionId) {
        return (e, id, detail) -> {
            if (e == event && sessionId.equals(id)) latch.countDown();
        };
    }

    /**
     * Creates a listener that dispatches events to a map of per-session latches.
     * Use for multi-connection tests: put expected session IDs as keys and
     * CountDownLatch(1) as values before connecting clients.
     *
     * @param latchMap map of sessionId -> CountDownLatch
     * @param event the event type to match
     * @return a listener that counts down the latch for the matching session
     */
    static StompEventListener latchOnConnections(java.util.Map<String, CountDownLatch> latchMap, EventType event) {
        return (e, id, detail) -> {
            if (e == event && id != null) {
                var latch = latchMap.get(id);
                if (latch != null) latch.countDown();
            }
        };
    }

    /** No-op listener — zero overhead when no listener is set. */
    StompEventListener NO_OP = (event, sessionId, detail) -> {};
}
