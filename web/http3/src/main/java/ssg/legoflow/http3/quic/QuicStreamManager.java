package ssg.legoflow.http3.quic;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages QUIC streams for a connection.
 *
 * <p>Allocates stream IDs following the RFC 9000 convention (bits 0-1
 * encode initiator and direction), enforces MAX_STREAMS limits, and
 * provides lookup and lifecycle management for active streams.</p>
 *
 * <p>This class is thread-safe. All operations use concurrent data structures
 * and atomic counters.</p>
 *
 * @since 0.1.0
 */
public class QuicStreamManager {

    private final Map<Long, QuicStream> streams = new ConcurrentHashMap<>();
    private final AtomicLong nextClientBidiStreamId = new AtomicLong(0x00);
    private final AtomicLong nextServerBidiStreamId = new AtomicLong(0x01);
    private final AtomicLong nextClientUniStreamId = new AtomicLong(0x02);
    private final AtomicLong nextServerUniStreamId = new AtomicLong(0x03);
    private final boolean isServer;
    private volatile long maxStreamsBidi;
    private volatile long maxStreamsUni;
    private final long initialSendWindow;
    private final long initialReceiveWindow;

    /**
     * Creates a new stream manager.
     *
     * @param isServer             {@code true} if this is the server side
     * @param maxStreamsBidi       maximum number of bidirectional streams
     * @param maxStreamsUni        maximum number of unidirectional streams
     * @param initialSendWindow   initial send window per stream
     * @param initialReceiveWindow initial receive window per stream
     * @since 0.1.0
     */
    public QuicStreamManager(boolean isServer, long maxStreamsBidi, long maxStreamsUni,
                             long initialSendWindow, long initialReceiveWindow) {
        this.isServer = isServer;
        this.maxStreamsBidi = maxStreamsBidi;
        this.maxStreamsUni = maxStreamsUni;
        this.initialSendWindow = initialSendWindow;
        this.initialReceiveWindow = initialReceiveWindow;
    }

    /**
     * Creates a new bidirectional stream.
     *
     * @return the new stream
     * @throws IllegalStateException if the maximum stream limit is reached
     * @since 0.1.0
     */
    public QuicStream createBidiStream() {
        long count = streams.values().stream()
                .filter(QuicStream::isBidirectional)
                .filter(s -> !s.isClosed())
                .count();
        if (count >= maxStreamsBidi) {
            throw new IllegalStateException("Max bidirectional streams exceeded: " + maxStreamsBidi);
        }
        var counter = isServer ? nextServerBidiStreamId : nextClientBidiStreamId;
        long streamId = counter.getAndAdd(4);
        var stream = new QuicStream(streamId, initialSendWindow, initialReceiveWindow);
        stream.transitionTo(QuicStreamState.OPEN);
        streams.put(streamId, stream);
        return stream;
    }

    /**
     * Creates a new unidirectional stream.
     *
     * @return the new stream
     * @throws IllegalStateException if the maximum stream limit is reached
     * @since 0.1.0
     */
    public QuicStream createUniStream() {
        long count = streams.values().stream()
                .filter(QuicStream::isUnidirectional)
                .filter(s -> !s.isClosed())
                .count();
        if (count >= maxStreamsUni) {
            throw new IllegalStateException("Max unidirectional streams exceeded: " + maxStreamsUni);
        }
        var counter = isServer ? nextServerUniStreamId : nextClientUniStreamId;
        long streamId = counter.getAndAdd(4);
        var stream = new QuicStream(streamId, initialSendWindow, initialReceiveWindow);
        stream.transitionTo(QuicStreamState.OPEN);
        streams.put(streamId, stream);
        return stream;
    }

    /**
     * Gets an existing stream or creates a new one for the given ID.
     *
     * @param streamId the stream identifier
     * @return the existing or newly created stream
     * @since 0.1.0
     */
    public QuicStream getOrCreateStream(long streamId) {
        return streams.computeIfAbsent(streamId,
                id -> new QuicStream(id, initialSendWindow, initialReceiveWindow));
    }

    /**
     * Returns the stream for the given ID, or {@code null} if not found.
     *
     * @param streamId the stream identifier
     * @return the stream, or {@code null}
     * @since 0.1.0
     */
    public QuicStream getStream(long streamId) {
        return streams.get(streamId);
    }

    /**
     * Closes a stream by transitioning it to the {@link QuicStreamState#CLOSED} state.
     *
     * @param streamId the stream identifier
     * @since 0.1.0
     */
    public void closeStream(long streamId) {
        var stream = streams.get(streamId);
        if (stream != null && !stream.isClosed()) {
            stream.transitionTo(QuicStreamState.CLOSED);
        }
    }

    /**
     * Returns all currently active (non-closed) streams.
     *
     * @return an unmodifiable collection of active streams
     * @since 0.1.0
     */
    public Collection<QuicStream> getActiveStreams() {
        return streams.values().stream()
                .filter(s -> !s.isClosed())
                .toList();
    }

    /**
     * Returns the total number of streams (including closed).
     *
     * @return the total stream count
     * @since 0.1.0
     */
    public int getStreamCount() {
        return streams.size();
    }

    /**
     * Returns the number of active (non-closed) streams.
     *
     * @return the active stream count
     * @since 0.1.0
     */
    public int getActiveStreamCount() {
        return (int) streams.values().stream()
                .filter(s -> !s.isClosed())
                .count();
    }

    /**
     * Updates the maximum bidirectional stream limit.
     *
     * @param maxStreamsBidi the new limit
     * @since 0.1.0
     */
    public void setMaxStreamsBidi(long maxStreamsBidi) {
        this.maxStreamsBidi = maxStreamsBidi;
    }

    /**
     * Updates the maximum unidirectional stream limit.
     *
     * @param maxStreamsUni the new limit
     * @since 0.1.0
     */
    public void setMaxStreamsUni(long maxStreamsUni) {
        this.maxStreamsUni = maxStreamsUni;
    }

    /**
     * Returns the maximum bidirectional stream limit.
     *
     * @return the max bidi streams
     * @since 0.1.0
     */
    public long maxStreamsBidi() {
        return maxStreamsBidi;
    }

    /**
     * Returns the maximum unidirectional stream limit.
     *
     * @return the max uni streams
     * @since 0.1.0
     */
    public long maxStreamsUni() {
        return maxStreamsUni;
    }

    /**
     * Returns whether this manager is for the server side.
     *
     * @return {@code true} if server-side
     * @since 0.1.0
     */
    public boolean isServer() {
        return isServer;
    }
}
