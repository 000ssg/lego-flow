package ssg.legoflow.rpc.grpc.client;

import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.common.StatusException;
import ssg.legoflow.rpc.grpc.metadata.Metadata;
import ssg.legoflow.rpc.grpc.protobuf.ProtoMessage;
import ssg.legoflow.rpc.grpc.protobuf.ProtobufCodec;
import ssg.legoflow.rpc.grpc.transport.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client-side representation of a gRPC call.
 * Builds request frames and parses response frames.
 */
public class ClientCall {

    private final MethodDescriptor method;
    private final CallOptions options;
    private final List<ProtoMessage> responseMessages;
    private Metadata responseMetadata;
    private Metadata responseTrailers;
    private GrpcStatus status;
    private String statusMessage;
    private boolean cancelled;

    public ClientCall(MethodDescriptor method, CallOptions options) {
        this.method = method;
        this.options = options != null ? options : CallOptions.defaults();
        this.responseMessages = new ArrayList<>();
        this.responseMetadata = new Metadata();
        this.responseTrailers = new Metadata();
        this.status = GrpcStatus.OK;
    }

    public MethodDescriptor method() {
        return method;
    }

    public CallOptions options() {
        return options;
    }

    /**
     * Encodes a single request message into a gRPC frame.
     */
    public byte[] encodeRequest(ProtoMessage request) {
        var descriptor = method.requestDescriptor();
        byte[] encoded = descriptor != null
                ? ProtobufCodec.encode(request, descriptor)
                : ProtobufCodec.encode(request);

        if (options.encoding() != GrpcEncoding.IDENTITY) {
            return GrpcFrameCodec.encodeCompressed(encoded, options.encoding());
        }
        return GrpcFrameCodec.encode(encoded);
    }

    /**
     * Encodes multiple request messages into combined gRPC frames.
     */
    public byte[] encodeRequests(List<ProtoMessage> requests) {
        var frames = new ArrayList<byte[]>();
        for (var request : requests) {
            frames.add(encodeRequest(request));
        }
        int totalLen = frames.stream().mapToInt(f -> f.length).sum();
        var combined = new byte[totalLen];
        int offset = 0;
        for (var frame : frames) {
            System.arraycopy(frame, 0, combined, offset, frame.length);
            offset += frame.length;
        }
        return combined;
    }

    /**
     * Creates the HTTP/2 request headers for this call.
     */
    public ssg.legoflow.http.core.HttpHeaders buildRequestHeaders() {
        return GrpcHeaders.createRequestHeaders(
                method.path(),
                options.authority(),
                options.encoding(),
                options.timeout(),
                options.metadata()
        );
    }

    /**
     * Processes the response data (gRPC framed) and populates response messages.
     */
    public void processResponse(byte[] responseData, GrpcEncoding encoding) {
        var buf = ByteBuffer.wrap(responseData);
        var frames = GrpcFrameCodec.decodeAllFrames(buf);
        var descriptor = method.responseDescriptor();

        for (var frame : frames) {
            byte[] data = GrpcFrameCodec.decompressIfNeeded(frame, encoding);
            var msg = ProtobufCodec.decode(data, descriptor);
            responseMessages.add(msg);
        }
    }

    /**
     * Processes trailer headers.
     */
    public void processTrailers(ssg.legoflow.http.core.HttpHeaders trailers) {
        this.status = GrpcHeaders.extractStatus(trailers);
        this.statusMessage = GrpcHeaders.extractMessage(trailers);
        this.responseTrailers = GrpcHeaders.extractMetadata(trailers);
    }

    /**
     * Gets the single response message (for unary or client-streaming calls).
     */
    public ProtoMessage getResponse() {
        if (status.isError()) {
            throw new StatusException(status, statusMessage);
        }
        if (responseMessages.isEmpty()) {
            throw new StatusException(GrpcStatus.INTERNAL, "No response message received");
        }
        return responseMessages.getFirst();
    }

    /**
     * Gets all response messages (for streaming calls).
     */
    public List<ProtoMessage> getResponses() {
        if (status.isError()) {
            throw new StatusException(status, statusMessage);
        }
        return Collections.unmodifiableList(responseMessages);
    }

    public GrpcStatus status() {
        return status;
    }

    public String statusMessage() {
        return statusMessage;
    }

    public Metadata responseMetadata() {
        return responseMetadata;
    }

    public void setResponseMetadata(Metadata metadata) {
        this.responseMetadata = metadata;
    }

    public Metadata responseTrailers() {
        return responseTrailers;
    }

    public void cancel() {
        this.cancelled = true;
        this.status = GrpcStatus.CANCELLED;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
