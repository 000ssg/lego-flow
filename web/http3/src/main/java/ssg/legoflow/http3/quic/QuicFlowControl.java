package ssg.legoflow.http3.quic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * QUIC flow control at both connection and stream levels per RFC 9000 section 4.
 *
 * <p>Tracks send and receive windows for the overall connection and for
 * each individual stream. Provides methods to consume windows, update
 * limits (via MAX_DATA / MAX_STREAM_DATA), and detect when a sender
 * is blocked.</p>
 *
 * <p>This class is thread-safe. All counters use {@link AtomicLong}.</p>
 *
 * @since 1.0.0
 */
public class QuicFlowControl {

    private final AtomicLong connectionSendLimit;
    private final AtomicLong connectionSendUsed;
    private final AtomicLong connectionReceiveLimit;
    private final AtomicLong connectionReceiveUsed;
    private final Map<Long, AtomicLong> streamSendLimits = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> streamSendUsed = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> streamReceiveLimits = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> streamReceiveUsed = new ConcurrentHashMap<>();

    /**
     * Creates flow control with the given connection-level limits.
     *
     * @param initialMaxData the initial connection-level data limit
     * @since 1.0.0
     */
    public QuicFlowControl(long initialMaxData) {
        this.connectionSendLimit = new AtomicLong(initialMaxData);
        this.connectionSendUsed = new AtomicLong(0);
        this.connectionReceiveLimit = new AtomicLong(initialMaxData);
        this.connectionReceiveUsed = new AtomicLong(0);
    }

    /**
     * Registers a stream with its initial flow control windows.
     *
     * @param streamId     the stream identifier
     * @param sendLimit    the initial send limit for the stream
     * @param receiveLimit the initial receive limit for the stream
     * @since 1.0.0
     */
    public void registerStream(long streamId, long sendLimit, long receiveLimit) {
        streamSendLimits.put(streamId, new AtomicLong(sendLimit));
        streamSendUsed.put(streamId, new AtomicLong(0));
        streamReceiveLimits.put(streamId, new AtomicLong(receiveLimit));
        streamReceiveUsed.put(streamId, new AtomicLong(0));
    }

    /**
     * Consumes send window for both the stream and the connection.
     *
     * @param streamId the stream identifier
     * @param amount   the number of bytes to consume
     * @return {@code true} if both windows had sufficient capacity
     * @since 1.0.0
     */
    public boolean consumeSendWindow(long streamId, long amount) {
        // Check connection-level
        long connAvailable = connectionSendLimit.get() - connectionSendUsed.get();
        if (amount > connAvailable) {
            return false;
        }

        // Check stream-level
        var streamLimit = streamSendLimits.get(streamId);
        var streamUsed = streamSendUsed.get(streamId);
        if (streamLimit == null || streamUsed == null) {
            return false;
        }
        long streamAvailable = streamLimit.get() - streamUsed.get();
        if (amount > streamAvailable) {
            return false;
        }

        connectionSendUsed.addAndGet(amount);
        streamUsed.addAndGet(amount);
        return true;
    }

    /**
     * Consumes receive window for both the stream and the connection.
     *
     * @param streamId the stream identifier
     * @param amount   the number of bytes to consume
     * @return {@code true} if both windows had sufficient capacity
     * @since 1.0.0
     */
    public boolean consumeReceiveWindow(long streamId, long amount) {
        long connAvailable = connectionReceiveLimit.get() - connectionReceiveUsed.get();
        if (amount > connAvailable) {
            return false;
        }

        var streamLimit = streamReceiveLimits.get(streamId);
        var streamUsed = streamReceiveUsed.get(streamId);
        if (streamLimit == null || streamUsed == null) {
            return false;
        }
        long streamAvailable = streamLimit.get() - streamUsed.get();
        if (amount > streamAvailable) {
            return false;
        }

        connectionReceiveUsed.addAndGet(amount);
        streamUsed.addAndGet(amount);
        return true;
    }

    /**
     * Updates the connection-level maximum data (MAX_DATA frame received).
     *
     * @param newLimit the new connection data limit
     * @since 1.0.0
     */
    public void updateMaxData(long newLimit) {
        connectionSendLimit.updateAndGet(current -> Math.max(current, newLimit));
    }

    /**
     * Updates the stream-level maximum data (MAX_STREAM_DATA frame received).
     *
     * @param streamId the stream identifier
     * @param newLimit the new stream data limit
     * @since 1.0.0
     */
    public void updateMaxStreamData(long streamId, long newLimit) {
        var limit = streamSendLimits.get(streamId);
        if (limit != null) {
            limit.updateAndGet(current -> Math.max(current, newLimit));
        }
    }

    /**
     * Determines whether a MAX_DATA frame should be sent to increase
     * the peer's connection-level send limit.
     *
     * <p>Returns {@code true} when more than half the receive window
     * has been consumed.</p>
     *
     * @return {@code true} if MAX_DATA should be sent
     * @since 1.0.0
     */
    public boolean shouldSendMaxData() {
        long limit = connectionReceiveLimit.get();
        long used = connectionReceiveUsed.get();
        return used > limit / 2;
    }

    /**
     * Determines whether a MAX_STREAM_DATA frame should be sent for the given stream.
     *
     * @param streamId the stream identifier
     * @return {@code true} if MAX_STREAM_DATA should be sent
     * @since 1.0.0
     */
    public boolean shouldSendMaxStreamData(long streamId) {
        var limit = streamReceiveLimits.get(streamId);
        var used = streamReceiveUsed.get(streamId);
        if (limit == null || used == null) return false;
        return used.get() > limit.get() / 2;
    }

    /**
     * Returns the available send window for a stream, considering both
     * the stream-level and connection-level limits.
     *
     * @param streamId the stream identifier
     * @return the available send window in bytes
     * @since 1.0.0
     */
    public long getAvailableSendWindow(long streamId) {
        long connAvailable = connectionSendLimit.get() - connectionSendUsed.get();
        var streamLimit = streamSendLimits.get(streamId);
        var streamUsed = streamSendUsed.get(streamId);
        if (streamLimit == null || streamUsed == null) return 0;
        long streamAvailable = streamLimit.get() - streamUsed.get();
        return Math.min(connAvailable, streamAvailable);
    }

    /**
     * Returns the available receive window for a stream, considering both
     * the stream-level and connection-level limits.
     *
     * @param streamId the stream identifier
     * @return the available receive window in bytes
     * @since 1.0.0
     */
    public long getAvailableReceiveWindow(long streamId) {
        long connAvailable = connectionReceiveLimit.get() - connectionReceiveUsed.get();
        var streamLimit = streamReceiveLimits.get(streamId);
        var streamUsed = streamReceiveUsed.get(streamId);
        if (streamLimit == null || streamUsed == null) return 0;
        long streamAvailable = streamLimit.get() - streamUsed.get();
        return Math.min(connAvailable, streamAvailable);
    }

    /**
     * Returns the connection-level send limit.
     *
     * @return the send limit in bytes
     * @since 1.0.0
     */
    public long connectionSendLimit() {
        return connectionSendLimit.get();
    }

    /**
     * Returns the connection-level receive limit.
     *
     * @return the receive limit in bytes
     * @since 1.0.0
     */
    public long connectionReceiveLimit() {
        return connectionReceiveLimit.get();
    }

    /**
     * Returns the amount of connection-level send data consumed.
     *
     * @return the consumed bytes
     * @since 1.0.0
     */
    public long connectionSendUsed() {
        return connectionSendUsed.get();
    }

    /**
     * Returns the amount of connection-level receive data consumed.
     *
     * @return the consumed bytes
     * @since 1.0.0
     */
    public long connectionReceiveUsed() {
        return connectionReceiveUsed.get();
    }
}
