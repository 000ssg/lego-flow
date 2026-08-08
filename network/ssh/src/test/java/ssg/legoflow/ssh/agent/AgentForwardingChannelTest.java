package ssg.legoflow.ssh.agent;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AgentForwardingChannelTest {

    @Test void testChannelTypeConstant() {
        assertThat(AgentForwardingChannel.CHANNEL_TYPE).isEqualTo("auth-agent@openssh.com");
    }

    @Test void testSshAgentMessageConstants() {
        assertThat(SshAgentMessage.SSH_AGENT_FAILURE).isEqualTo(5);
        assertThat(SshAgentMessage.SSH_AGENT_SUCCESS).isEqualTo(6);
        assertThat(SshAgentMessage.SSH_AGENTC_REQUEST_IDENTITIES).isEqualTo(11);
        assertThat(SshAgentMessage.SSH_AGENT_IDENTITIES_ANSWER).isEqualTo(12);
        assertThat(SshAgentMessage.SSH_AGENTC_SIGN_REQUEST).isEqualTo(13);
        assertThat(SshAgentMessage.SSH_AGENT_SIGN_RESPONSE).isEqualTo(14);
    }

    @Test void testSshAgentFailureMessage() {
        var msg = new SshAgentMessage.Failure();
        assertThat(msg.type()).isEqualTo(SshAgentMessage.SSH_AGENT_FAILURE);
    }

    @Test void testSshAgentSuccessMessage() {
        var msg = new SshAgentMessage.Success();
        assertThat(msg.type()).isEqualTo(SshAgentMessage.SSH_AGENT_SUCCESS);
    }

    @Test void testIdentitiesAnswerType() {
        var identity = new SshAgentMessage.IdentitiesAnswer.Identity(new byte[]{1, 2, 3}, "comment");
        var msg = new SshAgentMessage.IdentitiesAnswer(java.util.List.of(identity));
        assertThat(msg.type()).isEqualTo(SshAgentMessage.SSH_AGENT_IDENTITIES_ANSWER);
        assertThat(msg.identities()).hasSize(1);
    }

    @Test void testSignResponseType() {
        var msg = new SshAgentMessage.SignResponse(new byte[]{1, 2});
        assertThat(msg.type()).isEqualTo(SshAgentMessage.SSH_AGENT_SIGN_RESPONSE);
    }

    @Test void testAddIdentityType() {
        var msg = new SshAgentMessage.AddIdentity("ssh-ed25519", new byte[]{1}, new byte[]{2}, "comment");
        assertThat(msg.type()).isEqualTo(SshAgentMessage.SSH_AGENTC_ADD_IDENTITY);
    }

    @Test void testRemoveIdentityType() {
        var msg = new SshAgentMessage.RemoveIdentity(new byte[]{1});
        assertThat(msg.type()).isEqualTo(SshAgentMessage.SSH_AGENTC_REMOVE_IDENTITY);
    }

    @Test void testRemoveAllIdentitiesType() {
        var msg = new SshAgentMessage.RemoveAllIdentities();
        assertThat(msg.type()).isEqualTo(SshAgentMessage.SSH_AGENTC_REMOVE_ALL_IDENTITIES);
    }
}
