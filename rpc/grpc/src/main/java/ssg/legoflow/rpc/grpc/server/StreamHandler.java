package ssg.legoflow.rpc.grpc.server;

import ssg.legoflow.rpc.grpc.metadata.Metadata;
import ssg.legoflow.rpc.grpc.protobuf.ProtoMessage;

import java.util.List;
import java.util.function.Consumer;

/**
 * Handler for streaming gRPC calls (server streaming, client streaming, bidi streaming).
 */
public interface StreamHandler {

    /**
     * Handler for server-streaming calls: single request, stream of responses.
     */
    @FunctionalInterface
    interface ServerStreaming {
        /**
         * Handles a server-streaming call.
         *
         * @param request        the request message
         * @param metadata       the request metadata
         * @param responseStream consumer to send each response message
         */
        void handle(ProtoMessage request, Metadata metadata, Consumer<ProtoMessage> responseStream);
    }

    /**
     * Handler for client-streaming calls: stream of requests, single response.
     */
    @FunctionalInterface
    interface ClientStreaming {
        /**
         * Handles a client-streaming call.
         *
         * @param requests the collected request messages
         * @param metadata the request metadata
         * @return the single response message
         */
        ProtoMessage handle(List<ProtoMessage> requests, Metadata metadata);
    }

    /**
     * Handler for bidi-streaming calls: stream of requests, stream of responses.
     */
    @FunctionalInterface
    interface BidiStreaming {
        /**
         * Handles a bidirectional streaming call.
         *
         * @param requests       the collected request messages
         * @param metadata       the request metadata
         * @param responseStream consumer to send each response message
         */
        void handle(List<ProtoMessage> requests, Metadata metadata, Consumer<ProtoMessage> responseStream);
    }
}
