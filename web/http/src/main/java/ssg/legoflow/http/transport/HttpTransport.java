package ssg.legoflow.http.transport;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Byte-level transport SPI for the HTTP protocol.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving raw HTTP bytes
 * (TCP sockets, in-memory queues, WebSocket, etc.). The protocol core uses this interface
 * for all I/O, enabling transport-agnostic protocol logic.
 *
 * @since 0.3.0
 */
public interface HttpTransport extends ByteTransport {
}
