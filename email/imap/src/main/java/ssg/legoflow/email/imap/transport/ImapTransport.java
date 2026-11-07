package ssg.legoflow.email.imap.transport;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Byte-level transport SPI for the IMAP protocol.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving raw bytes
 * (e.g. TCP sockets, in-memory queues, WebSocket). The invariant protocol core uses
 * this interface for all I/O, enabling transport-agnostic protocol logic.
 *
 * @since 0.3.0
 */
public interface ImapTransport extends ByteTransport {
}