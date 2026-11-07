package ssg.legoflow.rpc.grpc.transport;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Byte-level transport SPI for the gRPC protocol.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving raw gRPC
 * frames (TCP sockets, in-memory queues, etc.). The protocol core uses this interface
 * for all I/O, enabling transport-agnostic protocol logic.
 *
 * @since 0.3.0
 */
public interface GrpcTransport extends ByteTransport {
}
