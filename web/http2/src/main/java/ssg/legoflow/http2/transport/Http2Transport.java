package ssg.legoflow.http2.transport;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Byte-level transport SPI for the HTTP/2 protocol.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving raw HTTP/2
 * binary frames (TCP sockets, in-memory queues, etc.). The protocol core uses this
 * interface for all I/O, enabling transport-agnostic protocol logic.
 *
 * @since 0.3.0
 */
public interface Http2Transport extends ByteTransport {
}
