package ssg.legoflow.coap.transport;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Byte-level transport SPI for the CoAP protocol.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving raw CoAP
 * messages (UDP datagrams, in-memory queues, etc.). The protocol core uses this interface
 * for all I/O, enabling transport-agnostic protocol logic.
 *
 * @since 0.3.0
 */
public interface CoapTransport extends ByteTransport {
}
