package ssg.legoflow.ssh.client;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SshClientTest {

    @Test
    void testDefaultConstructor() {
        SshClient client = new SshClient();
        assertThat(client.config()).isNotNull();
        assertThat(client.isAuthenticated()).isFalse();
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void testConstructorWithConfig() {
        SshClientConfig config = SshClientConfig.defaults();
        SshClient client = new SshClient(config);
        assertThat(client.config()).isSameAs(config);
    }

    @Test
    void testNullConfigThrows() {
        assertThatThrownBy(() -> new SshClient(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNotAuthenticatedByDefault() {
        SshClient client = new SshClient();
        assertThat(client.isAuthenticated()).isFalse();
    }

    @Test
    void testNotConnectedByDefault() {
        SshClient client = new SshClient();
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void testTransportNullBeforeConnect() {
        SshClient client = new SshClient();
        assertThat(client.transport()).isNull();
    }

    @Test
    void testConnectionNullBeforeConnect() {
        SshClient client = new SshClient();
        assertThat(client.connection()).isNull();
    }

    @Test
    void testOpenSessionWithoutAuthThrows() {
        SshClient client = new SshClient();
        assertThatThrownBy(client::openSession)
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Not authenticated");
    }

    @Test
    void testCreateLocalForwardWithoutAuthThrows() {
        SshClient client = new SshClient();
        assertThatThrownBy(() -> client.createLocalForward(8080, "localhost", 80))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Not authenticated");
    }

    @Test
    void testCreateRemoteForwardWithoutAuthThrows() {
        SshClient client = new SshClient();
        assertThatThrownBy(() -> client.createRemoteForward(9090, "localhost", 80))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Not authenticated");
    }

    @Test
    void testOpenSftpChannelWithoutAuthThrows() {
        SshClient client = new SshClient();
        assertThatThrownBy(client::openSftpChannel)
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Not authenticated");
    }

    @Test
    void testDisconnectWithoutConnectDoesNotThrow() throws Exception {
        SshClient client = new SshClient();
        assertThatCode(client::disconnect).doesNotThrowAnyException();
    }

    @Test
    void testCloseWithoutConnectDoesNotThrow() throws Exception {
        SshClient client = new SshClient();
        assertThatCode(client::close).doesNotThrowAnyException();
    }
}
