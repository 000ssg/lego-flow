package ssg.legoflow.network.ldap.client;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LdapClientExceptionTest {

    @Test
    void testBasicConstructor() {
        var ex = new LdapClientException("Connection failed");
        assertThat(ex.getMessage()).isEqualTo("Connection failed");
    }

    @Test
    void testCauseConstructor() {
        var cause = new RuntimeException("IO error");
        var ex = new LdapClientException("Failed", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
