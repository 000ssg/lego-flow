package ssg.legoflow.rpc.grpc.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive gRPC demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code GrpcServer} with loopback channel.
 * To test against an external gRPC server, set {@code DemoGrpcAll.USE_EXTERNAL = true}
 * and configure host/port before running.</p>
 */
class DemoGrpcAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoGrpcAll.runAll();

        assertThat(results.unaryRpc())
                .as("Unary RPC (add, multiply, divide)")
                .isTrue();

        assertThat(results.serverStreaming())
                .as("Server streaming chunks received")
                .isGreaterThanOrEqualTo(1);

        assertThat(results.clientStreaming())
                .as("Client streaming upload with checksum")
                .isTrue();

        assertThat(results.bidiStreaming())
                .as("Bidi streaming responses (3 messages * 2 responses)")
                .isEqualTo(6);

        assertThat(results.metadata())
                .as("Metadata round-trip and merge")
                .isTrue();

        assertThat(results.interceptors())
                .as("Interceptor invocations observed")
                .isGreaterThanOrEqualTo(1);

        assertThat(results.deadlineTimeout())
                .as("Deadline/timeout configuration")
                .isTrue();

        assertThat(results.statusCodes())
                .as("Status code error handling")
                .isTrue();

        assertThat(results.protobufEncoding())
                .as("Protobuf encode/decode round-trip")
                .isTrue();
    }
}
