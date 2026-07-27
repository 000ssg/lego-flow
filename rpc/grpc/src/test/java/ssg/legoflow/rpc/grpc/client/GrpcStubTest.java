package ssg.legoflow.rpc.grpc.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.protobuf.*;
import ssg.legoflow.rpc.grpc.server.GrpcServer;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class GrpcStubTest {

    private static final MessageDescriptor REQ_DESC = MessageDescriptor.builder("Req")
            .addField(FieldDescriptor.scalar(1, "value", FieldDescriptor.Type.INT32))
            .build();

    private static final MessageDescriptor RESP_DESC = MessageDescriptor.builder("Resp")
            .addField(FieldDescriptor.scalar(1, "result", FieldDescriptor.Type.INT32))
            .build();

    private GrpcServer server;
    private GrpcChannel channel;
    private GrpcStub stub;

    @BeforeEach
    void setUp() {
        server = new GrpcServer();

        var method = MethodDescriptor.unary("test.Svc", "Square", REQ_DESC, RESP_DESC);
        var ssMethod = MethodDescriptor.serverStreaming("test.Svc", "Range", REQ_DESC, RESP_DESC);
        var csMethod = MethodDescriptor.clientStreaming("test.Svc", "Sum", REQ_DESC, RESP_DESC);
        var bidiMethod = MethodDescriptor.bidiStreaming("test.Svc", "Echo", REQ_DESC, RESP_DESC);

        var service = ServiceDescriptor.builder("test.Svc")
                .addMethod(method)
                .addMethod(ssMethod)
                .addMethod(csMethod)
                .addMethod(bidiMethod)
                .build();

        server.registry().registerService(service);

        server.registry().registerUnaryHandler(method.path(), (req, md) -> {
            int v = req.getInt32(1);
            return new ProtoMessage().setVarint(1, v * v);
        });

        server.registry().registerServerStreamingHandler(ssMethod.path(), (req, md, stream) -> {
            for (int i = 0; i < req.getInt32(1); i++) {
                stream.accept(new ProtoMessage().setVarint(1, i));
            }
        });

        server.registry().registerClientStreamingHandler(csMethod.path(), (requests, md) -> {
            int sum = requests.stream().mapToInt(r -> r.getInt32(1)).sum();
            return new ProtoMessage().setVarint(1, sum);
        });

        server.registry().registerBidiStreamingHandler(bidiMethod.path(), (requests, md, stream) -> {
            for (var req : requests) {
                stream.accept(new ProtoMessage().setVarint(1, req.getInt32(1)));
            }
        });

        channel = new GrpcChannel(server);
        stub = new GrpcStub(channel, service);
    }

    @Test
    void testUnaryCall() {
        var response = stub.unaryCall("Square", new ProtoMessage().setVarint(1, 5));
        assertThat(response.getInt32(1)).isEqualTo(25);
    }

    @Test
    void testServerStreamingCall() {
        var responses = stub.serverStreamingCall("Range", new ProtoMessage().setVarint(1, 3));
        assertThat(responses).hasSize(3);
    }

    @Test
    void testClientStreamingCall() {
        var requests = List.of(
                new ProtoMessage().setVarint(1, 1),
                new ProtoMessage().setVarint(1, 2),
                new ProtoMessage().setVarint(1, 3)
        );
        var response = stub.clientStreamingCall("Sum", requests);
        assertThat(response.getInt32(1)).isEqualTo(6);
    }

    @Test
    void testBidiStreamingCall() {
        var requests = List.of(
                new ProtoMessage().setVarint(1, 10),
                new ProtoMessage().setVarint(1, 20)
        );
        var responses = stub.bidiStreamingCall("Echo", requests);
        assertThat(responses).hasSize(2);
    }

    @Test
    void testMethodNotFound() {
        assertThatThrownBy(() -> stub.unaryCall("NonExistent", new ProtoMessage()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Method not found");
    }

    @Test
    void testServiceDescriptor() {
        assertThat(stub.serviceDescriptor().fullName()).isEqualTo("test.Svc");
    }

    @Test
    void testChannel() {
        assertThat(stub.channel()).isEqualTo(channel);
    }
}
