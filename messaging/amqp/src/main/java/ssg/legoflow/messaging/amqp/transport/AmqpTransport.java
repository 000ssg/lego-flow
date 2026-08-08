package ssg.legoflow.messaging.amqp.transport;

import java.nio.ByteBuffer;

/**
 * Service Provider Interface for AMQP frame transport.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving
 * raw bytes (e.g. TCP sockets, in-memory queues). The invariant AMQP core
 * uses this interface for all I/O, enabling transport-agnostic protocol logic.
 *
 * @since 0.1.0
 */
public interface AmqpTransport {

    /**
     * Sends raw bytes through this transport.
     *
     * @param data the bytes to send
     */
    void send(ByteBuffer data);

    /**
     * Receives raw bytes from this transport, blocking until data is available.
     *
     * @param buffer the buffer to read into
     * @return the number of bytes read, or -1 if the transport is closed
     */
    int receive(ByteBuffer buffer);

    /**
     * Closes this transport and releases associated resources.
     */
    void close();

    /**
     * Returns whether this transport is currently open and usable.
     *
     * @return {@code true} if the transport is open
     */
    boolean isOpen();
}
