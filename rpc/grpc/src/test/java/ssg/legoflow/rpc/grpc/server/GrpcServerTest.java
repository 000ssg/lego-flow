package ssg.legoflow.rpc.grpc.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.common.StatusException;
import ssg.legoflow.rpc.grpc.metadata.Metadata;
import ssg.legoflow.rpc.grpc.protobuf.*;
import ssg.legoflow.rpc.grpc.transport.GrpcEncoding;
import ssg.legoflow.rpc.grpc.transport.GrpcFrameCodec;
import ssg.legoflow.rpc.grpc.transport.GrpcStatus;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class GrpcServerTest {

    private static final MessageDescriptor REQ_DESC = MessageDescriptor.builder("Req")
            .addField(FieldDescriptor.scalar(1, "value", FieldDescriptor.Type.INT32))
            .build();

    private static final MessageDescriptor RESP_DESC = MessageDescriptor.builder("Resp")
            .addField(FieldDescriptor.scalar(1, "result", FieldDescriptor.Type.INT32))
            .build();

    private GrpcServer server;

    @BeforeEach
    void setUp() {
        server = new GrpcServer();
    }

    @Nested
    class UnaryTests {

        @Test
        void testUnaryCallSuccess() {
            var method = MethodDescriptor.unary("test.Svc", "Echo", REQ_DESC, RESP_DESC);
            var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

            server.registry().registerService(service);
            server.registry().registerUnaryHandler(method.path(), (req, md) ->
                    new ProtoMessage().setVarint(1, req.getInt32(1) * 2));

            var request = new ProtoMessage().setVarint(1, 21);
            byte[] encoded = ProtobufCodec.encode(request, REQ_DESC);
            byte[] framed = GrpcFrameCodec.encode(encoded);

            var result = server.processRequest(method.path(), framed, new Metadata());
            assertThat(result.status()).isEqualTo(GrpcStatus.OK);
            assertThat(result.responseFrames()).hasSize(1);
        }

        @Test
        void testUnaryCallError() {
            var method = MethodDescriptor.unary("test.Svc", "Fail", REQ_DESC, RESP_DESC);
            var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

            server.registry().registerService(service);
            server.registry().registerUnaryHandler(method.path(), (req, md) -> {
                throw new StatusException(GrpcStatus.INVALID_ARGUMENT, "bad request");
            });

            var request = new ProtoMessage().setVarint(1, 0);
            byte[] encoded = ProtobufCodec.encode(request, REQ_DESC);
            byte[] framed = GrpcFrameCodec.encode(encoded);

            var result = server.processRequest(method.path(), framed, new Metadata());
            assertThat(result.status()).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
            assertThat(result.statusMessage()).isEqualTo("bad request");
        }

        @Test
        void testUnaryCallNotFound() {
            var result = server.processRequest("/nonexistent/Method", new byte[0], new Metadata());
            assertThat(result.status()).isEqualTo(GrpcStatus.UNIMPLEMENTED);
        }

        @Test
        void testUnaryCallNoHandler() {
            var method = MethodDescriptor.unary("test.Svc", "NoHandler", REQ_DESC, RESP_DESC);
            var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();
            server.registry().registerService(service);

            var request = new ProtoMessage().setVarint(1, 1);
            byte[] encoded = ProtobufCodec.encode(request, REQ_DESC);
            byte[] framed = GrpcFrameCodec.encode(encoded);

            var result = server.processRequest(method.path(), framed, new Metadata());
            assertThat(result.status()).isEqualTo(GrpcStatus.UNIMPLEMENTED);
        }

        @Test
        void testUnaryCallRuntimeException() {
            var method = MethodDescriptor.unary("test.Svc", "Crash", REQ_DESC, RESP_DESC);
            var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

            server.registry().registerService(service);
            server.registry().registerUnaryHandler(method.path(), (req, md) -> {
                throw new RuntimeException("unexpected error");
            });

            var request = new ProtoMessage().setVarint(1, 1);
            byte[] encoded = ProtobufCodec.encode(request, REQ_DESC);
            byte[] framed = GrpcFrameCodec.encode(encoded);

            var result = server.processRequest(method.path(), framed, new Metadata());
            assertThat(result.status()).isEqualTo(GrpcStatus.INTERNAL);
        }
    }

    @Nested
    class ServerStreamingTests {

        @Test
        void testServerStreamingCall() {
            var method = MethodDescriptor.serverStreaming("test.Svc", "Stream", REQ_DESC, RESP_DESC);
            var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

            server.registry().registerService(service);
            server.registry().registerServerStreamingHandler(method.path(), (req, md, stream) -> {
                int count = req.getInt32(1);
                for (int i = 0; i < count; i++) {
                    stream.accept(new ProtoMessage().setVarint(1, i));
                }
            });

            var request = new ProtoMessage().setVarint(1, 3);
            byte[] encoded = ProtobufCodec.encode(request, REQ_DESC);
            byte[] framed = GrpcFrameCodec.encode(encoded);

            var result = server.processRequest(method.path(), framed, new Metadata());
            assertThat(result.status()).isEqualTo(GrpcStatus.OK);
            assertThat(result.responseFrames()).hasSize(3);
        }

        @Test
        void testServerStreamingEmpty() {
            var method = MethodDescriptor.serverStreaming("test.Svc", "EmptyStream", REQ_DESC, RESP_DESC);
            var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

            server.registry().registerService(service);
            server.registry().registerServerStreamingHandler(method.path(), (req, md, stream) -> {
                // Send nothing
            });

            var request = new ProtoMessage().setVarint(1, 0);
            byte[] encoded = ProtobufCodec.encode(request, REQ_DESC);
            byte[] framed = GrpcFrameCodec.encode(encoded);

            var result = server.processRequest(method.path(), framed, new Metadata());
            assertThat(result.status()).isEqualTo(GrpcStatus.OK);
            assertThat(result.responseFrames()).isEmpty();
        }
    }

    @Nested
    class ClientStreamingTests {

        @Test
        void testClientStreamingCall() {
            var method = MethodDescriptor.clientStreaming("test.Svc", "Collect", REQ_DESC, RESP_DESC);
            var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

            server.registry().registerService(service);
            server.registry().registerClientStreamingHandler(method.path(), (requests, md) -> {
                int sum = requests.stream().mapToInt(r -> r.getInt32(1)).sum();
                return new ProtoMessage().setVarint(1, sum);
            });

            // Create multiple framed requests
            byte[] frame1 = GrpcFrameCodec.encode(
                    ProtobufCodec.encode(new ProtoMessage().setVarint(1, 10), REQ_DESC));
            byte[] frame2 = GrpcFrameCodec.encode(
                    ProtobufCodec.encode(new ProtoMessage().setVarint(1, 20), REQ_DESC));
            byte[] combined = new byte[frame1.length + frame2.length];
            System.arraycopy(frame1, 0, combined, 0, frame1.length);
            System.arraycopy(frame2, 0, combined, frame1.length, frame2.length);

            var result = server.processRequest(method.path(), combined, new Metadata());
            assertThat(result.status()).isEqualTo(GrpcStatus.OK);
            assertThat(result.responseFrames()).hasSize(1);
        }
    }

    @Nested
    class BidiStreamingTests {

        @Test
        void testBidiStreamingCall() {
            var method = MethodDescriptor.bidiStreaming("test.Svc", "Bidi", REQ_DESC, RESP_DESC);
            var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

            server.registry().registerService(service);
            server.registry().registerBidiStreamingHandler(method.path(), (requests, md, stream) -> {
                for (var req : requests) {
                    stream.accept(new ProtoMessage().setVarint(1, req.getInt32(1) + 1));
                }
            });

            byte[] frame1 = GrpcFrameCodec.encode(
                    ProtobufCodec.encode(new ProtoMessage().setVarint(1, 5), REQ_DESC));
            byte[] frame2 = GrpcFrameCodec.encode(
                    ProtobufCodec.encode(new ProtoMessage().setVarint(1, 10), REQ_DESC));
            byte[] combined = new byte[frame1.length + frame2.length];
            System.arraycopy(frame1, 0, combined, 0, frame1.length);
            System.arraycopy(frame2, 0, combined, frame1.length, frame2.length);

            var result = server.processRequest(method.path(), combined, new Metadata());
            assertThat(result.status()).isEqualTo(GrpcStatus.OK);
            assertThat(result.responseFrames()).hasSize(2);
        }
    }

    @Nested
    class InterceptorTests {

        @Test
        void testServerInterceptor() {
            var method = MethodDescriptor.unary("test.Svc", "WithInterceptor", REQ_DESC, RESP_DESC);
            var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

            server.registry().registerService(service);
            server.registry().registerUnaryHandler(method.path(), (req, md) ->
                    new ProtoMessage().setVarint(1, 42));

            // Interceptor that adds metadata to the response
            server.addInterceptor((m, metadata, next) -> {
                next.responseMetadata().put("x-intercepted", "true");
                return next;
            });

            var request = new ProtoMessage().setVarint(1, 1);
            byte[] encoded = ProtobufCodec.encode(request, REQ_DESC);
            byte[] framed = GrpcFrameCodec.encode(encoded);

            var result = server.processRequest(method.path(), framed, new Metadata());
            assertThat(result.status()).isEqualTo(GrpcStatus.OK);
            assertThat(result.responseMetadata().get("x-intercepted")).isEqualTo("true");
        }

        @Test
        void testMultipleInterceptors() {
            var method = MethodDescriptor.unary("test.Svc", "Multi", REQ_DESC, RESP_DESC);
            var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

            server.registry().registerService(service);
            server.registry().registerUnaryHandler(method.path(), (req, md) ->
                    new ProtoMessage().setVarint(1, 1));

            server.addInterceptor((m, metadata, next) -> {
                next.responseMetadata().put("x-first", "1");
                return next;
            });
            server.addInterceptor((m, metadata, next) -> {
                next.responseMetadata().put("x-second", "2");
                return next;
            });

            var request = new ProtoMessage().setVarint(1, 1);
            byte[] encoded = ProtobufCodec.encode(request, REQ_DESC);
            byte[] framed = GrpcFrameCodec.encode(encoded);

            var result = server.processRequest(method.path(), framed, new Metadata());
            assertThat(result.responseMetadata().get("x-first")).isEqualTo("1");
            assertThat(result.responseMetadata().get("x-second")).isEqualTo("2");
        }
    }

    @Nested
    class CompressionTests {

        @Test
        void testGzipCompression() {
            var compressedServer = new GrpcServer(GrpcEncoding.GZIP);
            var method = MethodDescriptor.unary("test.Svc", "Compressed", REQ_DESC, RESP_DESC);
            var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

            compressedServer.registry().registerService(service);
            compressedServer.registry().registerUnaryHandler(method.path(), (req, md) ->
                    new ProtoMessage().setVarint(1, 99));

            var request = new ProtoMessage().setVarint(1, 1);
            byte[] encoded = ProtobufCodec.encode(request, REQ_DESC);
            byte[] framed = GrpcFrameCodec.encode(encoded);

            var result = compressedServer.processRequest(method.path(), framed, new Metadata());
            assertThat(result.status()).isEqualTo(GrpcStatus.OK);
            assertThat(result.encoding()).isEqualTo(GrpcEncoding.GZIP);
        }
    }

    @Nested
    class ServerCallResultTests {

        @Test
        void testCombinedResponseData() {
            var frames = List.of(new byte[]{1, 2, 3}, new byte[]{4, 5});
            var result = new GrpcServer.ServerCallResult(
                    GrpcStatus.OK, null, new Metadata(), new Metadata(),
                    frames, GrpcEncoding.IDENTITY);

            assertThat(result.combinedResponseData()).containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        void testErrorResult() {
            var result = GrpcServer.ServerCallResult.error(GrpcStatus.NOT_FOUND, "missing");
            assertThat(result.status()).isEqualTo(GrpcStatus.NOT_FOUND);
            assertThat(result.statusMessage()).isEqualTo("missing");
            assertThat(result.responseFrames()).isEmpty();
        }
    }
}
