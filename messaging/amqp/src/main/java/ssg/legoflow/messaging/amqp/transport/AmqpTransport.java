package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Service Provider Interface for AMQP frame transport.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving
 * raw bytes (e.g. TCP sockets, in-memory queues). The invariant AMQP core
 * uses this interface for all I/O, enabling transport-agnostic protocol logic.
 *
 * @since 0.1.0
 */
public interface AmqpTransport extends ByteTransport {
}
