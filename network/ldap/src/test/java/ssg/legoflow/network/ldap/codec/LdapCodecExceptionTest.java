package ssg.legoflow.network.ldap.codec;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class LdapCodecExceptionTest {

    @Test
    void testMessageConstructor() {
        var ex = new LdapCodecException("decode failed");
        assertThat(ex.getMessage()).isEqualTo("decode failed");
    }

    @Test
    void testMessageAndCauseConstructor() {
        var cause = new RuntimeException("root");
        var ex = new LdapCodecException("failed", cause);
        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void testExtendsRuntimeException() {
        assertThatThrownBy(() -> { throw new LdapCodecException("test"); })
                .isInstanceOf(RuntimeException.class);
    }
}
