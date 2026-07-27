package ssg.legoflow.rpc.grpc.client;

import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.transport.GrpcEncoding;
import ssg.legoflow.rpc.grpc.transport.GrpcTimeout;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class CallOptionsTest {

    @Test
    void testDefaults() {
        var options = CallOptions.defaults();
        assertThat(options.timeout()).isNull();
        assertThat(options.encoding()).isEqualTo(GrpcEncoding.IDENTITY);
        assertThat(options.metadata()).isNotNull();
        assertThat(options.authority()).isNull();
        assertThat(options.maxResponseSize()).isEqualTo(4 * 1024 * 1024);
    }

    @Test
    void testTimeout() {
        var options = new CallOptions().timeout(GrpcTimeout.ofSeconds(10));
        assertThat(options.timeout().toMillis()).isEqualTo(10000);
    }

    @Test
    void testDeadline() {
        var options = CallOptions.withDeadline(Duration.ofSeconds(5));
        assertThat(options.timeout()).isNotNull();
        assertThat(options.timeout().toMillis()).isEqualTo(5000);
    }

    @Test
    void testEncoding() {
        var options = CallOptions.withEncoding(GrpcEncoding.GZIP);
        assertThat(options.encoding()).isEqualTo(GrpcEncoding.GZIP);
    }

    @Test
    void testAuthority() {
        var options = new CallOptions().authority("example.com:443");
        assertThat(options.authority()).isEqualTo("example.com:443");
    }

    @Test
    void testMaxResponseSize() {
        var options = new CallOptions().maxResponseSize(1024);
        assertThat(options.maxResponseSize()).isEqualTo(1024);
    }

    @Test
    void testFluentApi() {
        var options = new CallOptions()
                .encoding(GrpcEncoding.GZIP)
                .authority("host:1234")
                .deadline(Duration.ofMinutes(1));
        assertThat(options.encoding()).isEqualTo(GrpcEncoding.GZIP);
        assertThat(options.authority()).isEqualTo("host:1234");
        assertThat(options.timeout()).isNotNull();
    }
}
