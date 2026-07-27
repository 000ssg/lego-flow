package ssg.legoflow.rpc.grpc.server;

import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.common.MethodType;
import ssg.legoflow.rpc.grpc.common.StatusException;
import ssg.legoflow.rpc.grpc.metadata.Metadata;
import ssg.legoflow.rpc.grpc.protobuf.MessageDescriptor;
import ssg.legoflow.rpc.grpc.protobuf.ProtoMessage;
import ssg.legoflow.rpc.grpc.protobuf.ProtobufCodec;
import ssg.legoflow.rpc.grpc.transport.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * gRPC server that processes requests over HTTP/2.
 * Supports all four call types and interceptor chains.
 */
public class GrpcServer {

    private final GrpcServiceRegistry registry;
    private final List<ServerInterceptor> interceptors;
    private final GrpcEncoding defaultEncoding;

    public GrpcServer() {
        this(GrpcEncoding.IDENTITY);
    }

    public GrpcServer(GrpcEncoding defaultEncoding) {
        this.registry = new GrpcServiceRegistry();
        this.interceptors = new CopyOnWriteArrayList<>();
        this.defaultEncoding = defaultEncoding;
    }

    public GrpcServiceRegistry registry() {
        return registry;
    }

    public void addInterceptor(ServerInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    /**
     * Processes a gRPC request: parses the path, looks up the handler,
     * decodes the request, invokes the handler, encodes the response.
     *
     * @param path          the HTTP/2 :path (e.g., /package.Service/Method)
     * @param requestData   the raw request body (gRPC framed)
     * @param requestMetadata  metadata from request headers
     * @return the server call result containing response frames and trailers
     */
    public ServerCallResult processRequest(String path, byte[] requestData, Metadata requestMetadata) {
        var method = registry.getMethodDescriptor(path);
        if (method == null) {
            return ServerCallResult.error(GrpcStatus.UNIMPLEMENTED,
                    "Method not found: " + path);
        }

        var call = new ServerCall(method, requestMetadata);

        // Apply interceptor chain
        var interceptedCall = applyInterceptors(method, requestMetadata, call);

        try {
            dispatchCall(interceptedCall, method, requestData);
        } catch (StatusException e) {
            interceptedCall.close(e.status(), e.getMessage());
            return ServerCallResult.error(e.status(), e.getMessage(), e.trailers());
        } catch (Exception e) {
            interceptedCall.close(GrpcStatus.INTERNAL, e.getMessage());
            return ServerCallResult.error(GrpcStatus.INTERNAL, e.getMessage());
        }

        return buildResult(interceptedCall, method);
    }

    private ServerCall applyInterceptors(MethodDescriptor method, Metadata metadata, ServerCall call) {
        var current = call;
        for (var interceptor : interceptors) {
            current = interceptor.intercept(method, metadata, current);
        }
        return current;
    }

    private void dispatchCall(ServerCall call, MethodDescriptor method, byte[] requestData) {
        String path = method.path();

        switch (method.type()) {
            case UNARY -> {
                var handler = registry.getUnaryHandler(path);
                if (handler == null) {
                    throw new StatusException(GrpcStatus.UNIMPLEMENTED, "No handler for: " + path);
                }
                var requestMessage = decodeRequest(requestData, method.requestDescriptor());
                var response = handler.handle(requestMessage, call.requestMetadata());
                call.sendMessage(response);
            }
            case SERVER_STREAMING -> {
                var handler = registry.getServerStreamingHandler(path);
                if (handler == null) {
                    throw new StatusException(GrpcStatus.UNIMPLEMENTED, "No handler for: " + path);
                }
                var requestMessage = decodeRequest(requestData, method.requestDescriptor());
                handler.handle(requestMessage, call.requestMetadata(), call.responseConsumer());
            }
            case CLIENT_STREAMING -> {
                var handler = registry.getClientStreamingHandler(path);
                if (handler == null) {
                    throw new StatusException(GrpcStatus.UNIMPLEMENTED, "No handler for: " + path);
                }
                var requests = decodeAllRequests(requestData, method.requestDescriptor());
                var response = handler.handle(requests, call.requestMetadata());
                call.sendMessage(response);
            }
            case BIDI_STREAMING -> {
                var handler = registry.getBidiStreamingHandler(path);
                if (handler == null) {
                    throw new StatusException(GrpcStatus.UNIMPLEMENTED, "No handler for: " + path);
                }
                var requests = decodeAllRequests(requestData, method.requestDescriptor());
                handler.handle(requests, call.requestMetadata(), call.responseConsumer());
            }
        }
    }

    private ProtoMessage decodeRequest(byte[] framedData, MessageDescriptor descriptor) {
        var buf = ByteBuffer.wrap(framedData);
        var frame = GrpcFrameCodec.decodeFrame(buf);
        if (frame == null) {
            throw new StatusException(GrpcStatus.INTERNAL, "Failed to decode request frame");
        }
        byte[] data = GrpcFrameCodec.decompressIfNeeded(frame, GrpcEncoding.IDENTITY);
        return ProtobufCodec.decode(data, descriptor);
    }

    private List<ProtoMessage> decodeAllRequests(byte[] framedData, MessageDescriptor descriptor) {
        var buf = ByteBuffer.wrap(framedData);
        var frames = GrpcFrameCodec.decodeAllFrames(buf);
        var messages = new ArrayList<ProtoMessage>();
        for (var frame : frames) {
            byte[] data = GrpcFrameCodec.decompressIfNeeded(frame, GrpcEncoding.IDENTITY);
            messages.add(ProtobufCodec.decode(data, descriptor));
        }
        return messages;
    }

    private ServerCallResult buildResult(ServerCall call, MethodDescriptor method) {
        var responseFrames = new ArrayList<byte[]>();
        var descriptor = method.responseDescriptor();

        for (var msg : call.responseMessages()) {
            byte[] encoded = descriptor != null
                    ? ProtobufCodec.encode(msg, descriptor)
                    : ProtobufCodec.encode(msg);
            byte[] framed;
            if (defaultEncoding != GrpcEncoding.IDENTITY) {
                framed = GrpcFrameCodec.encodeCompressed(encoded, defaultEncoding);
            } else {
                framed = GrpcFrameCodec.encode(encoded);
            }
            responseFrames.add(framed);
        }

        return new ServerCallResult(
                call.status(),
                call.statusMessage(),
                call.responseMetadata(),
                call.trailers(),
                responseFrames,
                defaultEncoding
        );
    }

    /**
     * Result of processing a server call.
     */
    public record ServerCallResult(
            GrpcStatus status,
            String statusMessage,
            Metadata responseMetadata,
            Metadata trailers,
            List<byte[]> responseFrames,
            GrpcEncoding encoding
    ) {
        public static ServerCallResult error(GrpcStatus status, String message) {
            return new ServerCallResult(status, message, new Metadata(), new Metadata(),
                    List.of(), GrpcEncoding.IDENTITY);
        }

        public static ServerCallResult error(GrpcStatus status, String message, Metadata trailers) {
            return new ServerCallResult(status, message, new Metadata(), trailers,
                    List.of(), GrpcEncoding.IDENTITY);
        }

        /**
         * Combines all response frames into a single byte array.
         */
        public byte[] combinedResponseData() {
            int totalLen = responseFrames.stream().mapToInt(f -> f.length).sum();
            var combined = new byte[totalLen];
            int offset = 0;
            for (var frame : responseFrames) {
                System.arraycopy(frame, 0, combined, offset, frame.length);
                offset += frame.length;
            }
            return combined;
        }
    }
}
