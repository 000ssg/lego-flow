package ssg.legoflow.rpc.grpc.common;

import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.metadata.Metadata;
import ssg.legoflow.rpc.grpc.transport.GrpcStatus;

import static org.assertj.core.api.Assertions.*;

class StatusExceptionTest {

    @Test
    void testStatusOnly() {
        var ex = new StatusException(GrpcStatus.NOT_FOUND);
        assertThat(ex.status()).isEqualTo(GrpcStatus.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("NOT_FOUND");
        assertThat(ex.trailers()).isNotNull();
    }

    @Test
    void testStatusWithMessage() {
        var ex = new StatusException(GrpcStatus.INVALID_ARGUMENT, "bad input");
        assertThat(ex.status()).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
        assertThat(ex.getMessage()).isEqualTo("bad input");
    }

    @Test
    void testStatusWithCause() {
        var cause = new RuntimeException("root cause");
        var ex = new StatusException(GrpcStatus.INTERNAL, "failed", cause);
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void testStatusWithTrailers() {
        var trailers = new Metadata().put("x-debug", "info");
        var ex = new StatusException(GrpcStatus.UNAVAILABLE, "try again", trailers);
        assertThat(ex.trailers().get("x-debug")).isEqualTo("info");
    }

    @Test
    void testNullTrailers() {
        var ex = new StatusException(GrpcStatus.OK, "ok", (Metadata) null);
        assertThat(ex.trailers()).isNotNull();
    }

    @Test
    void testIsRuntimeException() {
        var ex = new StatusException(GrpcStatus.UNKNOWN);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
