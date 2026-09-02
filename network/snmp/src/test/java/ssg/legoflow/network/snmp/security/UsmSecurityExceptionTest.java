package ssg.legoflow.network.snmp.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link UsmSecurityException}.
 *
 * @since 0.1.0
 */
class UsmSecurityExceptionTest {

    @Test
    void testConstructorWithMessage() {
        var ex = new UsmSecurityException("security failed");
        assertThat(ex.getMessage()).isEqualTo("security failed");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void testConstructorWithMessageAndCause() {
        var cause = new RuntimeException("underlying");
        var ex = new UsmSecurityException("security failed", cause);
        assertThat(ex.getMessage()).isEqualTo("security failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void testExtendsRuntimeException() {
        assertThatThrownBy(() -> { throw new UsmSecurityException("fail"); })
                .isInstanceOf(RuntimeException.class);
    }
}
