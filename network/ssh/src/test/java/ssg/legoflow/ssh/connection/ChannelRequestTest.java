package ssg.legoflow.ssh.connection;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ChannelRequest} record and constants.
 */
class ChannelRequestTest {

    @Test void testConstants() {
        assertThat(ChannelRequest.PTY_REQ).isEqualTo("pty-req");
        assertThat(ChannelRequest.SHELL).isEqualTo("shell");
        assertThat(ChannelRequest.EXEC).isEqualTo("exec");
        assertThat(ChannelRequest.SUBSYSTEM).isEqualTo("subsystem");
        assertThat(ChannelRequest.ENV).isEqualTo("env");
        assertThat(ChannelRequest.SIGNAL).isEqualTo("signal");
        assertThat(ChannelRequest.EXIT_STATUS).isEqualTo("exit-status");
        assertThat(ChannelRequest.EXIT_SIGNAL).isEqualTo("exit-signal");
        assertThat(ChannelRequest.WINDOW_CHANGE).isEqualTo("window-change");
        assertThat(ChannelRequest.XON_XOFF).isEqualTo("xon-xoff");
        assertThat(ChannelRequest.AUTH_AGENT_REQ).isEqualTo("auth-agent-req@openssh.com");
        assertThat(ChannelRequest.X11_REQ).isEqualTo("x11-req");
        assertThat(ChannelRequest.X11_FORWARDING).isEqualTo("x11");
    }

    @Test void testBasicChannelRequest() {
        var data = "ls -la".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var req = new ChannelRequest(0, ChannelRequest.EXEC, true, data);
        assertThat(req.recipientChannel()).isEqualTo(0);
        assertThat(req.requestType()).isEqualTo("exec");
        assertThat(req.wantReply()).isTrue();
        assertThat(req.data()).isEqualTo(data);
    }

    @Test void testShellRequest() {
        var req = new ChannelRequest(1, ChannelRequest.SHELL, true, new byte[0]);
        assertThat(req.recipientChannel()).isEqualTo(1);
        assertThat(req.requestType()).isEqualTo("shell");
        assertThat(req.wantReply()).isTrue();
    }

    @Test void testPtyRequest() {
        // PTY request with terminal type data
        var ptyData = new byte[]{0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x08};
        var req = new ChannelRequest(2, ChannelRequest.PTY_REQ, true, ptyData);
        assertThat(req.requestType()).isEqualTo("pty-req");
    }

    @Test void testNoReply() {
        var data = new byte[]{42};
        var req = new ChannelRequest(0, ChannelRequest.SIGNAL, false, data);
        assertThat(req.wantReply()).isFalse();
    }

    @Test void testNullDataAllowed() {
        var req = new ChannelRequest(0, "custom", true, null);
        assertThat(req.data()).isNull();
    }

    @Test void testEqualsAndHashCode() {
        var data = "cmd".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var r1 = new ChannelRequest(0, "exec", true, data);
        var r2 = new ChannelRequest(0, "exec", true, data);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test void testDifferentChannelDifferentEquals() {
        var data = new byte[0];
        var r1 = new ChannelRequest(0, "exec", true, data);
        var r2 = new ChannelRequest(1, "exec", true, data);
        assertThat(r1).isNotEqualTo(r2);
    }

    @Test void testSubsystemSftp() {
        var sftpData = "sftp".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var req = new ChannelRequest(0, ChannelRequest.SUBSYSTEM, true, sftpData);
        assertThat(req.requestType()).isEqualTo("subsystem");
    }

    @Test void testExitStatus() {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(4);
        bb.putInt(0); // exit code 0
        var req = new ChannelRequest(0, ChannelRequest.EXIT_STATUS, false, bb.array());
        assertThat(req.requestType()).isEqualTo("exit-status");
    }

    @Test void testEnvironmentVariable() {
        var envData = "PATH=/usr/bin".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var req = new ChannelRequest(0, ChannelRequest.ENV, true, envData);
        assertThat(req.requestType()).isEqualTo("env");
    }

    @Test void testWindowChange() {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(16);
        bb.putInt(80);  // cols
        bb.putInt(24);  // rows
        bb.putInt(640); // width
        bb.putInt(480); // height
        var req = new ChannelRequest(0, ChannelRequest.WINDOW_CHANGE, false, bb.array());
        assertThat(req.requestType()).isEqualTo("window-change");
    }

    @Test void testX11Forwarding() {
        var x11Data = "localhost:0".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var req = new ChannelRequest(0, ChannelRequest.X11_REQ, true, x11Data);
        assertThat(req.requestType()).isEqualTo("x11-req");
    }

    @Test void testAuthAgentForwarding() {
        var req = new ChannelRequest(0, ChannelRequest.AUTH_AGENT_REQ, true, new byte[0]);
        assertThat(req.requestType()).isEqualTo("auth-agent-req@openssh.com");
    }
}
