package ssg.legoflow.rpc.grpc.protobuf;

import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import static org.assertj.core.api.Assertions.*;
class ServiceDescriptorTest {

    @Test
    void testBuildServiceDescriptor() {
        var reqDesc = MessageDescriptor.builder("Request").build();
        var respDesc = MessageDescriptor.builder("Response").build();

        var service = ServiceDescriptor.builder("test.MyService")
                .addMethod(MethodDescriptor.unary("test.MyService", "DoWork", reqDesc, respDesc))
                .build();

        assertThat(service.fullName()).isEqualTo("test.MyService");
        assertThat(service.simpleName()).isEqualTo("MyService");
        assertThat(service.methods()).hasSize(1);
        assertThat(service.method("DoWork")).isNotNull();
    }

    @Test
    void testMultipleMethods() {
        var reqDesc = MessageDescriptor.builder("Request").build();
        var respDesc = MessageDescriptor.builder("Response").build();

        var service = ServiceDescriptor.builder("pkg.Svc")
                .addMethod(MethodDescriptor.unary("pkg.Svc", "A", reqDesc, respDesc))
                .addMethod(MethodDescriptor.serverStreaming("pkg.Svc", "B", reqDesc, respDesc))
                .addMethod(MethodDescriptor.clientStreaming("pkg.Svc", "C", reqDesc, respDesc))
                .build();

        assertThat(service.methods()).hasSize(3);
    }

    @Test
    void testSimpleNameWithPackage() {
        var service = ServiceDescriptor.builder("com.example.service.MyService").build();
        assertThat(service.simpleName()).isEqualTo("MyService");
    }

    @Test
    void testSimpleNameWithoutPackage() {
        var service = ServiceDescriptor.builder("MyService").build();
        assertThat(service.simpleName()).isEqualTo("MyService");
    }

    @Test
    void testMethodNotFound() {
        var service = ServiceDescriptor.builder("test.Svc").build();
        assertThat(service.method("NonExistent")).isNull();
    }
}
