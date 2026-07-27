package ssg.legoflow.network.syslog.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Severity}.
 */
class SeverityTest {

    @Test
    void testAllSeverityCodes() {
        assertThat(Severity.EMERGENCY.code()).isEqualTo(0);
        assertThat(Severity.ALERT.code()).isEqualTo(1);
        assertThat(Severity.CRITICAL.code()).isEqualTo(2);
        assertThat(Severity.ERROR.code()).isEqualTo(3);
        assertThat(Severity.WARNING.code()).isEqualTo(4);
        assertThat(Severity.NOTICE.code()).isEqualTo(5);
        assertThat(Severity.INFO.code()).isEqualTo(6);
        assertThat(Severity.DEBUG.code()).isEqualTo(7);
    }

    @ParameterizedTest
    @EnumSource(Severity.class)
    void testRoundTripByCode(Severity severity) {
        assertThat(Severity.of(severity.code())).isEqualTo(severity);
    }

    @Test
    void testTotalCount() {
        assertThat(Severity.values()).hasSize(8);
    }

    @Test
    void testInvalidCode() {
        assertThatThrownBy(() -> Severity.of(8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Severity.of(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
