package ssg.legoflow.media.rtsp.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RtspStatus}.
 */
class RtspStatusTest {

    @Test
    void testSuccessStatuses() {
        assertThat(RtspStatus.OK.isSuccess()).isTrue();
        assertThat(RtspStatus.OK.code()).isEqualTo(200);
        assertThat(RtspStatus.OK.reason()).isEqualTo("OK");
    }

    @Test
    void testClientErrorStatuses() {
        assertThat(RtspStatus.BAD_REQUEST.isError()).isTrue();
        assertThat(RtspStatus.NOT_FOUND.isError()).isTrue();
        assertThat(RtspStatus.SESSION_NOT_FOUND.code()).isEqualTo(454);
        assertThat(RtspStatus.METHOD_NOT_VALID_IN_THIS_STATE.code()).isEqualTo(455);
    }

    @Test
    void testServerErrorStatuses() {
        assertThat(RtspStatus.INTERNAL_SERVER_ERROR.isError()).isTrue();
        assertThat(RtspStatus.INTERNAL_SERVER_ERROR.code()).isEqualTo(500);
        assertThat(RtspStatus.NOT_IMPLEMENTED.code()).isEqualTo(501);
        assertThat(RtspStatus.RTSP_VERSION_NOT_SUPPORTED.code()).isEqualTo(505);
    }

    @Test
    void testFromCode() {
        assertThat(RtspStatus.fromCode(200)).isEqualTo(RtspStatus.OK);
        assertThat(RtspStatus.fromCode(404)).isEqualTo(RtspStatus.NOT_FOUND);
        assertThat(RtspStatus.fromCode(454)).isEqualTo(RtspStatus.SESSION_NOT_FOUND);
        assertThat(RtspStatus.fromCode(461)).isEqualTo(RtspStatus.UNSUPPORTED_TRANSPORT);
    }

    @Test
    void testFromCodeUnknownThrows() {
        assertThatThrownBy(() -> RtspStatus.fromCode(999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(RtspStatus.class)
    void testAllStatusesHaveCodeAndReason(RtspStatus status) {
        assertThat(status.code()).isGreaterThan(0);
        assertThat(status.reason()).isNotBlank();
    }

    @Test
    void testRtspSpecificStatuses() {
        assertThat(RtspStatus.PARAMETER_NOT_UNDERSTOOD.code()).isEqualTo(451);
        assertThat(RtspStatus.NOT_ENOUGH_BANDWIDTH.code()).isEqualTo(453);
        assertThat(RtspStatus.INVALID_RANGE.code()).isEqualTo(457);
        assertThat(RtspStatus.AGGREGATE_OPERATION_NOT_ALLOWED.code()).isEqualTo(459);
        assertThat(RtspStatus.OPTION_NOT_SUPPORTED.code()).isEqualTo(551);
    }

    @Test
    void testIsSuccessAndIsError() {
        assertThat(RtspStatus.CONTINUE.isSuccess()).isFalse();
        assertThat(RtspStatus.CONTINUE.isError()).isFalse();
        assertThat(RtspStatus.OK.isSuccess()).isTrue();
        assertThat(RtspStatus.OK.isError()).isFalse();
        assertThat(RtspStatus.MOVED_PERMANENTLY.isSuccess()).isFalse();
        assertThat(RtspStatus.MOVED_PERMANENTLY.isError()).isFalse();
        assertThat(RtspStatus.BAD_REQUEST.isSuccess()).isFalse();
        assertThat(RtspStatus.BAD_REQUEST.isError()).isTrue();
    }
}
