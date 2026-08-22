package ssg.legoflow.ssh.agent;

import ssg.legoflow.ssh.hostkey.Ed25519;
import ssg.legoflow.ssh.hostkey.EcdsaSha2Nistp256;
import ssg.legoflow.ssh.hostkey.SshKeyPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SshAgentTest {

    private SshAgent agent;
    private SshKeyPair ed25519Key;

    @BeforeEach
    void setUp() {
        agent = new SshAgent();
        ed25519Key = SshKeyPair.generate(new Ed25519());
    }

    @Test
    void testAddIdentity() {
        agent.addIdentity(ed25519Key, "test@host");
        assertThat(agent.size()).isEqualTo(1);
        assertThat(agent.identities()).hasSize(1);
        assertThat(agent.identities().getFirst().comment()).isEqualTo("test@host");
    }

    @Test
    void testAddMultipleIdentities() {
        agent.addIdentity(ed25519Key, "key1");
        SshKeyPair ecdsaKey = SshKeyPair.generate(new EcdsaSha2Nistp256());
        agent.addIdentity(ecdsaKey, "key2");
        assertThat(agent.size()).isEqualTo(2);
    }

    @Test
    void testRemoveIdentity() {
        agent.addIdentity(ed25519Key, "test@host");
        assertThat(agent.removeIdentity(ed25519Key.publicKeyBlob())).isTrue();
        assertThat(agent.size()).isEqualTo(0);
    }

    @Test
    void testRemoveIdentityNotFound() {
        assertThat(agent.removeIdentity(new byte[]{1, 2, 3})).isFalse();
    }

    @Test
    void testRemoveAllIdentities() {
        agent.addIdentity(ed25519Key, "key1");
        agent.addIdentity(SshKeyPair.generate(new EcdsaSha2Nistp256()), "key2");
        assertThat(agent.size()).isEqualTo(2);
        agent.removeAllIdentities();
        assertThat(agent.size()).isEqualTo(0);
    }

    @Test
    void testListIdentities() {
        agent.addIdentity(ed25519Key, "my-key");
        var identities = agent.identities();
        assertThat(identities).hasSize(1);
        assertThat(identities.getFirst().publicKeyBlob()).isEqualTo(ed25519Key.publicKeyBlob());
        assertThat(identities.getFirst().comment()).isEqualTo("my-key");
    }

    @Test
    void testSignWithStoredKey() {
        agent.addIdentity(ed25519Key, "test");
        byte[] data = "data to sign".getBytes();
        byte[] signature = agent.sign(ed25519Key.publicKeyBlob(), data, 0);
        assertThat(signature).isNotNull();
        assertThat(signature).isNotEmpty();
        // Verify the signature is valid
        assertThat(ed25519Key.hostKeyAlgorithm().verify(
                ed25519Key.publicKeyBlob(), data, signature)).isTrue();
    }

    @Test
    void testSignWithUnknownKeyFails() {
        byte[] signature = agent.sign(new byte[]{1, 2, 3}, "data".getBytes(), 0);
        assertThat(signature).isNull();
    }

    @Test
    void testProcessRequestIdentities() {
        agent.addIdentity(ed25519Key, "test");
        SshAgentMessage response = agent.processMessage(new SshAgentMessage.RequestIdentities());
        assertThat(response).isInstanceOf(SshAgentMessage.IdentitiesAnswer.class);
        var answer = (SshAgentMessage.IdentitiesAnswer) response;
        assertThat(answer.identities()).hasSize(1);
    }

    @Test
    void testProcessSignRequest() {
        agent.addIdentity(ed25519Key, "test");
        byte[] data = "sign me".getBytes();
        SshAgentMessage response = agent.processMessage(
                new SshAgentMessage.SignRequest(ed25519Key.publicKeyBlob(), data, 0));
        assertThat(response).isInstanceOf(SshAgentMessage.SignResponse.class);
    }

    @Test
    void testProcessSignRequestUnknownKey() {
        SshAgentMessage response = agent.processMessage(
                new SshAgentMessage.SignRequest(new byte[]{1}, "data".getBytes(), 0));
        assertThat(response).isInstanceOf(SshAgentMessage.Failure.class);
    }

    @Test
    void testProcessRemoveIdentity() {
        agent.addIdentity(ed25519Key, "test");
        SshAgentMessage response = agent.processMessage(
                new SshAgentMessage.RemoveIdentity(ed25519Key.publicKeyBlob()));
        assertThat(response).isInstanceOf(SshAgentMessage.Success.class);
        assertThat(agent.size()).isEqualTo(0);
    }

    @Test
    void testProcessRemoveAllIdentities() {
        agent.addIdentity(ed25519Key, "test");
        SshAgentMessage response = agent.processMessage(new SshAgentMessage.RemoveAllIdentities());
        assertThat(response).isInstanceOf(SshAgentMessage.Success.class);
        assertThat(agent.size()).isEqualTo(0);
    }
}
