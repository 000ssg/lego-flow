package ssg.legoflow.database.redis.transport;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Byte-level transport SPI for the Redis protocol.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving raw bytes
 * (e.g. TCP sockets, in-memory queues). The invariant protocol core uses
 * this interface for all I/O, enabling transport-agnostic protocol logic.
 *
 * @since 0.3.0
 */
public interface RedisTransport extends ByteTransport {
}