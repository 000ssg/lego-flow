package ssg.legoflow.http3.quic;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a single QUIC stream with state management and flow control.
 *
 * <p>Stream IDs encode the initiator and directionality in the low two bits
 * per RFC 9000 section 2.1:</p>
 * <ul>
 *   <li>{@code 0x0} — client-initiated, bidirectional</li>
 *   <li>{@code 0x1} — server-initiated, bidirectional</li>
 *   <li>{@code 0x2} — client-initiated, unidirectional</li>
 *   <li>{@code 0x3} — server-initiated, unidirectional</li>
 * </ul>
 *
 * <p>This class is thread-safe. All state transitions and flow control
 * operations are guarded by a lock.</p>
 *
 * @since 0.1.0
 */
public class QuicStream {

    private final long streamId;
    private volatile QuicStreamState state;
    private final AtomicLong sendWindow;
    private final AtomicLong receiveWindow;
    private final List<ByteBuffer> dataBuffers;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Creates a new stream with the given ID and initial flow control windows.
     *
     * @param streamId          the stream identifier
     * @param initialSendWindow the initial send window in bytes
     * @param initialReceiveWindow the initial receive window in bytes
     * @since 0.1.0
     */
    public QuicStream(long streamId, long initialSendWindow, long initialReceiveWindow) {
        this.streamId = streamId;
        this.state = QuicStreamState.IDLE;
        this.sendWindow = new AtomicLong(initialSendWindow);
        this.receiveWindow = new AtomicLong(initialReceiveWindow);
        this.dataBuffers = new ArrayList<>();
    }

    /**
     * Returns the stream identifier.
     *
     * @return the stream ID
     * @since 0.1.0
     */
    public long streamId() {
        return streamId;
    }

    /**
     * Returns the current stream state.
     *
     * @return the current {@link QuicStreamState}
     * @since 0.1.0
     */
    public QuicStreamState state() {
        return state;
    }

    /**
     * Transitions the stream to the given state.
     *
     * @param newState the target state
     * @throws IllegalStateException if the transition is not valid
     * @since 0.1.0
     */
    public void transitionTo(QuicStreamState newState) {
        lock.lock();
        try {
            if (!state.canTransitionTo(newState)) {
                throw new IllegalStateException(
                        "Cannot transition stream " + streamId + " from " + state + " to " + newState);
            }
            this.state = newState;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sends data on this stream, consuming the send window.
     *
     * @param data the data to send
     * @return {@code true} if the send window was sufficient
     * @throws IllegalStateException if the stream is not in a sendable state
     * @since 0.1.0
     */
    public boolean send(ByteBuffer data) {
        lock.lock();
        try {
            if (state != QuicStreamState.OPEN && state != QuicStreamState.HALF_CLOSED_REMOTE) {
                throw new IllegalStateException("Cannot send on stream " + streamId + " in state " + state);
            }
            int size = data.remaining();
            long current = sendWindow.get();
            if (size > current) {
                return false;
            }
            sendWindow.addAndGet(-size);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Receives data on this stream, consuming the receive window and
     * buffering the data.
     *
     * @param data the received data
     * @throws IllegalStateException if the stream is not in a receivable state
     * @since 0.1.0
     */
    public void receive(ByteBuffer data) {
        lock.lock();
        try {
            if (state != QuicStreamState.OPEN && state != QuicStreamState.HALF_CLOSED_LOCAL) {
                throw new IllegalStateException("Cannot receive on stream " + streamId + " in state " + state);
            }
            int size = data.remaining();
            receiveWindow.addAndGet(-size);
            dataBuffers.add(data.duplicate());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all accumulated received data as a single buffer.
     *
     * @return a {@link ByteBuffer} containing all received data
     * @since 0.1.0
     */
    public ByteBuffer getAccumulatedData() {
        lock.lock();
        try {
            if (dataBuffers.isEmpty()) {
                return ByteBuffer.allocate(0);
            }
            int total = dataBuffers.stream().mapToInt(ByteBuffer::remaining).sum();
            var combined = ByteBuffer.allocate(total);
            for (var buf : dataBuffers) {
                combined.put(buf.duplicate());
            }
            combined.flip();
            return combined;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Initiates a stream reset with the given error code.
     *
     * @param errorCode the application error code
     * @throws IllegalStateException if the stream cannot be reset from its current state
     * @since 0.1.0
     */
    public void resetStream(long errorCode) {
        lock.lock();
        try {
            if (!state.canTransitionTo(QuicStreamState.RESET_SENT)) {
                throw new IllegalStateException(
                        "Cannot reset stream " + streamId + " from state " + state);
            }
            this.state = QuicStreamState.RESET_SENT;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Requests the peer to stop sending on this stream.
     *
     * @param errorCode the application error code
     * @throws IllegalStateException if stop sending is not valid from the current state
     * @since 0.1.0
     */
    public void stopSending(long errorCode) {
        lock.lock();
        try {
            if (state != QuicStreamState.OPEN && state != QuicStreamState.HALF_CLOSED_LOCAL) {
                throw new IllegalStateException(
                        "Cannot stop sending on stream " + streamId + " in state " + state);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns whether this stream was initiated by the client.
     *
     * @return {@code true} if the stream ID indicates client initiation
     * @since 0.1.0
     */
    public boolean isClientInitiated() {
        return (streamId & 0x01) == 0;
    }

    /**
     * Returns whether this stream was initiated by the server.
     *
     * @return {@code true} if the stream ID indicates server initiation
     * @since 0.1.0
     */
    public boolean isServerInitiated() {
        return (streamId & 0x01) == 1;
    }

    /**
     * Returns whether this is a bidirectional stream.
     *
     * @return {@code true} if the stream type bits indicate bidirectional
     * @since 0.1.0
     */
    public boolean isBidirectional() {
        return (streamId & 0x02) == 0;
    }

    /**
     * Returns whether this is a unidirectional stream.
     *
     * @return {@code true} if the stream type bits indicate unidirectional
     * @since 0.1.0
     */
    public boolean isUnidirectional() {
        return (streamId & 0x02) != 0;
    }

    /**
     * Returns the current send window size.
     *
     * @return the remaining send window in bytes
     * @since 0.1.0
     */
    public long sendWindowSize() {
        return sendWindow.get();
    }

    /**
     * Returns the current receive window size.
     *
     * @return the remaining receive window in bytes
     * @since 0.1.0
     */
    public long receiveWindowSize() {
        return receiveWindow.get();
    }

    /**
     * Adjusts the send window by the given delta.
     *
     * @param delta the amount to add (positive) or subtract (negative)
     * @since 0.1.0
     */
    public void adjustSendWindow(long delta) {
        sendWindow.addAndGet(delta);
    }

    /**
     * Adjusts the receive window by the given delta.
     *
     * @param delta the amount to add (positive) or subtract (negative)
     * @since 0.1.0
     */
    public void adjustReceiveWindow(long delta) {
        receiveWindow.addAndGet(delta);
    }

    /**
     * Returns whether the stream is currently open (sending or receiving possible).
     *
     * @return {@code true} if the stream is in an active state
     * @since 0.1.0
     */
    public boolean isOpen() {
        var s = state;
        return s == QuicStreamState.OPEN
                || s == QuicStreamState.HALF_CLOSED_LOCAL
                || s == QuicStreamState.HALF_CLOSED_REMOTE;
    }

    /**
     * Returns whether the stream is fully closed.
     *
     * @return {@code true} if the stream state is {@link QuicStreamState#CLOSED}
     * @since 0.1.0
     */
    public boolean isClosed() {
        return state == QuicStreamState.CLOSED;
    }
}
