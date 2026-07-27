package ssg.legoflow.rpc.grpc.server;

import ssg.legoflow.rpc.grpc.metadata.Metadata;
import ssg.legoflow.rpc.grpc.protobuf.ProtoMessage;

/**
 * Handler for unary (single request, single response) gRPC calls.
 */
@FunctionalInterface
public interface UnaryHandler {

    /**
     * Handles a unary call.
     *
     * @param request  the request message
     * @param metadata the request metadata (headers)
     * @return the response message
     */
    ProtoMessage handle(ProtoMessage request, Metadata metadata);
}
