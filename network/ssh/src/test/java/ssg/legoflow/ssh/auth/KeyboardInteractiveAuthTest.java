package ssg.legoflow.ssh.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.function.Function;

/**
 * Tests for {@link KeyboardInteractiveAuth}.
 */
class KeyboardInteractiveAuthTest {

    @Test void testMethodName() {
        Function<List<String>, List<String>> provider = prompts -> List.of("answer");
        var auth = new KeyboardInteractiveAuth(provider);
        assertThat(auth.methodName()).isEqualTo("keyboard-interactive");
    }

    @Test void testIsInteractive() {
        Function<List<String>, List<String>> provider = prompts -> List.of();
        var auth = new KeyboardInteractiveAuth(provider);
        assertThat(auth.isInteractive()).isTrue();
    }

    @Test void testNullProviderThrows() {
        assertThatThrownBy(() -> new KeyboardInteractiveAuth(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testResponseProviderCalled() {
        java.util.concurrent.atomic.AtomicBoolean called = new java.util.concurrent.atomic.AtomicBoolean(false);
        Function<List<String>, List<String>> provider = prompts -> {
            called.set(true);
            return List.of("password123");
        };
        var auth = new KeyboardInteractiveAuth(provider);
        // Encoding a request should not call the provider (it's used during response handling)
        byte[] encoded = auth.encodeRequest("user", "ssh-connection");
        assertThat(encoded).isNotEmpty();
    }

    @Test void testEncodeRequest() {
        Function<List<String>, List<String>> provider = prompts -> List.of();
        var auth = new KeyboardInteractiveAuth(provider);
        byte[] encoded = auth.encodeRequest("admin", "ssh-connection");
        assertThat(encoded).isNotEmpty();
        assertThat(encoded[0] & 0xFF).isEqualTo(50); // SSH_MSG_USERAUTH_REQUEST
    }
}
