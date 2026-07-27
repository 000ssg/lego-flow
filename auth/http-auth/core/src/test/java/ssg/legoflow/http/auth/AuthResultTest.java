package ssg.legoflow.http.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AuthResultTest {

    @Test
    void testSuccessResult() {
        var principal = AuthPrincipal.of("alice");
        var result = AuthResult.success(principal);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
        assertThat(((AuthResult.Success) result).principal().getName()).isEqualTo("alice");
    }

    @Test
    void testFailureResult() {
        var result = AuthResult.failure("bad credentials");
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
        assertThat(((AuthResult.Failure) result).reason()).isEqualTo("bad credentials");
    }

    @Test
    void testChallengeResult() {
        var result = AuthResult.challenge("Basic");
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
        assertThat(((AuthResult.Challenge) result).schemeName()).isEqualTo("Basic");
    }

    @Test
    void testSuccessRequiresNonNullPrincipal() {
        assertThatThrownBy(() -> new AuthResult.Success(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testFailureRequiresNonNullReason() {
        assertThatThrownBy(() -> new AuthResult.Failure(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testChallengeRequiresNonNullScheme() {
        assertThatThrownBy(() -> new AuthResult.Challenge(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testPatternMatchingOnResult() {
        AuthResult result = AuthResult.success(AuthPrincipal.of("bob"));
        String outcome = switch (result) {
            case AuthResult.Success s -> "authenticated: " + s.principal().getName();
            case AuthResult.Failure f -> "failed: " + f.reason();
            case AuthResult.Challenge c -> "challenge: " + c.schemeName();
        };
        assertThat(outcome).isEqualTo("authenticated: bob");
    }
}
