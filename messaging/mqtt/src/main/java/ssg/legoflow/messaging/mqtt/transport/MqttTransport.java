package ssg.legoflow.messaging.mqtt.transport;

import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Service Provider Interface for MQTT packet transport.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving
 * raw bytes (e.g. TCP sockets, in-memory queues). The invariant MQTT core
 * uses this interface for all I/O, enabling transport-agnostic protocol logic.
 *
 * @since 0.1.0
 */
public interface MqttTransport {

    /**
     * Sends raw bytes through this transport.
     *
     * @param data the bytes to send
     */
    void send(ByteBuffer data);

    /**
     * Receives raw bytes from this transport, blocking until data is available.
     * Default timeout is 5 seconds.
     *
     * @param buffer the buffer to read into
     * @return the number of bytes read, or -1 if the transport is closed
     */
    default int receive(ByteBuffer buffer) {
        return receiveWithTimeout(buffer, 5, TimeUnit.SECONDS);
    }

    /**
     * Receives raw bytes from this transport with an explicit timeout.
     *
     * @param buffer the buffer to read into
     * @param timeout how long to wait
     * @param unit    timeout unit
     * @return the number of bytes read, or -1 if the transport is closed or timed out
     */
    int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit);

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

    /**
     * Returns the underlying {@link DataChannel} for this transport.
     *
     * @return the data channel
     */
    DataChannel getChannel();
}
