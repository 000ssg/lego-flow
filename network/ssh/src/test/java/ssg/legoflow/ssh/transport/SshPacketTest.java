package ssg.legoflow.ssh.transport;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SshPacket} sealed hierarchy.
 */
class SshPacketTest {

    @Test
    void testDisconnectMessageType() {
        var pkt = new SshPacket.Disconnect(11, "bye", "en");
        assertThat(pkt.messageType()).isEqualTo((byte) 1);
        assertThat(pkt.reasonCode()).isEqualTo(11);
        assertThat(pkt.description()).isEqualTo("bye");
    }

    @Test
    void testIgnoreMessageType() {
        var pkt = new SshPacket.Ignore(new byte[]{1, 2, 3});
        assertThat(pkt.messageType()).isEqualTo((byte) 2);
    }

    @Test
    void testUnimplementedMessageType() {
        var pkt = new SshPacket.Unimplemented(42);
        assertThat(pkt.messageType()).isEqualTo((byte) 3);
        assertThat(pkt.sequenceNumber()).isEqualTo(42);
    }

    @Test
    void testDebugMessageType() {
        var pkt = new SshPacket.Debug(true, "test", "en");
        assertThat(pkt.messageType()).isEqualTo((byte) 4);
        assertThat(pkt.alwaysDisplay()).isTrue();
    }

    @Test
    void testServiceRequestMessageType() {
        var pkt = new SshPacket.ServiceRequest("ssh-userauth");
        assertThat(pkt.messageType()).isEqualTo((byte) 5);
        assertThat(pkt.serviceName()).isEqualTo("ssh-userauth");
    }

    @Test
    void testServiceAcceptMessageType() {
        var pkt = new SshPacket.ServiceAccept("ssh-connection");
        assertThat(pkt.messageType()).isEqualTo((byte) 6);
    }

    @Test
    void testNewKeysMessageType() {
        var pkt = new SshPacket.NewKeys();
        assertThat(pkt.messageType()).isEqualTo((byte) 21);
    }

    @Test
    void testKexDhInitMessageType() {
        var pkt = new SshPacket.KexDhInit(new byte[]{1});
        assertThat(pkt.messageType()).isEqualTo((byte) 30);
    }

    @Test
    void testKexDhReplyMessageType() {
        var pkt = new SshPacket.KexDhReply(new byte[]{1}, new byte[]{2}, new byte[]{3});
        assertThat(pkt.messageType()).isEqualTo((byte) 31);
    }

    @Test
    void testUserAuthRequestMessageType() {
        var pkt = new SshPacket.UserAuthRequest("user", "ssh-connection", "password", new byte[0]);
        assertThat(pkt.messageType()).isEqualTo((byte) 50);
    }

    @Test
    void testUserAuthFailureMessageType() {
        var pkt = new SshPacket.UserAuthFailure(List.of("password", "publickey"), false);
        assertThat(pkt.messageType()).isEqualTo((byte) 51);
        assertThat(pkt.authMethodsThatCanContinue()).containsExactly("password", "publickey");
    }

    @Test
    void testUserAuthSuccessMessageType() {
        var pkt = new SshPacket.UserAuthSuccess();
        assertThat(pkt.messageType()).isEqualTo((byte) 52);
    }

    @Test
    void testChannelOpenMessageType() {
        var pkt = new SshPacket.ChannelOpen("session", 0, 2097152, 32768, new byte[0]);
        assertThat(pkt.messageType()).isEqualTo((byte) 90);
        assertThat(pkt.channelType()).isEqualTo("session");
    }

    @Test
    void testChannelDataMessageType() {
        var pkt = new SshPacket.ChannelData(0, "hello".getBytes());
        assertThat(pkt.messageType()).isEqualTo((byte) 94);
    }

    @Test
    void testChannelCloseMessageType() {
        var pkt = new SshPacket.ChannelClose(0);
        assertThat(pkt.messageType()).isEqualTo((byte) 97);
    }

    @Test
    void testChannelRequestMessageType() {
        var pkt = new SshPacket.ChannelRequest(0, "exec", true, new byte[0]);
        assertThat(pkt.messageType()).isEqualTo((byte) 98);
        assertThat(pkt.requestType()).isEqualTo("exec");
    }

    @Test
    void testGlobalRequestMessageType() {
        var pkt = new SshPacket.GlobalRequest("tcpip-forward", true, new byte[0]);
        assertThat(pkt.messageType()).isEqualTo((byte) 80);
    }
}
