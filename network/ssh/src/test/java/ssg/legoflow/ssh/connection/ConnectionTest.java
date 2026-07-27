package ssg.legoflow.ssh.connection;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConnectionTest {

    @Test
    void testWindowManagerDefaults() {
        WindowManager wm = new WindowManager();
        assertThat(wm.localWindow()).isEqualTo(WindowManager.DEFAULT_WINDOW_SIZE);
        assertThat(wm.remoteWindow()).isEqualTo(0);
        assertThat(wm.initialWindowSize()).isEqualTo(WindowManager.DEFAULT_WINDOW_SIZE);
        assertThat(wm.maxPacketSize()).isEqualTo(WindowManager.DEFAULT_MAX_PACKET_SIZE);
    }

    @Test
    void testWindowManagerSetRemoteWindow() {
        WindowManager wm = new WindowManager();
        wm.setRemoteWindow(100000);
        assertThat(wm.remoteWindow()).isEqualTo(100000);
    }

    @Test
    void testWindowManagerAdjustRemoteWindow() {
        WindowManager wm = new WindowManager();
        wm.setRemoteWindow(100);
        wm.adjustRemoteWindow(50);
        assertThat(wm.remoteWindow()).isEqualTo(150);
    }

    @Test
    void testWindowManagerConsumeRemoteWindow() {
        WindowManager wm = new WindowManager();
        wm.setRemoteWindow(100);
        assertThat(wm.consumeRemoteWindow(50)).isTrue();
        assertThat(wm.remoteWindow()).isEqualTo(50);
    }

    @Test
    void testWindowManagerConsumeRemoteWindowInsufficient() {
        WindowManager wm = new WindowManager();
        wm.setRemoteWindow(10);
        assertThat(wm.consumeRemoteWindow(20)).isFalse();
        assertThat(wm.remoteWindow()).isEqualTo(10);
    }

    @Test
    void testWindowManagerConsumeLocalWindow() {
        WindowManager wm = new WindowManager();
        long initial = wm.localWindow();
        wm.consumeLocalWindow(1000);
        assertThat(wm.localWindow()).isEqualTo(initial - 1000);
    }

    @Test
    void testWindowManagerShouldAdjust() {
        WindowManager wm = new WindowManager();
        assertThat(wm.shouldAdjust()).isFalse();
        // Consume most of the window
        wm.consumeLocalWindow(wm.localWindow() - 100);
        assertThat(wm.shouldAdjust()).isTrue();
    }

    @Test
    void testWindowManagerAdjustLocalWindow() {
        WindowManager wm = new WindowManager();
        wm.consumeLocalWindow(1000);
        long toAdd = wm.adjustLocalWindow();
        assertThat(toAdd).isEqualTo(1000);
        assertThat(wm.localWindow()).isEqualTo(WindowManager.DEFAULT_WINDOW_SIZE);
    }

    @Test
    void testWindowManagerCustomSizes() {
        WindowManager wm = new WindowManager(500000, 16384);
        assertThat(wm.initialWindowSize()).isEqualTo(500000);
        assertThat(wm.maxPacketSize()).isEqualTo(16384);
    }

    @Test
    void testChannelRequestConstants() {
        assertThat(ChannelRequest.PTY_REQ).isEqualTo("pty-req");
        assertThat(ChannelRequest.SHELL).isEqualTo("shell");
        assertThat(ChannelRequest.EXEC).isEqualTo("exec");
        assertThat(ChannelRequest.SUBSYSTEM).isEqualTo("subsystem");
        assertThat(ChannelRequest.EXIT_STATUS).isEqualTo("exit-status");
    }

    @Test
    void testGlobalRequestTcpIpForwardEncode() {
        byte[] encoded = GlobalRequest.encodeTcpIpForward("0.0.0.0", 8080);
        assertThat(encoded).isNotEmpty();
        assertThat(encoded[0]).isEqualTo((byte) 80); // SSH_MSG_GLOBAL_REQUEST
    }

    @Test
    void testGlobalRequestCancelTcpIpForwardEncode() {
        byte[] encoded = GlobalRequest.encodeCancelTcpIpForward("0.0.0.0", 8080);
        assertThat(encoded).isNotEmpty();
        assertThat(encoded[0]).isEqualTo((byte) 80);
    }

    @Test
    void testGlobalRequestConstants() {
        assertThat(GlobalRequest.TCPIP_FORWARD).isEqualTo("tcpip-forward");
        assertThat(GlobalRequest.CANCEL_TCPIP_FORWARD).isEqualTo("cancel-tcpip-forward");
    }
}
