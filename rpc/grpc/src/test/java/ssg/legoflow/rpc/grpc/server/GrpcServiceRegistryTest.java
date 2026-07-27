package ssg.legoflow.rpc.grpc.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.protobuf.MessageDescriptor;
import ssg.legoflow.rpc.grpc.protobuf.ServiceDescriptor;

import static org.assertj.core.api.Assertions.*;

class GrpcServiceRegistryTest {

    @Test
    void testRegisterAndLookupService() {
        var registry = new GrpcServiceRegistry();
        var reqDesc = MessageDescriptor.builder("Req").build();
        var respDesc = MessageDescriptor.builder("Resp").build();
        var method = MethodDescriptor.unary("test.Svc", "Do", reqDesc, respDesc);
        var service = ServiceDescriptor.builder("test.Svc").addMethod(method).build();

        registry.registerService(service);
        assertThat(registry.getService("test.Svc")).isNotNull();
        assertThat(registry.getMethodDescriptor("/test.Svc/Do")).isNotNull();
        assertThat(registry.hasMethod("/test.Svc/Do")).isTrue();
    }

    @Test
    void testRegisterUnaryHandler() {
        var registry = new GrpcServiceRegistry();
        registry.registerUnaryHandler("/svc/method", (req, md) -> null);
        assertThat(registry.getUnaryHandler("/svc/method")).isNotNull();
    }

    @Test
    void testRegisterStreamingHandlers() {
        var registry = new GrpcServiceRegistry();

        registry.registerServerStreamingHandler("/svc/ss", (req, md, stream) -> {});
        registry.registerClientStreamingHandler("/svc/cs", (reqs, md) -> null);
        registry.registerBidiStreamingHandler("/svc/bidi", (reqs, md, stream) -> {});

        assertThat(registry.getServerStreamingHandler("/svc/ss")).isNotNull();
        assertThat(registry.getClientStreamingHandler("/svc/cs")).isNotNull();
        assertThat(registry.getBidiStreamingHandler("/svc/bidi")).isNotNull();
    }

    @Test
    void testServicesCollection() {
        var registry = new GrpcServiceRegistry();
        assertThat(registry.services()).isEmpty();

        var service = ServiceDescriptor.builder("test.Svc").build();
        registry.registerService(service);
        assertThat(registry.services()).hasSize(1);
    }

    @Test
    void testMethodNotFound() {
        var registry = new GrpcServiceRegistry();
        assertThat(registry.getMethodDescriptor("/missing/Method")).isNull();
        assertThat(registry.hasMethod("/missing/Method")).isFalse();
    }
}
