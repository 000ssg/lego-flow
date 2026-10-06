package ssg.legoflow.xmpp.transport;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Byte-level transport SPI for the XMPP protocol.
 *
 * <p>Implementation of the lego-flow reference pattern (see
 * {@code doc/PROTOCOL-GUIDELINES.md} and the NATS/STOMP/MQTT/AMQP transports): the protocol
 * core ({@code XmppClient}, {@code XmppServer}) talks only to this interface — <b>never to
 * sockets</b>. Implementations:
 * <ul>
 *   <li>{@link InMemoryXmppTransport} — test pair, no network</li>
 *   <li>{@link PipelineXmppTransport} — production transport backed by a {@code DataChannel},
 *       driven by the {@code SelectableChannelManager} selector thread</li>
 * </ul>
 *
 * <p>{@code receive} is <b>blocking</b>: XMPP is a byte stream, so the reader (the virtual-thread
 * read loop behind the codec) blocks until the next bytes arrive or the transport closes.
 * A timeout is only an internal implementation detail — a silent peer must never surface as a
 * spurious EOF; callers that get {@code -1} should check {@link #isOpen()} to tell a timeout
 * from a real close.
 *
 * @since 0.1.0
 */
public interface XmppTransport {

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
     * expired with nothing to read. A silent peer must not look like EOF: callers that must
     * distinguish the two should check {@link #isOpen()} on {@code -1}.
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
