package ssg.legoflow.ssh.server;

import ssg.legoflow.ssh.auth.AuthContext;
import ssg.legoflow.ssh.hostkey.Ed25519;
import ssg.legoflow.ssh.hostkey.SshKeyPair;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SshServerTest {

    @Test
    void testDefaultConstructor() {
        SshServer server = new SshServer();
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void testConstructorWithConfig() {
        SshServerConfig config = SshServerConfig.builder().port(2222).build();
        SshServer server = new SshServer(config);
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void testNullConfigThrows() {
        assertThatThrownBy(() -> new SshServer(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNotRunningByDefault() {
        SshServer server = new SshServer();
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void testConnectionCountZeroByDefault() {
        SshServer server = new SshServer();
        assertThat(server.connectionCount()).isEqualTo(0);
    }

    @Test
    void testBindWithoutHostKeyThrows() {
        SshServer server = new SshServer();
        server.setAuthenticator(new AuthContext());
        assertThatThrownBy(server::bind)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Host key not set");
    }

    @Test
    void testBindWithoutAuthenticatorThrows() {
        SshServer server = new SshServer();
        server.setHostKey(SshKeyPair.generate(new Ed25519()));
        assertThatThrownBy(server::bind)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authenticator not set");
    }

    @Test
    void testSetHostKeyChaining() {
        SshServer server = new SshServer();
        SshServer result = server.setHostKey(SshKeyPair.generate(new Ed25519()));
        assertThat(result).isSameAs(server);
    }

    @Test
    void testSetAuthenticatorChaining() {
        SshServer server = new SshServer();
        SshServer result = server.setAuthenticator(new AuthContext());
        assertThat(result).isSameAs(server);
    }

    @Test
    void testSetShellFactoryChaining() {
        SshServer server = new SshServer();
        SshServer result = server.setShellFactory((in, out, err) -> {});
        assertThat(result).isSameAs(server);
    }

    @Test
    void testSetCommandFactoryChaining() {
        SshServer server = new SshServer();
        SshServer result = server.setCommandFactory((cmd, out, err) -> 0);
        assertThat(result).isSameAs(server);
    }

    @Test
    void testSetForwardingFilterChaining() {
        SshServer server = new SshServer();
        SshServer result = server.setForwardingFilter(ForwardingFilter.allowAll());
        assertThat(result).isSameAs(server);
    }

    @Test
    void testForwardingFilterAllowAll() {
        ForwardingFilter filter = ForwardingFilter.allowAll();
        assertThat(filter.allow("user", "host", 8080)).isTrue();
    }

    @Test
    void testForwardingFilterDenyAll() {
        ForwardingFilter filter = ForwardingFilter.denyAll();
        assertThat(filter.allow("user", "host", 8080)).isFalse();
    }

    @Test
    void testCloseWithoutBindDoesNotThrow() throws Exception {
        SshServer server = new SshServer();
        assertThatCode(server::close).doesNotThrowAnyException();
    }

    @Test
    void testNullHostKeyThrows() {
        SshServer server = new SshServer();
        assertThatThrownBy(() -> server.setHostKey(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullAuthenticatorThrows() {
        SshServer server = new SshServer();
        assertThatThrownBy(() -> server.setAuthenticator(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullForwardingFilterThrows() {
        SshServer server = new SshServer();
        assertThatThrownBy(() -> server.setForwardingFilter(null))
                .isInstanceOf(NullPointerException.class);
    }
}
