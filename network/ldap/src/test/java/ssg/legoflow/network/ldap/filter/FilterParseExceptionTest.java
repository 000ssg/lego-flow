package ssg.legoflow.network.ldap.filter;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class FilterParseExceptionTest {

    @Test
    void testMessageConstructor() {
        var ex = new FilterParseException("invalid filter");
        assertThat(ex.getMessage()).isEqualTo("invalid filter");
    }

    @Test
    void testMessageAndCauseConstructor() {
        var cause = new RuntimeException("root");
        var ex = new FilterParseException("parse error", cause);
        assertThat(ex.getMessage()).isEqualTo("parse error");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void testExtendsRuntimeException() {
        assertThatThrownBy(() -> { throw new FilterParseException("test"); })
                .isInstanceOf(RuntimeException.class);
    }
}
