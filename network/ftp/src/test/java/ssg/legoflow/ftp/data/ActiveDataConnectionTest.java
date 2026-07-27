package ssg.legoflow.ftp.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ActiveDataConnection}.
 */
class ActiveDataConnectionTest {

    @Test
    void testFormatPortArgument() {
        InetAddress addr = InetAddress.getLoopbackAddress();
        String result = ActiveDataConnection.formatPortArgument(addr, 1025);
        // 1025 = 4*256 + 1
        assertThat(result).isEqualTo("127,0,0,1,4,1");
    }

    @Test
    void testFormatPortArgumentPort80() {
        InetAddress addr = InetAddress.getLoopbackAddress();
        String result = ActiveDataConnection.formatPortArgument(addr, 80);
        // 80 = 0*256 + 80
        assertThat(result).isEqualTo("127,0,0,1,0,80");
    }

    @Test
    void testFormatPortArgumentHighPort() {
        InetAddress addr = InetAddress.getLoopbackAddress();
        String result = ActiveDataConnection.formatPortArgument(addr, 65535);
        // 65535 = 255*256 + 255
        assertThat(result).isEqualTo("127,0,0,1,255,255");
    }

    @Test
    void testParsePortArgument() throws IOException {
        Object[] result = ActiveDataConnection.parsePortArgument("192,168,1,100,4,1");
        InetAddress addr = (InetAddress) result[0];
        int port = (int) result[1];
        assertThat(addr.getHostAddress()).isEqualTo("192.168.1.100");
        assertThat(port).isEqualTo(1025);
    }

    @Test
    void testParsePortArgumentInvalidThrows() {
        assertThatIOException().isThrownBy(
                () -> ActiveDataConnection.parsePortArgument("192,168,1"));
    }

    @Test
    void testParsePortArgumentNonNumericThrows() {
        assertThatIOException().isThrownBy(
                () -> ActiveDataConnection.parsePortArgument("abc,0,0,1,0,80"));
    }

    @Test
    void testFormatEprtArgument() {
        InetAddress addr = InetAddress.getLoopbackAddress();
        String result = ActiveDataConnection.formatEprtArgument(addr, 6789);
        assertThat(result).isEqualTo("|1|127.0.0.1|6789|");
    }

    @Test
    void testParseEprtArgument() throws IOException {
        Object[] result = ActiveDataConnection.parseEprtArgument("|1|192.168.1.1|6789|");
        InetAddress addr = (InetAddress) result[0];
        int port = (int) result[1];
        assertThat(addr.getHostAddress()).isEqualTo("192.168.1.1");
        assertThat(port).isEqualTo(6789);
    }

    @Test
    void testParseEprtArgumentInvalidFormat() {
        assertThatIOException().isThrownBy(
                () -> ActiveDataConnection.parseEprtArgument("bad format"));
    }

    @Test
    void testParseEprtArgumentMissingParts() {
        assertThatIOException().isThrownBy(
                () -> ActiveDataConnection.parseEprtArgument("|1|192.168.1.1|"));
    }

    @Test
    void testRoundTripPortArgument() throws IOException {
        InetAddress addr = InetAddress.getByName("10.0.0.1");
        int port = 12345;
        String formatted = ActiveDataConnection.formatPortArgument(addr, port);
        Object[] parsed = ActiveDataConnection.parsePortArgument(formatted);
        assertThat(((InetAddress) parsed[0]).getHostAddress()).isEqualTo("10.0.0.1");
        assertThat((int) parsed[1]).isEqualTo(12345);
    }

    @Test
    void testRoundTripEprtArgument() throws IOException {
        InetAddress addr = InetAddress.getByName("10.0.0.1");
        int port = 54321;
        String formatted = ActiveDataConnection.formatEprtArgument(addr, port);
        Object[] parsed = ActiveDataConnection.parseEprtArgument(formatted);
        assertThat(((InetAddress) parsed[0]).getHostAddress()).isEqualTo("10.0.0.1");
        assertThat((int) parsed[1]).isEqualTo(54321);
    }

    @Test
    void testConstructionServerSide() {
        var conn = new ActiveDataConnection(InetAddress.getLoopbackAddress(), 1234, true);
        assertThat(conn.isOpen()).isFalse();
        assertThat(conn.getPort()).isEqualTo(1234);
        assertThat(conn.getAddress()).isEqualTo(InetAddress.getLoopbackAddress());
    }

    @Test
    void testConstructionClientSide() {
        var conn = new ActiveDataConnection(InetAddress.getLoopbackAddress(), 0, false);
        assertThat(conn.isOpen()).isFalse();
    }

    @Test
    void testGetInputStreamWhenNotOpen() {
        var conn = new ActiveDataConnection(InetAddress.getLoopbackAddress(), 1234, true);
        assertThatIOException().isThrownBy(conn::getInputStream);
    }

    @Test
    void testGetOutputStreamWhenNotOpen() {
        var conn = new ActiveDataConnection(InetAddress.getLoopbackAddress(), 1234, true);
        assertThatIOException().isThrownBy(conn::getOutputStream);
    }
}
