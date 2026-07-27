package ssg.legoflow.rpc.grpc.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.client.CallOptions;
import ssg.legoflow.rpc.grpc.client.GrpcChannel;
import ssg.legoflow.rpc.grpc.client.GrpcStub;
import ssg.legoflow.rpc.grpc.common.StatusException;
import ssg.legoflow.rpc.grpc.protobuf.ProtoMessage;
import ssg.legoflow.rpc.grpc.server.GrpcServer;
import ssg.legoflow.rpc.grpc.transport.GrpcStatus;

import static org.assertj.core.api.Assertions.*;

class CalculatorServiceTest {

    private GrpcStub stub;

    @BeforeEach
    void setUp() {
        var server = new GrpcServer();
        CalculatorService.register(server);
        var channel = new GrpcChannel(server);
        stub = new GrpcStub(channel, CalculatorService.serviceDescriptor());
    }

    @Test
    void testAdd() {
        var request = new ProtoMessage().setDouble(1, 3.0).setDouble(2, 4.0);
        var response = stub.unaryCall("Add", request);
        assertThat(response.getDouble(1)).isEqualTo(7.0);
    }

    @Test
    void testAddNegative() {
        var request = new ProtoMessage().setDouble(1, -5.0).setDouble(2, 3.0);
        var response = stub.unaryCall("Add", request);
        assertThat(response.getDouble(1)).isEqualTo(-2.0);
    }

    @Test
    void testAddZero() {
        var request = new ProtoMessage().setDouble(1, 0.0).setDouble(2, 0.0);
        var response = stub.unaryCall("Add", request);
        assertThat(response.getDouble(1)).isEqualTo(0.0);
    }

    @Test
    void testMultiply() {
        var request = new ProtoMessage().setDouble(1, 6.0).setDouble(2, 7.0);
        var response = stub.unaryCall("Multiply", request);
        assertThat(response.getDouble(1)).isEqualTo(42.0);
    }

    @Test
    void testMultiplyByZero() {
        var request = new ProtoMessage().setDouble(1, 100.0).setDouble(2, 0.0);
        var response = stub.unaryCall("Multiply", request);
        assertThat(response.getDouble(1)).isEqualTo(0.0);
    }

    @Test
    void testDivide() {
        var request = new ProtoMessage().setDouble(1, 10.0).setDouble(2, 3.0);
        var response = stub.unaryCall("Divide", request);
        assertThat(response.getDouble(1)).isCloseTo(3.333, within(0.01));
    }

    @Test
    void testDivideExact() {
        var request = new ProtoMessage().setDouble(1, 20.0).setDouble(2, 4.0);
        var response = stub.unaryCall("Divide", request);
        assertThat(response.getDouble(1)).isEqualTo(5.0);
    }

    @Test
    void testDivideByZero() {
        var request = new ProtoMessage().setDouble(1, 10.0).setDouble(2, 0.0);
        assertThatThrownBy(() -> stub.unaryCall("Divide", request))
                .isInstanceOf(StatusException.class)
                .satisfies(ex -> assertThat(((StatusException) ex).status())
                        .isEqualTo(GrpcStatus.INVALID_ARGUMENT));
    }

    @Test
    void testServiceDescriptor() {
        var desc = CalculatorService.serviceDescriptor();
        assertThat(desc.fullName()).isEqualTo("demo.Calculator");
        assertThat(desc.methods()).hasSize(3);
        assertThat(desc.method("Add")).isNotNull();
        assertThat(desc.method("Multiply")).isNotNull();
        assertThat(desc.method("Divide")).isNotNull();
    }

    @Test
    void testAddLargeNumbers() {
        var request = new ProtoMessage().setDouble(1, 1e15).setDouble(2, 2e15);
        var response = stub.unaryCall("Add", request);
        assertThat(response.getDouble(1)).isEqualTo(3e15);
    }

    @Test
    void testMultiplyFractional() {
        var request = new ProtoMessage().setDouble(1, 0.5).setDouble(2, 0.3);
        var response = stub.unaryCall("Multiply", request);
        assertThat(response.getDouble(1)).isCloseTo(0.15, within(0.001));
    }
}
