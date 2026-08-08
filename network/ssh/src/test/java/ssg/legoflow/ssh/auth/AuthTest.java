package ssg.legoflow.ssh.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthTest {

    @Test void testPasswordAuth() {
        var auth = new PasswordAuth("secret");
        assertThat(auth.methodName()).isEqualTo("password");
        byte[] encoded = auth.encodeRequest("user", "ssh-connection");
        assertThat(encoded).isNotEmpty();
    }

    @Test void testNoneAuth() {
        var auth = new NoneAuth();
        assertThat(auth.methodName()).isEqualTo("none");
    }

    @Test void testPublicKeyAuth() throws Exception {
        var hostKey = ssg.legoflow.ssh.hostkey.HostKeyFactory.create("ssh-ed25519");
        var keyPair = ssg.legoflow.ssh.hostkey.SshKeyPair.generate(hostKey);
        byte[] sessionHash = new byte[32];
        var auth = new PublicKeyAuth(keyPair, sessionHash);
        assertThat(auth.methodName()).isEqualTo("publickey");
    }

    @Test void testAuthBanner() {
        var banner = new AuthBanner("Welcome", "en");
        assertThat(banner.message()).isEqualTo("Welcome");
    }
}
