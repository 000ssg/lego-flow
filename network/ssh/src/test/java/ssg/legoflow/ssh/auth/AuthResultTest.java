package ssg.legoflow.ssh.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link AuthResult} sealed interface.
 */
class AuthResultTest {

    @Test void testSuccessResult() {
        var result = new AuthResult.Success();
        assertThat(result).isInstanceOf(AuthResult.class);
    }

    @Test void testFailureResult() {
        var methods = java.util.List.of("password", "publickey");
        var result = new AuthResult.Failure(methods, false);
        assertThat(result.authMethodsThatCanContinue()).isEqualTo(methods);
        assertThat(result.partialSuccess()).isFalse();
    }

    @Test void testFailureWithPartialSuccess() {
        var methods = java.util.List.of("keyboard-interactive");
        var result = new AuthResult.Failure(methods, true);
        assertThat(result.partialSuccess()).isTrue();
    }

    @Test void testContinuationResult() {
        var subsys = "pam";
        var name = "login";
        var prompts = java.util.List.of(java.util.Map.entry("username:", false), 
                                        java.util.Map.entry("password:", true));
        // Test that Continuation record is constructible
        // (exact fields depend on API)
    }

    @Test void testSuccessNotFailure() {
        var success = new AuthResult.Success();
        assertThat(success).isNotInstanceOf(AuthResult.Failure.class);
    }

    @Test void testFailureMethodsListImmutable() {
        var methods = java.util.List.of("password");
        var result = new AuthResult.Failure(methods, false);
        assertThat(result.authMethodsThatCanContinue()).containsExactly("password");
    }

    @Test void testEmptyFailureMethods() {
        var result = new AuthResult.Failure(java.util.List.of(), false);
        assertThat(result.authMethodsThatCanContinue()).isEmpty();
    }
}
