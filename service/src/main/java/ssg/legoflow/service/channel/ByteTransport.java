package ssg.legoflow.service.channel;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Byte-level transport SPI for protocol implementations.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving raw bytes
 * (e.g. TCP sockets, in-memory queues, WebSocket). The invariant protocol core uses
 * this interface for all I/O, enabling transport-agnostic protocol logic.
 *
 * <p>Two reference implementations are provided:
 * <ul>
 *   <li>{@link InMemoryByteTransport} — test pair, no network</li>
 *   <li>{@link PipelineByteTransport} — production transport backed by a {@link DataChannel},
 *       driven by the {@code SelectableChannelManager} selector thread</li>
 * </ul>
 *
 * <p>A protocol module's transport interface should extend this interface and add any
 * protocol-specific methods (if any). See {@code AmqpTransport}, {@code KafkaTransport},
 * etc.
 *
 * @since 0.2.0
 */
public interface ByteTransport {

    /**
     * Sends raw bytes through this transport.
     *
     * <p>Non-blocking with respect to the caller: bytes are queued and flushed by the
     * transport's I/O. Never blocks on the wire for a long time.
     *
     * @param data the bytes to send
     */
    void send(ByteBuffer data);

    /**
     * Receives raw bytes from this transport, blocking until data is available.
     * Default timeout is 5 seconds.
     *
     * @param buffer the buffer to read into
     * @return bytes read (>= 1), or -1 if the transport is closed
     */
    default int receive(ByteBuffer buffer) {
        return receiveWithTimeout(buffer, 5, TimeUnit.SECONDS);
    }

    /**
     * Receives raw bytes, waiting at most the given timeout for the next read.
     *
     * <p>Returns at least one byte, or -1 if the transport is closed <b>or</b> the timeout
     * expired with nothing to read. Callers that must distinguish the two (a silent peer
     * must not look like EOF) should check {@link #isOpen()} on -1.
     *
     * @param buffer  the buffer to read into
     * @param timeout how long to wait
     * @param unit    timeout unit
     * @return bytes read (>= 0), or -1 if closed or timed out
     */
    int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit);

    /**
     * Closes this transport and releases associated resources. Idempotent.
     */
    void close();

    /**
     * Returns whether this transport is currently open and usable.
     *
     * @return true if open
     */
    boolean isOpen();
}
