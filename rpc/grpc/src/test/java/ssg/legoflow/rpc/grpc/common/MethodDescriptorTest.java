package ssg.legoflow.rpc.grpc.common;

import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.protobuf.MessageDescriptor;

import static org.assertj.core.api.Assertions.*;

class MethodDescriptorTest {

    private final MessageDescriptor reqDesc = MessageDescriptor.builder("Request").build();
    private final MessageDescriptor respDesc = MessageDescriptor.builder("Response").build();

    @Test
    void testUnaryMethod() {
        var method = MethodDescriptor.unary("pkg.Svc", "DoWork", reqDesc, respDesc);
        assertThat(method.fullMethodName()).isEqualTo("pkg.Svc/DoWork");
        assertThat(method.serviceName()).isEqualTo("pkg.Svc");
        assertThat(method.methodName()).isEqualTo("DoWork");
        assertThat(method.type()).isEqualTo(MethodType.UNARY);
        assertThat(method.path()).isEqualTo("/pkg.Svc/DoWork");
    }

    @Test
    void testServerStreamingMethod() {
        var method = MethodDescriptor.serverStreaming("pkg.Svc", "ListItems", reqDesc, respDesc);
        assertThat(method.type()).isEqualTo(MethodType.SERVER_STREAMING);
        assertThat(method.isServerStreaming()).isTrue();
        assertThat(method.isClientStreaming()).isFalse();
    }

    @Test
    void testClientStreamingMethod() {
        var method = MethodDescriptor.clientStreaming("pkg.Svc", "Upload", reqDesc, respDesc);
        assertThat(method.type()).isEqualTo(MethodType.CLIENT_STREAMING);
        assertThat(method.isClientStreaming()).isTrue();
        assertThat(method.isServerStreaming()).isFalse();
    }

    @Test
    void testBidiStreamingMethod() {
        var method = MethodDescriptor.bidiStreaming("pkg.Svc", "Chat", reqDesc, respDesc);
        assertThat(method.type()).isEqualTo(MethodType.BIDI_STREAMING);
        assertThat(method.isClientStreaming()).isTrue();
        assertThat(method.isServerStreaming()).isTrue();
    }

    @Test
    void testRequestDescriptor() {
        var method = MethodDescriptor.unary("pkg.Svc", "M", reqDesc, respDesc);
        assertThat(method.requestDescriptor()).isEqualTo(reqDesc);
    }

    @Test
    void testResponseDescriptor() {
        var method = MethodDescriptor.unary("pkg.Svc", "M", reqDesc, respDesc);
        assertThat(method.responseDescriptor()).isEqualTo(respDesc);
    }
}
