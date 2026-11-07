package ssg.legoflow.database.postgresql.transport;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Byte-level transport SPI for the PostgreSQL protocol.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving raw bytes
 * (e.g. TCP sockets, in-memory queues). The protocol core uses this interface for all I/O,
 * enabling transport-agnostic protocol logic.
 *
 * @since 0.3.0
 */
public interface PgTransport extends ByteTransport {
}
