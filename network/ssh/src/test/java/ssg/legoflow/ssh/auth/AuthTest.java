package ssg.legoflow.ssh.auth;

import ssg.legoflow.ssh.hostkey.Ed25519;
import ssg.legoflow.ssh.hostkey.SshKeyPair;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AuthTest {

    @Test
    void testPasswordAuthMethodName() {
        assertThat(new PasswordAuth("pass").methodName()).isEqualTo("password");
    }

    @Test
    void testPasswordAuthNotInteractive() {
        assertThat(new PasswordAuth("pass").isInteractive()).isFalse();
    }

    @Test
    void testPasswordAuthEncodeRequest() {
        PasswordAuth auth = new PasswordAuth("secret");
        byte[] request = auth.encodeRequest("user", "ssh-connection");
        assertThat(request).isNotEmpty();
        assertThat(request[0]).isEqualTo((byte) 50);
    }

    @Test
    void testNoneAuthMethodName() {
        assertThat(new NoneAuth().methodName()).isEqualTo("none");
    }

    @Test
    void testNoneAuthEncodeRequest() {
        NoneAuth auth = new NoneAuth();
        byte[] request = auth.encodeRequest("user", "ssh-connection");
        assertThat(request).isNotEmpty();
        assertThat(request[0]).isEqualTo((byte) 50);
    }

    @Test
    void testPublicKeyAuthMethodName() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        PublicKeyAuth auth = new PublicKeyAuth(kp, new byte[32]);
        assertThat(auth.methodName()).isEqualTo("publickey");
    }

    @Test
    void testPublicKeyAuthNotInteractive() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        PublicKeyAuth auth = new PublicKeyAuth(kp, new byte[32]);
        assertThat(auth.isInteractive()).isFalse();
    }

    @Test
    void testPublicKeyAuthEncodeRequest() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        PublicKeyAuth auth = new PublicKeyAuth(kp, new byte[32]);
        byte[] request = auth.encodeRequest("user", "ssh-connection");
        assertThat(request).isNotEmpty();
    }

    @Test
    void testKeyboardInteractiveMethodName() {
        KeyboardInteractiveAuth auth = new KeyboardInteractiveAuth(prompts -> List.of("answer"));
        assertThat(auth.methodName()).isEqualTo("keyboard-interactive");
    }

    @Test
    void testKeyboardInteractiveIsInteractive() {
        KeyboardInteractiveAuth auth = new KeyboardInteractiveAuth(prompts -> List.of());
        assertThat(auth.isInteractive()).isTrue();
    }

    @Test
    void testKeyboardInteractiveEncodeResponses() {
        KeyboardInteractiveAuth auth = new KeyboardInteractiveAuth(
                prompts -> prompts.stream().map(p -> "answer").toList());
        byte[] response = auth.encodeResponses(List.of("Password: "));
        assertThat(response[0]).isEqualTo((byte) 61);
    }

    @Test
    void testHostBasedAuthMethodName() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        HostBasedAuth auth = new HostBasedAuth(kp, "client.local", "user", new byte[32]);
        assertThat(auth.methodName()).isEqualTo("hostbased");
    }

    @Test
    void testAuthResultSuccess() {
        AuthResult result = new AuthResult.Success();
        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    @Test
    void testAuthResultFailure() {
        AuthResult result = new AuthResult.Failure(List.of("password", "publickey"), false);
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
        AuthResult.Failure failure = (AuthResult.Failure) result;
        assertThat(failure.authMethodsThatCanContinue()).containsExactly("password", "publickey");
        assertThat(failure.partialSuccess()).isFalse();
    }

    @Test
    void testAuthResultContinuation() {
        AuthResult result = new AuthResult.Continuation(new byte[]{1, 2});
        assertThat(result).isInstanceOf(AuthResult.Continuation.class);
    }

    @Test
    void testAuthContextPasswordSuccess() {
        AuthContext ctx = new AuthContext()
                .setPasswordValidator((user, pass) -> "admin".equals(user) && "secret".equals(pass));
        AuthResult result = ctx.authenticatePassword("admin", "secret");
        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    @Test
    void testAuthContextPasswordFailure() {
        AuthContext ctx = new AuthContext()
                .setPasswordValidator((user, pass) -> false);
        AuthResult result = ctx.authenticatePassword("admin", "wrong");
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void testAuthContextPublicKeySuccess() {
        AuthContext ctx = new AuthContext()
                .setPublicKeyValidator((user, blob) -> true);
        AuthResult result = ctx.authenticatePublicKey("user", new byte[]{1});
        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    @Test
    void testAuthContextFailureCount() {
        AuthContext ctx = new AuthContext()
                .setPasswordValidator((user, pass) -> false);
        ctx.authenticatePassword("user", "wrong1");
        ctx.authenticatePassword("user", "wrong2");
        assertThat(ctx.failureCount("user")).isEqualTo(2);
    }

    @Test
    void testAuthContextMaxFailures() {
        AuthContext ctx = new AuthContext()
                .setPasswordValidator((user, pass) -> false)
                .setMaxFailures(2);
        ctx.authenticatePassword("user", "wrong1");
        ctx.authenticatePassword("user", "wrong2");
        assertThat(ctx.isLocked("user")).isTrue();
    }

    @Test
    void testAuthContextBanner() {
        AuthContext ctx = new AuthContext().setBanner("Welcome!");
        assertThat(ctx.banner()).isEqualTo("Welcome!");
    }

    @Test
    void testAuthBannerEncodeDecode() {
        AuthBanner banner = AuthBanner.of("Hello, User!");
        byte[] encoded = banner.encode();
        AuthBanner decoded = AuthBanner.decode(encoded);
        assertThat(decoded.message()).isEqualTo("Hello, User!");
        assertThat(decoded.language()).isEmpty();
    }

    @Test
    void testAuthBannerWithLanguage() {
        AuthBanner banner = new AuthBanner("Willkommen", "de");
        byte[] encoded = banner.encode();
        AuthBanner decoded = AuthBanner.decode(encoded);
        assertThat(decoded.language()).isEqualTo("de");
    }
}
