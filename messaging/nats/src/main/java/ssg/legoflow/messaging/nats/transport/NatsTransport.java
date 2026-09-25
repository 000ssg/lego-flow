package ssg.legoflow.messaging.nats.transport;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Byte-level transport SPI for the NATS protocol.
 *
 * <p>Implementation of the lego-flow reference pattern (see
 * {@code doc/PROTOCOL-GUIDELINES.md} and the STOMP/MQTT/AMQP transports): the protocol
 * core ({@code NatsClient}, {@code NatsServer}, {@code ClientConnection}) talks only to this
 * interface — <b>never to sockets</b>. Implementations:
 * <ul>
 *   <li>{@link InMemoryNatsTransport} — test pair, no network</li>
 *   <li>{@link PipelineNatsTransport} — production transport backed by a {@code DataChannel},
 *       driven by the {@code SelectableChannelManager} selector thread</li>
 * </ul>
 *
 * <p>{@code receive} is <b>blocking</b>: NATS is line/text-framed, so the reader (the
 * line-based codec behind a {@code BufferedReader}) blocks until the next bytes arrive or the
 * transport closes. Timeout is only an internal implementation detail — a silent broker must
 * not surface as a spurious EOF.
 *
 * @since 0.1.0
 */
public interface NatsTransport {

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
     * Receives raw bytes from this transport, <b>blocking</b> until data is available.
     *
     * <p>Equivalent to {@code receiveWithTimeout(buffer, Long.MAX_VALUE, MILLISECONDS)}.
     *
     * @param buffer the buffer to read into
     * @return bytes read (>= 1), or -1 if the transport is closed
     */
    default int receive(ByteBuffer buffer) {
        return receiveWithTimeout(buffer, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    /**
     * Receives raw bytes, waiting at most the given timeout for the next read.
     *
     * <p>Returns at least one byte, or -1 if the transport is closed <b>or</b> the timeout
     * expired with nothing to read. Callers that must distinguish the two (a silent broker
     * must not look like EOF) should check {@link #isOpen()} on -1 — see
     * {@link TransportStreams}.
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
