package ssg.legoflow.ftp.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PassiveDataConnection}.
 */
class PassiveDataConnectionTest {

    @Test
    void testParsePasvReply() throws IOException {
        Object[] result = PassiveDataConnection.parsePasvReply(
                "Entering Passive Mode (192,168,1,1,4,1).");
        InetAddress addr = (InetAddress) result[0];
        int port = (int) result[1];
        assertThat(addr.getHostAddress()).isEqualTo("192.168.1.1");
        assertThat(port).isEqualTo(1025);
    }

    @Test
    void testParsePasvReplyInvalidNoParentheses() {
        assertThatIOException().isThrownBy(
                () -> PassiveDataConnection.parsePasvReply("No parentheses here"));
    }

    @Test
    void testFormatPasvReply() {
        InetAddress addr = InetAddress.getLoopbackAddress();
        String result = PassiveDataConnection.formatPasvReply(addr, 1025);
        assertThat(result).isEqualTo("(127,0,0,1,4,1)");
    }

    @Test
    void testParseEpsvReply() throws IOException {
        int port = PassiveDataConnection.parseEpsvReply(
                "Entering Extended Passive Mode (|||6789|)");
        assertThat(port).isEqualTo(6789);
    }

    @Test
    void testParseEpsvReplyHighPort() throws IOException {
        int port = PassiveDataConnection.parseEpsvReply(
                "Entering Extended Passive Mode (|||65535|)");
        assertThat(port).isEqualTo(65535);
    }

    @Test
    void testParseEpsvReplyInvalid() {
        assertThatIOException().isThrownBy(
                () -> PassiveDataConnection.parseEpsvReply("Invalid reply"));
    }

    @Test
    void testConstructionServerSide() {
        var conn = new PassiveDataConnection(InetAddress.getLoopbackAddress(), 0, true);
        assertThat(conn.isOpen()).isFalse();
    }

    @Test
    void testConstructionClientSide() {
        var conn = new PassiveDataConnection(InetAddress.getLoopbackAddress(), 1234, false);
        assertThat(conn.isOpen()).isFalse();
        assertThat(conn.getAddress()).isEqualTo(InetAddress.getLoopbackAddress());
    }

    @Test
    void testListenOnClientSideThrows() {
        var conn = new PassiveDataConnection(InetAddress.getLoopbackAddress(), 0, false);
        assertThatIOException().isThrownBy(conn::listen);
    }

    @Test
    void testAcceptWithoutListenThrows() {
        var conn = new PassiveDataConnection(InetAddress.getLoopbackAddress(), 0, true);
        assertThatIOException().isThrownBy(conn::accept);
    }

    @Test
    void testListenAndGetLocalPort() throws IOException {
        var conn = new PassiveDataConnection(InetAddress.getLoopbackAddress(), 0, true);
        try {
            int port = conn.listen();
            assertThat(port).isGreaterThan(0);
            assertThat(conn.getLocalPort()).isEqualTo(port);
        } finally {
            conn.close();
        }
    }

    @Test
    void testGetInputStreamWhenNotOpen() {
        var conn = new PassiveDataConnection(InetAddress.getLoopbackAddress(), 1234, false);
        assertThatIOException().isThrownBy(conn::getInputStream);
    }

    @Test
    void testGetOutputStreamWhenNotOpen() {
        var conn = new PassiveDataConnection(InetAddress.getLoopbackAddress(), 1234, false);
        assertThatIOException().isThrownBy(conn::getOutputStream);
    }

    @Test
    void testRoundTripPasvFormat() throws IOException {
        InetAddress addr = InetAddress.getByName("10.20.30.40");
        int port = 5000;
        String formatted = PassiveDataConnection.formatPasvReply(addr, port);
        // Wrap with standard reply text for parsing
        Object[] parsed = PassiveDataConnection.parsePasvReply("227 Entering Passive Mode " + formatted);
        assertThat(((InetAddress) parsed[0]).getHostAddress()).isEqualTo("10.20.30.40");
        assertThat((int) parsed[1]).isEqualTo(5000);
    }
}
