package ssg.legoflow.ssh.agent;

import ssg.legoflow.ssh.hostkey.Ed25519;
import ssg.legoflow.ssh.hostkey.SshKeyPair;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AgentForwardingChannelTest {

    private SshAgent agent;
    private SshKeyPair keyPair;
    private AgentForwardingChannel channel;

    @BeforeEach
    void setUp() {
        agent = new SshAgent();
        keyPair = SshKeyPair.generate(new Ed25519());
        agent.addIdentity(keyPair, "test-key");
        channel = new AgentForwardingChannel(0, null, agent);
    }

    @Test
    void testChannelType() {
        assertThat(channel.channelType()).isEqualTo("auth-agent@openssh.com");
    }

    @Test
    void testAgentAccessor() {
        assertThat(channel.agent()).isSameAs(agent);
    }

    @Test
    void testChannelTypeConstant() {
        assertThat(AgentForwardingChannel.CHANNEL_TYPE).isEqualTo("auth-agent@openssh.com");
    }

    @Test
    void testAgentForwardingChannelCreation() {
        AgentForwardingChannel ch = new AgentForwardingChannel(42, null, agent);
        assertThat(ch.localId()).isEqualTo(42);
        assertThat(ch.channelType()).isEqualTo("auth-agent@openssh.com");
        assertThat(ch.agent()).isSameAs(agent);
    }

    @Test
    void testAgentProcessesRequestIdentities() {
        // Verify the agent correctly processes a RequestIdentities via processMessage
        SshAgentMessage response = agent.processMessage(new SshAgentMessage.RequestIdentities());
        assertThat(response).isInstanceOf(SshAgentMessage.IdentitiesAnswer.class);
        var answer = (SshAgentMessage.IdentitiesAnswer) response;
        assertThat(answer.identities()).hasSize(1);
        assertThat(answer.identities().getFirst().comment()).isEqualTo("test-key");
    }

    @Test
    void testAgentProcessesSignRequest() {
        byte[] data = "data to sign".getBytes();
        SshAgentMessage response = agent.processMessage(
                new SshAgentMessage.SignRequest(keyPair.publicKeyBlob(), data, 0));
        assertThat(response).isInstanceOf(SshAgentMessage.SignResponse.class);
        var signResponse = (SshAgentMessage.SignResponse) response;
        assertThat(signResponse.signature()).isNotEmpty();
    }

    @Test
    void testAgentProcessesSignRequestUnknownKey() {
        SshAgentMessage response = agent.processMessage(
                new SshAgentMessage.SignRequest(new byte[]{1, 2, 3}, "data".getBytes(), 0));
        assertThat(response).isInstanceOf(SshAgentMessage.Failure.class);
    }

    @Test
    void testEncodeDecodeRoundtripForAgentChannel() {
        // Verify the full encode-decode cycle that the channel would perform
        byte[] request = SshAgentCodec.encode(new SshAgentMessage.RequestIdentities());
        SshAgentMessage decoded = SshAgentCodec.decode(request);
        SshAgentMessage response = agent.processMessage(decoded);
        assertThat(response).isInstanceOf(SshAgentMessage.IdentitiesAnswer.class);

        byte[] encodedResponse = SshAgentCodec.encode(response);
        SshAgentMessage decodedResponse = SshAgentCodec.decode(encodedResponse);
        assertThat(decodedResponse).isInstanceOf(SshAgentMessage.IdentitiesAnswer.class);
        var answer = (SshAgentMessage.IdentitiesAnswer) decodedResponse;
        assertThat(answer.identities()).hasSize(1);
    }
}
