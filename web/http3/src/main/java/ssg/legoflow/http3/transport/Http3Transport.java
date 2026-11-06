package ssg.legoflow.http3.transport;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Byte-level transport SPI for the HTTP/3 protocol.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving raw HTTP/3
 * frames over QUIC (UDP sockets, in-memory queues, etc.). The protocol core uses this
 * interface for all I/O, enabling transport-agnostic protocol logic.
 *
 * @since 0.3.0
 */
public interface Http3Transport extends ByteTransport {
}
