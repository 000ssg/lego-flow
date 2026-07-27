package ssg.legoflow.ssh.auth;

import java.util.List;

/**
 * Result of an SSH authentication attempt.
 *
 * @since 1.0.0
 */
public sealed interface AuthResult {

    /**
     * Authentication succeeded.
     *
     * @since 1.0.0
     */
    record Success() implements AuthResult {}

    /**
     * Authentication failed.
     *
     * @param authMethodsThatCanContinue methods the user can try next
     * @param partialSuccess             whether partial success occurred
     * @since 1.0.0
     */
    record Failure(List<String> authMethodsThatCanContinue,
                   boolean partialSuccess) implements AuthResult {}

    /**
     * Authentication needs continuation (e.g., keyboard-interactive).
     *
     * @param data continuation data
     * @since 1.0.0
     */
    record Continuation(byte[] data) implements AuthResult {}
}
