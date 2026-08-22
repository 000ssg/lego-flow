package ssg.legoflow.rpc.grpc.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.metadata.Metadata;
import ssg.legoflow.rpc.grpc.protobuf.MessageDescriptor;
import ssg.legoflow.rpc.grpc.protobuf.ProtoMessage;
import ssg.legoflow.rpc.grpc.transport.GrpcStatus;
import static org.assertj.core.api.Assertions.*;
class ServerCallTest {

    private final MethodDescriptor method = MethodDescriptor.unary(
            "test.Svc", "Method",
            MessageDescriptor.builder("Req").build(),
            MessageDescriptor.builder("Resp").build());

    @Test
    void testInitialState() {
        var call = new ServerCall(method, new Metadata());
        assertThat(call.method()).isEqualTo(method);
        assertThat(call.status()).isEqualTo(GrpcStatus.OK);
        assertThat(call.isCancelled()).isFalse();
        assertThat(call.responseMessages()).isEmpty();
    }

    @Test
    void testSendMessage() {
        var call = new ServerCall(method, new Metadata());
        call.sendMessage(new ProtoMessage().setVarint(1, 42));
        assertThat(call.responseMessages()).hasSize(1);
    }

    @Test
    void testSendMultipleMessages() {
        var call = new ServerCall(method, new Metadata());
        call.sendMessage(new ProtoMessage().setVarint(1, 1));
        call.sendMessage(new ProtoMessage().setVarint(1, 2));
        call.sendMessage(new ProtoMessage().setVarint(1, 3));
        assertThat(call.responseMessages()).hasSize(3);
    }

    @Test
    void testClose() {
        var call = new ServerCall(method, new Metadata());
        call.close(GrpcStatus.NOT_FOUND, "not found");
        assertThat(call.status()).isEqualTo(GrpcStatus.NOT_FOUND);
        assertThat(call.statusMessage()).isEqualTo("not found");
    }

    @Test
    void testCancel() {
        var call = new ServerCall(method, new Metadata());
        call.cancel();
        assertThat(call.isCancelled()).isTrue();
        assertThat(call.status()).isEqualTo(GrpcStatus.CANCELLED);
    }

    @Test
    void testResponseConsumer() {
        var call = new ServerCall(method, new Metadata());
        var consumer = call.responseConsumer();
        consumer.accept(new ProtoMessage().setVarint(1, 10));
        assertThat(call.responseMessages()).hasSize(1);
    }

    @Test
    void testNullMetadata() {
        var call = new ServerCall(method, null);
        assertThat(call.requestMetadata()).isNotNull();
    }

    @Test
    void testResponseMetadata() {
        var call = new ServerCall(method, new Metadata());
        call.responseMetadata().put("x-key", "value");
        assertThat(call.responseMetadata().get("x-key")).isEqualTo("value");
    }

    @Test
    void testTrailers() {
        var call = new ServerCall(method, new Metadata());
        call.trailers().put("x-trailer", "data");
        assertThat(call.trailers().get("x-trailer")).isEqualTo("data");
    }
}
