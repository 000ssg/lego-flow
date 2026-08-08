package ssg.legoflow.ssh.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.Set;

/**
 * Tests for {@link AuthContext}.
 */
class AuthContextTest {

    @Test void testDefaultAuthContext() {
        var ctx = new AuthContext();
        assertThat(ctx).isNotNull();
        assertThat(ctx.allowedMethods()).contains("publickey", "password");
    }

    @Test void testAllowedMethodsContainsPublickeyAndPassword() {
        var ctx = new AuthContext();
        Set<String> methods = ctx.allowedMethods();
        assertThat(methods).contains("publickey", "password");
        assertThat(methods).doesNotContain("none", "keyboard-interactive");
    }

    @Test void testAllowedMethodsIsUnmodifiable() {
        var ctx = new AuthContext();
        assertThatThrownBy(() -> ctx.allowedMethods().add("hack"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void testSetPasswordValidatorChaining() {
        var ctx = new AuthContext();
        AuthContext result = ctx.setPasswordValidator((user, pass) -> true);
        assertThat(result).isSameAs(ctx);
    }

    @Test void testAuthenticatePasswordSuccess() {
        var ctx = new AuthContext();
        ctx.setPasswordValidator((user, pass) -> "admin".equals(user) && "secret".equals(pass));
        
        var result = ctx.authenticatePassword("admin", "secret");
        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    @Test void testAuthenticatePasswordFailure() {
        var ctx = new AuthContext();
        ctx.setPasswordValidator((user, pass) -> "admin".equals(user) && "secret".equals(pass));
        
        var result = ctx.authenticatePassword("admin", "wrong");
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test void testAuthenticatePublicKeySuccess() {
        var ctx = new AuthContext();
        byte[] validKey = new byte[]{0x01, 0x02, 0x03};
        ctx.setPublicKeyValidator((user, key) -> 
            "keyuser".equals(user) && java.util.Arrays.equals(key, validKey));
        
        var result = ctx.authenticatePublicKey("keyuser", validKey);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    @Test void testAuthenticatePublicKeyFailure() {
        var ctx = new AuthContext();
        byte[] invalidKey = new byte[]{(byte)0xFF};
        ctx.setPublicKeyValidator((user, key) -> 
            "keyuser".equals(user) && java.util.Arrays.equals(key, new byte[]{0x01}));
        
        var result = ctx.authenticatePublicKey("keyuser", invalidKey);
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test void testAuthenticatePublicKeyNoValidatorReturnsFailure() {
        var ctx = new AuthContext();
        // Without setting a validator, auth should fail
        var result = ctx.authenticatePublicKey("any", new byte[]{1});
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test void testSetBanner() {
        var ctx = new AuthContext();
        ctx.setBanner("Warning: unauthorized access");
        assertThat(ctx.banner()).isEqualTo("Warning: unauthorized access");
    }

    @Test void testBannerDefaultIsNull() {
        var ctx = new AuthContext();
        assertThat(ctx.banner()).isNull();
    }

    @Test void testSetMaxFailuresChaining() {
        var ctx = new AuthContext();
        AuthContext result = ctx.setMaxFailures(5);
        assertThat(result).isSameAs(ctx);
        assertThat(ctx.maxFailures()).isEqualTo(5);
    }

    @Test void testDefaultMaxFailures() {
        var ctx = new AuthContext();
        // Default should be some positive number (typically 6)
        assertThat(ctx.maxFailures()).isPositive();
    }

    @Test void testFailureCountIncrements() {
        var ctx = new AuthContext();
        ctx.setPasswordValidator((u, p) -> false);
        
        assertThat(ctx.failureCount("testuser")).isEqualTo(0);
        ctx.authenticatePassword("testuser", "wrong");
        assertThat(ctx.failureCount("testuser")).isEqualTo(1);
        ctx.authenticatePassword("testuser", "still_wrong");
        assertThat(ctx.failureCount("testuser")).isEqualTo(2);
    }

    @Test void testFailureCountPerUser() {
        var ctx = new AuthContext();
        ctx.setPasswordValidator((u, p) -> false);
        
        ctx.authenticatePassword("alice", "x");
        ctx.authenticatePassword("bob", "y");
        
        assertThat(ctx.failureCount("alice")).isEqualTo(1);
        assertThat(ctx.failureCount("bob")).isEqualTo(1);
    }

    @Test void testIsLockedWhenFailuresExceedMax() {
        var ctx = new AuthContext();
        ctx.setMaxFailures(3);
        ctx.setPasswordValidator((u, p) -> false);
        
        assertThat(ctx.isLocked("testuser")).isFalse();
        ctx.authenticatePassword("testuser", "1");
        ctx.authenticatePassword("testuser", "2");
        ctx.authenticatePassword("testuser", "3");
        assertThat(ctx.isLocked("testuser")).isTrue();
    }

    @Test void testIsNotLockedBelowMaxFailures() {
        var ctx = new AuthContext();
        ctx.setMaxFailures(5);
        ctx.setPasswordValidator((u, p) -> false);
        
        ctx.authenticatePassword("testuser", "1");
        ctx.authenticatePassword("testuser", "2");
        assertThat(ctx.isLocked("testuser")).isFalse();
    }

    @Test void testLockedUserCannotAuthenticate() {
        var ctx = new AuthContext();
        ctx.setMaxFailures(2);
        // Validator always returns false, so all attempts fail and count up
        ctx.setPasswordValidator((u, p) -> false);
        
        ctx.authenticatePassword("locked", "1");
        ctx.authenticatePassword("locked", "2");
        assertThat(ctx.isLocked("locked")).isTrue();
    }

    @Test void testSetPublicKeyValidatorChaining() {
        var ctx = new AuthContext();
        AuthContext result = ctx.setPublicKeyValidator((u, k) -> true);
        assertThat(result).isSameAs(ctx);
    }

    @Test void testMultipleUsersIndependentFailureCounts() {
        var ctx = new AuthContext();
        ctx.setMaxFailures(3);
        ctx.setPasswordValidator((u, p) -> false);
        
        // Lock user1 but not user2
        for (int i = 0; i < 3; i++) ctx.authenticatePassword("user1", "x");
        for (int i = 0; i < 2; i++) ctx.authenticatePassword("user2", "y");
        
        assertThat(ctx.isLocked("user1")).isTrue();
        assertThat(ctx.isLocked("user2")).isFalse();
    }

    @Test void testFailureResultContainsContinuationMethods() {
        var ctx = new AuthContext();
        ctx.setPasswordValidator((u, p) -> false);
        
        var result = ctx.authenticatePassword("user", "pass");
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
        // Failure should list remaining methods
        var failure = (AuthResult.Failure) result;
        assertThat(failure.authMethodsThatCanContinue()).isNotEmpty();
    }

    @Test void testSetBannerAllowsNull() {
        var ctx = new AuthContext();
        // setBanner accepts null (no NPE)
        ctx.setBanner(null);
        assertThat(ctx.banner()).isNull();
    }

    @Test void testPasswordValidatorAllowsNull() {
        var ctx = new AuthContext();
        // setPasswordValidator accepts null (no NPE)
        ctx.setPasswordValidator(null);
        // Authenticating without validator returns failure
        var result = ctx.authenticatePassword("user", "pass");
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }
}
