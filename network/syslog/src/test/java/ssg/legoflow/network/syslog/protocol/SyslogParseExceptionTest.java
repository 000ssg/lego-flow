package ssg.legoflow.network.syslog.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SyslogParseException}.
 */
class SyslogParseExceptionTest {

    @Test
    void testMessageOnly() {
        var ex = new SyslogParseException("test error");
        assertThat(ex.getMessage()).isEqualTo("test error");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void testMessageAndCause() {
        var cause = new RuntimeException("root cause");
        var ex = new SyslogParseException("test error", cause);
        assertThat(ex.getMessage()).isEqualTo("test error");
        assertThat(ex.getCause()).isEqualTo(cause);
    }
}
