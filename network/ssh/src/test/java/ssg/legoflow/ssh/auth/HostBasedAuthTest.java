package ssg.legoflow.ssh.auth;

import ssg.legoflow.ssh.hostkey.Ed25519;
import ssg.legoflow.ssh.hostkey.SshKeyPair;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostBasedAuthTest {

    @Test void testMethodMetadata() {
        HostBasedAuth auth = createHostBasedAuth();
        assertThat(auth.methodName()).isEqualTo("hostbased");
        assertThat(auth.isInteractive()).isFalse();
    }

    @Test void testConstructorRejectsNullHostKey() {
        assertThatThrownBy(() -> new HostBasedAuth(
                null, "client.example.com", "user", new byte[]{1}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("hostKey");
    }

    @Test void testConstructorRejectsNullHostname() {
        SshKeyPair key = SshKeyPair.generate(new Ed25519());
        assertThatThrownBy(() -> new HostBasedAuth(
                key, null, "user", new byte[]{1}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clientHostname");
    }

    @Test void testConstructorRejectsNullUsername() {
        SshKeyPair key = SshKeyPair.generate(new Ed25519());
        assertThatThrownBy(() -> new HostBasedAuth(
                key, "host", null, new byte[]{1}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clientUsername");
    }

    @Test void testConstructorRejectsNullSessionId() {
        SshKeyPair key = SshKeyPair.generate(new Ed25519());
        assertThatThrownBy(() -> new HostBasedAuth(
                key, "host", "user", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sessionId");
    }

    @Test void testEncodeRequestProducesNonEmptyResult() {
        HostBasedAuth auth = createHostBasedAuth();
        byte[] request = auth.encodeRequest("remoteUser", "ssh-connection");
        
        assertThat(request).isNotEmpty();
        // First byte should be SSH_MSG_USERAUTH_REQUEST (50)
        assertThat(request[0]).isEqualTo((byte) 50);
    }

    @Test void testEncodeRequestWithDataIntegrity() {
        HostBasedAuth auth = createHostBasedAuth();
        
        // Call twice - each time produces a signature so output differs,
        // but basic structure should be valid
        byte[] req1 = auth.encodeRequest("user", "service");
        byte[] req2 = auth.encodeRequest("user", "service");
        
        assertThat(req1).isNotEmpty();
        assertThat(req2).isNotEmpty();
        assertThat(req1[0]).isEqualTo((byte) 50);
        assertThat(req2[0]).isEqualTo((byte) 50);
    }

    @Test void testEncodeRequestWithFqdnHostname() {
        SshKeyPair key = SshKeyPair.generate(new Ed25519());
        HostBasedAuth auth = new HostBasedAuth(
                key, "client.example.com", "deployer", 
                new byte[]{1, 2, 3, 4, 5});
        
        byte[] request = auth.encodeRequest("admin", "ssh");
        assertThat(request).isNotEmpty();
    }

    @Test void testEncodeRequestWithShortSessionId() {
        SshKeyPair key = SshKeyPair.generate(new Ed25519());
        HostBasedAuth auth = new HostBasedAuth(
                key, "localhost", "user", 
                new byte[]{0x42}); // 1-byte session ID
        
        byte[] request = auth.encodeRequest("user", "ssh-connection");
        assertThat(request).isNotEmpty();
    }

    private HostBasedAuth createHostBasedAuth() {
        return new HostBasedAuth(
                SshKeyPair.generate(new Ed25519()),
                "client.example.com",
                "remoteUser",
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8}
        );
    }
}
