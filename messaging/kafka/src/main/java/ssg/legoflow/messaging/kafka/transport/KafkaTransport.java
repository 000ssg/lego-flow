package ssg.legoflow.messaging.kafka.transport;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Byte-level transport SPI for the Kafka protocol.
 *
 * <p>Implementation of the lego-flow reference pattern (see
 * {@code doc/PROTOCOL-GUIDELINES.md} and the STOMP/MQTT/AMQP transports): the protocol
 * core ({@code KafkaBroker}, {@code KafkaConnection}) talks only to this
 * interface — <b>never to sockets</b>. Implementations:
 * <ul>
 *   <li>{@link InMemoryKafkaTransport} — test pair, no network</li>
 *   <li>{@link PipelineKafkaTransport} — production transport backed by a {@code DataChannel},
 *       driven by the {@code SelectableChannelManager} selector thread</li>
 * </ul>
 *
 * <p>Like the AMQP transport, this is a <b>byte-level</b> SPI: callers read the 4-byte
 * length prefix and the body via {@link #receive}/{@link #receiveWithTimeout}. Frame
 * (length-prefix) reassembly is the caller's responsibility (see {@code KafkaCodec}).
 *
 * <p>{@code receive} is <b>blocking</b>: it waits until bytes are available or the
 * transport closes. A timeout is a silent-broker signal, not an EOF — check
 * {@link #isOpen()} on -1 before concluding the peer is gone.
 *
 * @since 0.1.0
 */
public interface KafkaTransport {

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
     * <p>Equivalent to {@code receiveWithTimeout(buffer, 5, SECONDS)}.
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
     * expired with nothing to read. Callers that must distinguish the two (a silent broker
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
