package ssg.legoflow.rpc.grpc.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.common.StatusException;
import ssg.legoflow.rpc.grpc.protobuf.*;
import ssg.legoflow.rpc.grpc.server.GrpcServer;
import ssg.legoflow.rpc.grpc.transport.GrpcStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class GrpcChannelTest {

    private static final MessageDescriptor REQ_DESC = MessageDescriptor.builder("Req")
            .addField(FieldDescriptor.scalar(1, "value", FieldDescriptor.Type.INT32))
            .build();

    private static final MessageDescriptor RESP_DESC = MessageDescriptor.builder("Resp")
            .addField(FieldDescriptor.scalar(1, "result", FieldDescriptor.Type.INT32))
            .build();

    private GrpcServer server;
    private GrpcChannel channel;

    @BeforeEach
    void setUp() {
        server = new GrpcServer();
        channel = new GrpcChannel(server);
    }

    @Test
    void testLoopbackUnaryCall() {
        var method = MethodDescriptor.unary("test.Svc", "Double", REQ_DESC, RESP_DESC);
        var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

        server.registry().registerService(service);
        server.registry().registerUnaryHandler(method.path(), (req, md) ->
                new ProtoMessage().setVarint(1, req.getInt32(1) * 2));

        var response = channel.unaryCall(method,
                new ProtoMessage().setVarint(1, 21), CallOptions.defaults());
        assertThat(response.getInt32(1)).isEqualTo(42);
    }

    @Test
    void testLoopbackServerStreaming() {
        var method = MethodDescriptor.serverStreaming("test.Svc", "Range", REQ_DESC, RESP_DESC);
        var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

        server.registry().registerService(service);
        server.registry().registerServerStreamingHandler(method.path(), (req, md, stream) -> {
            for (int i = 0; i < 5; i++) {
                stream.accept(new ProtoMessage().setVarint(1, i));
            }
        });

        var responses = channel.serverStreamingCall(method,
                new ProtoMessage().setVarint(1, 5), CallOptions.defaults());
        assertThat(responses).hasSize(5);
        assertThat(responses.get(0).getInt32(1)).isEqualTo(0);
        assertThat(responses.get(4).getInt32(1)).isEqualTo(4);
    }

    @Test
    void testLoopbackClientStreaming() {
        var method = MethodDescriptor.clientStreaming("test.Svc", "Sum", REQ_DESC, RESP_DESC);
        var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

        server.registry().registerService(service);
        server.registry().registerClientStreamingHandler(method.path(), (requests, md) -> {
            int sum = requests.stream().mapToInt(r -> r.getInt32(1)).sum();
            return new ProtoMessage().setVarint(1, sum);
        });

        var requests = List.of(
                new ProtoMessage().setVarint(1, 10),
                new ProtoMessage().setVarint(1, 20),
                new ProtoMessage().setVarint(1, 30)
        );
        var response = channel.clientStreamingCall(method, requests, CallOptions.defaults());
        assertThat(response.getInt32(1)).isEqualTo(60);
    }

    @Test
    void testLoopbackBidiStreaming() {
        var method = MethodDescriptor.bidiStreaming("test.Svc", "Echo", REQ_DESC, RESP_DESC);
        var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

        server.registry().registerService(service);
        server.registry().registerBidiStreamingHandler(method.path(), (requests, md, stream) -> {
            for (var req : requests) {
                stream.accept(new ProtoMessage().setVarint(1, req.getInt32(1)));
            }
        });

        var requests = List.of(
                new ProtoMessage().setVarint(1, 1),
                new ProtoMessage().setVarint(1, 2)
        );
        var responses = channel.bidiStreamingCall(method, requests, CallOptions.defaults());
        assertThat(responses).hasSize(2);
    }

    @Test
    void testUnaryCallError() {
        var method = MethodDescriptor.unary("test.Svc", "Fail", REQ_DESC, RESP_DESC);
        var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

        server.registry().registerService(service);
        server.registry().registerUnaryHandler(method.path(), (req, md) -> {
            throw new StatusException(GrpcStatus.PERMISSION_DENIED, "denied");
        });

        assertThatThrownBy(() -> channel.unaryCall(method,
                new ProtoMessage().setVarint(1, 1), CallOptions.defaults()))
                .isInstanceOf(StatusException.class)
                .satisfies(ex -> assertThat(((StatusException) ex).status())
                        .isEqualTo(GrpcStatus.PERMISSION_DENIED));
    }

    @Test
    void testChannelShutdown() {
        channel.shutdown();
        assertThat(channel.isClosed()).isTrue();

        var method = MethodDescriptor.unary("test.Svc", "M", REQ_DESC, RESP_DESC);
        assertThatThrownBy(() -> channel.newCall(method, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testChannelAuthority() {
        assertThat(channel.authority()).isEqualTo("localhost");

        var remoteChannel = new GrpcChannel("grpc.example.com:443");
        assertThat(remoteChannel.authority()).isEqualTo("grpc.example.com:443");
    }

    @Test
    void testRemoteChannelReturnsUnavailable() {
        var remoteChannel = new GrpcChannel("remote:50051");
        var method = MethodDescriptor.unary("test.Svc", "M", REQ_DESC, RESP_DESC);

        assertThatThrownBy(() -> remoteChannel.unaryCall(method,
                new ProtoMessage(), CallOptions.defaults()))
                .isInstanceOf(StatusException.class)
                .satisfies(ex -> assertThat(((StatusException) ex).status())
                        .isEqualTo(GrpcStatus.UNAVAILABLE));
    }

    @Test
    void testRemoteServerStreamingReturnsUnavailable() {
        var remoteChannel = new GrpcChannel("remote:50051");
        var method = MethodDescriptor.serverStreaming("test.Svc", "M", REQ_DESC, RESP_DESC);

        assertThatThrownBy(() -> remoteChannel.serverStreamingCall(method,
                new ProtoMessage(), CallOptions.defaults()))
                .isInstanceOf(StatusException.class)
                .satisfies(ex -> assertThat(((StatusException) ex).status())
                        .isEqualTo(GrpcStatus.UNAVAILABLE));
    }

    @Test
    void testRemoteClientStreamingReturnsUnavailable() {
        var remoteChannel = new GrpcChannel("remote:50051");
        var method = MethodDescriptor.clientStreaming("test.Svc", "M", REQ_DESC, RESP_DESC);

        assertThatThrownBy(() -> remoteChannel.clientStreamingCall(method,
                List.of(new ProtoMessage()), CallOptions.defaults()))
                .isInstanceOf(StatusException.class)
                .satisfies(ex -> assertThat(((StatusException) ex).status())
                        .isEqualTo(GrpcStatus.UNAVAILABLE));
    }

    @Test
    void testRemoteBidiStreamingReturnsUnavailable() {
        var remoteChannel = new GrpcChannel("remote:50051");
        var method = MethodDescriptor.bidiStreaming("test.Svc", "M", REQ_DESC, RESP_DESC);

        assertThatThrownBy(() -> remoteChannel.bidiStreamingCall(method,
                List.of(new ProtoMessage()), CallOptions.defaults()))
                .isInstanceOf(StatusException.class)
                .satisfies(ex -> assertThat(((StatusException) ex).status())
                        .isEqualTo(GrpcStatus.UNAVAILABLE));
    }
}
