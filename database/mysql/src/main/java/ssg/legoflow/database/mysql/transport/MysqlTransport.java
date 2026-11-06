package ssg.legoflow.database.mysql.transport;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Byte-level transport SPI for the MySQL protocol.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving raw MySQL wire
 * protocol bytes (TCP sockets, in-memory queues, etc.). The protocol core uses this interface
 * for all I/O, enabling transport-agnostic protocol logic.
 *
 * @since 0.3.0
 */
public interface MysqlTransport extends ByteTransport {
}
