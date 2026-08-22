package ssg.legoflow.media.rtsp.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RtspMethod}.
 */
class RtspMethodTest {

    @Test
    void testAllMethodsDefined() {
        assertThat(RtspMethod.values()).hasSize(10);
        assertThat(RtspMethod.values()).contains(
                RtspMethod.OPTIONS, RtspMethod.DESCRIBE, RtspMethod.SETUP,
                RtspMethod.PLAY, RtspMethod.PAUSE, RtspMethod.TEARDOWN,
                RtspMethod.GET_PARAMETER, RtspMethod.SET_PARAMETER,
                RtspMethod.ANNOUNCE, RtspMethod.RECORD
        );
    }

    @Test
    void testFromNameExact() {
        assertThat(RtspMethod.fromName("OPTIONS")).isEqualTo(RtspMethod.OPTIONS);
        assertThat(RtspMethod.fromName("DESCRIBE")).isEqualTo(RtspMethod.DESCRIBE);
        assertThat(RtspMethod.fromName("SETUP")).isEqualTo(RtspMethod.SETUP);
        assertThat(RtspMethod.fromName("PLAY")).isEqualTo(RtspMethod.PLAY);
        assertThat(RtspMethod.fromName("PAUSE")).isEqualTo(RtspMethod.PAUSE);
        assertThat(RtspMethod.fromName("TEARDOWN")).isEqualTo(RtspMethod.TEARDOWN);
    }

    @Test
    void testFromNameCaseInsensitive() {
        assertThat(RtspMethod.fromName("options")).isEqualTo(RtspMethod.OPTIONS);
        assertThat(RtspMethod.fromName("Play")).isEqualTo(RtspMethod.PLAY);
        assertThat(RtspMethod.fromName("get_parameter")).isEqualTo(RtspMethod.GET_PARAMETER);
    }

    @Test
    void testFromNameUnknownThrows() {
        assertThatThrownBy(() -> RtspMethod.fromName("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void testMethodNames() {
        assertThat(RtspMethod.OPTIONS.name()).isEqualTo("OPTIONS");
        assertThat(RtspMethod.GET_PARAMETER.name()).isEqualTo("GET_PARAMETER");
        assertThat(RtspMethod.SET_PARAMETER.name()).isEqualTo("SET_PARAMETER");
    }
}
