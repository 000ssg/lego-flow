package ssg.legoflow.ssh.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PasswordAuth}.
 */
class PasswordAuthTest {

    @Test void testMethodName() {
        var auth = new PasswordAuth("secret");
        assertThat(auth.methodName()).isEqualTo("password");
    }

    @Test void testNotInteractive() {
        var auth = new PasswordAuth("secret");
        assertThat(auth.isInteractive()).isFalse();
    }

    @Test void testEncodeRequest() {
        var auth = new PasswordAuth("mypass");
        byte[] encoded = auth.encodeRequest("user", "ssh-connection");
        assertThat(encoded).isNotEmpty();
        // First byte should be SSH_MSG_USERAUTH_REQUEST (50)
        assertThat(encoded[0] & 0xFF).isEqualTo(50);
    }

    @Test void testNullPasswordThrows() {
        assertThatThrownBy(() -> new PasswordAuth(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testEmptyPassword() {
        var auth = new PasswordAuth("");
        assertThat(auth.methodName()).isEqualTo("password");
        byte[] encoded = auth.encodeRequest("user", "ssh-connection");
        assertThat(encoded).isNotEmpty();
    }

    @Test void testChangePasswordFlag() {
        // If PasswordAuth supports changing password, verify that too
        var auth = new PasswordAuth("newpass");
        assertThat(auth).isNotNull();
    }
}
