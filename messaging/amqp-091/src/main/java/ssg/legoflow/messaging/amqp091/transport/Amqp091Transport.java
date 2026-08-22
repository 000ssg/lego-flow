package ssg.legoflow.messaging.amqp091.transport;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Abstraction for AMQP 0-9-1 transport.
 *
 * <p>Implements DP/DF (DataProcessor / DataFilter) architecture:
 * transport = DF (raw bytes), client = DP (protocol logic).
 *
 * <p>Life-cycle: open() -> handshake (getInputStream/getOutputStream)
 *              -> frame I/O (send/recv) -> close().
 *
 * @since 0.2.0
 */
public interface Amqp091Transport {

    /** Opens the connection. Must be called before send/recv or get streams. */
    void open() throws IOException;

    /**
     * Sends raw bytes through this transport.
     */
    void send(ByteBuffer data) throws IOException;

    /**
     * Receives bytes into the buffer, blocking until data is available.
     * @return number of bytes read, or -1 if the transport is closed.
     */
    int recv(ByteBuffer buffer) throws IOException;

    /**
     * Returns the input stream for the protocol handshake
     * (greeting, connection.start, etc.). The stream is buffered
     * like the official RabbitMQ Java client.
     */
    DataInputStream getInputStream() throws IOException;

    /**
     * Returns the output stream for the protocol handshake.
     * The stream is buffered like the official RabbitMQ Java client.
     */
    DataOutputStream getOutputStream() throws IOException;

    /** Closes the transport and releases resources. */
    void close() throws IOException;

    /** Returns whether this transport is currently open. */
    boolean isOpen();
}
