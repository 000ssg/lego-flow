package ssg.legoflow.rpc.grpc.server;

import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.metadata.Metadata;
import ssg.legoflow.rpc.grpc.protobuf.ProtoMessage;
import ssg.legoflow.rpc.grpc.transport.GrpcStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a server-side gRPC call, holding the method descriptor,
 * request metadata, and providing access to response messages and trailers.
 */
public class ServerCall {

    private final MethodDescriptor method;
    private final Metadata requestMetadata;
    private final Metadata responseMetadata;
    private final Metadata trailers;
    private final List<ProtoMessage> responseMessages;
    private GrpcStatus status;
    private String statusMessage;
    private boolean cancelled;

    public ServerCall(MethodDescriptor method, Metadata requestMetadata) {
        this.method = method;
        this.requestMetadata = requestMetadata != null ? requestMetadata : new Metadata();
        this.responseMetadata = new Metadata();
        this.trailers = new Metadata();
        this.responseMessages = new ArrayList<>();
        this.status = GrpcStatus.OK;
    }

    public MethodDescriptor method() {
        return method;
    }

    public Metadata requestMetadata() {
        return requestMetadata;
    }

    public Metadata responseMetadata() {
        return responseMetadata;
    }

    public Metadata trailers() {
        return trailers;
    }

    public void sendMessage(ProtoMessage message) {
        responseMessages.add(message);
    }

    public List<ProtoMessage> responseMessages() {
        return Collections.unmodifiableList(responseMessages);
    }

    public void close(GrpcStatus status, String message) {
        this.status = status;
        this.statusMessage = message;
    }

    public void cancel() {
        this.cancelled = true;
        this.status = GrpcStatus.CANCELLED;
    }

    public GrpcStatus status() {
        return status;
    }

    public String statusMessage() {
        return statusMessage;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Creates a response consumer that adds messages to this call.
     */
    public Consumer<ProtoMessage> responseConsumer() {
        return this::sendMessage;
    }
}
