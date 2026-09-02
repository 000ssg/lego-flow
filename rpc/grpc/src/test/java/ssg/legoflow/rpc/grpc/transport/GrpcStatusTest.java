package ssg.legoflow.rpc.grpc.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
class GrpcStatusTest {

    @Test
    void testOkCode() {
        assertThat(GrpcStatus.OK.code()).isEqualTo(0);
        assertThat(GrpcStatus.OK.isOk()).isTrue();
        assertThat(GrpcStatus.OK.isError()).isFalse();
    }

    @Test
    void testCancelledCode() {
        assertThat(GrpcStatus.CANCELLED.code()).isEqualTo(1);
    }

    @Test
    void testUnknownCode() {
        assertThat(GrpcStatus.UNKNOWN.code()).isEqualTo(2);
    }

    @Test
    void testInvalidArgumentCode() {
        assertThat(GrpcStatus.INVALID_ARGUMENT.code()).isEqualTo(3);
    }

    @Test
    void testDeadlineExceededCode() {
        assertThat(GrpcStatus.DEADLINE_EXCEEDED.code()).isEqualTo(4);
    }

    @Test
    void testNotFoundCode() {
        assertThat(GrpcStatus.NOT_FOUND.code()).isEqualTo(5);
    }

    @Test
    void testAlreadyExistsCode() {
        assertThat(GrpcStatus.ALREADY_EXISTS.code()).isEqualTo(6);
    }

    @Test
    void testPermissionDeniedCode() {
        assertThat(GrpcStatus.PERMISSION_DENIED.code()).isEqualTo(7);
    }

    @Test
    void testResourceExhaustedCode() {
        assertThat(GrpcStatus.RESOURCE_EXHAUSTED.code()).isEqualTo(8);
    }

    @Test
    void testFailedPreconditionCode() {
        assertThat(GrpcStatus.FAILED_PRECONDITION.code()).isEqualTo(9);
    }

    @Test
    void testAbortedCode() {
        assertThat(GrpcStatus.ABORTED.code()).isEqualTo(10);
    }

    @Test
    void testOutOfRangeCode() {
        assertThat(GrpcStatus.OUT_OF_RANGE.code()).isEqualTo(11);
    }

    @Test
    void testUnimplementedCode() {
        assertThat(GrpcStatus.UNIMPLEMENTED.code()).isEqualTo(12);
    }

    @Test
    void testInternalCode() {
        assertThat(GrpcStatus.INTERNAL.code()).isEqualTo(13);
    }

    @Test
    void testUnavailableCode() {
        assertThat(GrpcStatus.UNAVAILABLE.code()).isEqualTo(14);
    }

    @Test
    void testDataLossCode() {
        assertThat(GrpcStatus.DATA_LOSS.code()).isEqualTo(15);
    }

    @Test
    void testUnauthenticatedCode() {
        assertThat(GrpcStatus.UNAUTHENTICATED.code()).isEqualTo(16);
    }

    @Test
    void testAll17Codes() {
        assertThat(GrpcStatus.values()).hasSize(17);
    }

    @ParameterizedTest
    @EnumSource(GrpcStatus.class)
    void testFromCodeRoundTrip(GrpcStatus status) {
        assertThat(GrpcStatus.fromCode(status.code())).isEqualTo(status);
    }

    @Test
    void testFromCodeInvalid() {
        assertThatThrownBy(() -> GrpcStatus.fromCode(17))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = GrpcStatus.class, names = "OK", mode = EnumSource.Mode.EXCLUDE)
    void testErrorCodes(GrpcStatus status) {
        assertThat(status.isOk()).isFalse();
        assertThat(status.isError()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(GrpcStatus.class)
    void testDescriptionNotNull(GrpcStatus status) {
        assertThat(status.description()).isNotNull();
        assertThat(status.description()).isNotEmpty();
    }
}
