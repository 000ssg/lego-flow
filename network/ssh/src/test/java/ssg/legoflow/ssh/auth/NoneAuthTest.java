package ssg.legoflow.ssh.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link NoneAuth}.
 */
class NoneAuthTest {

    @Test void testMethodName() {
        var auth = new NoneAuth();
        assertThat(auth.methodName()).isEqualTo("none");
    }

    @Test void testNotInteractive() {
        var auth = new NoneAuth();
        assertThat(auth.isInteractive()).isFalse();
    }

    @Test void testEncodeRequest() {
        var auth = new NoneAuth();
        byte[] encoded = auth.encodeRequest("user", "ssh-connection");
        assertThat(encoded).isNotEmpty();
        // First byte should be SSH_MSG_USERAUTH_REQUEST (50)
        assertThat(encoded[0] & 0xFF).isEqualTo(50);
    }

    @Test void testEncodeWithEmptyUsername() {
        var auth = new NoneAuth();
        byte[] encoded = auth.encodeRequest("", "ssh-connection");
        assertThat(encoded).isNotEmpty();
    }

    @Test void testMultipleRequestsConsistent() {
        var auth = new NoneAuth();
        byte[] e1 = auth.encodeRequest("u", "s");
        byte[] e2 = auth.encodeRequest("u", "s");
        assertThat(e1).isEqualTo(e2);
    }
}
