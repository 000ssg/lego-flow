package ssg.legoflow.messaging.kafka.transport;

import ssg.legoflow.service.channel.ByteTransport;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Byte-level transport SPI for the Kafka protocol.
 *
 * @since 0.1.0
 */
public interface KafkaTransport extends ByteTransport {

    /**
     * Sends raw bytes through this transport.
     *
     * @param data the bytes to send
     */
    void send(ByteBuffer data);

    /**
     * Receives raw bytes from this transport, <b>blocking</b> until data is available.
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
