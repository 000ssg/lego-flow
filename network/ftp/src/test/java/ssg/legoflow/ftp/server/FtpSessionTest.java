package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.protocol.FtpTransferType;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link FtpSession}.
 */
class FtpSessionTest {

    @Test
    void testInitialState() {
        var session = new FtpSession("127.0.0.1:12345");
        assertThat(session.state()).isEqualTo(FtpSession.State.NOT_AUTHENTICATED);
        assertThat(session.username()).isNull();
        assertThat(session.currentDirectory()).isEqualTo("/");
        assertThat(session.transferType()).isEqualTo(FtpTransferType.BINARY);
        assertThat(session.dataMode()).isEqualTo(FtpSession.DataMode.PASSIVE);
        assertThat(session.renameFrom()).isNull();
        assertThat(session.isTlsEnabled()).isFalse();
        assertThat(session.isDataProtected()).isFalse();
        assertThat(session.isAuthenticated()).isFalse();
    }

    @Test
    void testSetState() {
        var session = new FtpSession("client");
        session.setState(FtpSession.State.USER_PROVIDED);
        assertThat(session.state()).isEqualTo(FtpSession.State.USER_PROVIDED);
        session.setState(FtpSession.State.AUTHENTICATED);
        assertThat(session.state()).isEqualTo(FtpSession.State.AUTHENTICATED);
        assertThat(session.isAuthenticated()).isTrue();
    }

    @Test
    void testSetUsername() {
        var session = new FtpSession("client");
        session.setUsername("admin");
        assertThat(session.username()).isEqualTo("admin");
    }

    @Test
    void testSetCurrentDirectory() {
        var session = new FtpSession("client");
        session.setCurrentDirectory("/home/user");
        assertThat(session.currentDirectory()).isEqualTo("/home/user");
    }

    @Test
    void testSetTransferType() {
        var session = new FtpSession("client");
        session.setTransferType(FtpTransferType.ASCII);
        assertThat(session.transferType()).isEqualTo(FtpTransferType.ASCII);
    }

    @Test
    void testSetDataMode() {
        var session = new FtpSession("client");
        session.setDataMode(FtpSession.DataMode.ACTIVE);
        assertThat(session.dataMode()).isEqualTo(FtpSession.DataMode.ACTIVE);
    }

    @Test
    void testSetRenameFrom() {
        var session = new FtpSession("client");
        session.setRenameFrom("/old.txt");
        assertThat(session.renameFrom()).isEqualTo("/old.txt");
    }

    @Test
    void testSetDataAddress() {
        var session = new FtpSession("client");
        session.setDataAddress(InetAddress.getLoopbackAddress());
        assertThat(session.dataAddress()).isEqualTo(InetAddress.getLoopbackAddress());
    }

    @Test
    void testSetDataPort() {
        var session = new FtpSession("client");
        session.setDataPort(12345);
        assertThat(session.dataPort()).isEqualTo(12345);
    }

    @Test
    void testSetTlsEnabled() {
        var session = new FtpSession("client");
        session.setTlsEnabled(true);
        assertThat(session.isTlsEnabled()).isTrue();
    }

    @Test
    void testSetDataProtected() {
        var session = new FtpSession("client");
        session.setDataProtected(true);
        assertThat(session.isDataProtected()).isTrue();
    }

    @Test
    void testResolvePathAbsolute() {
        var session = new FtpSession("client");
        session.setCurrentDirectory("/home");
        assertThat(session.resolvePath("/etc/file")).isEqualTo("/etc/file");
    }

    @Test
    void testResolvePathRelative() {
        var session = new FtpSession("client");
        session.setCurrentDirectory("/home/user");
        assertThat(session.resolvePath("file.txt")).isEqualTo("/home/user/file.txt");
    }

    @Test
    void testResolvePathNull() {
        var session = new FtpSession("client");
        session.setCurrentDirectory("/home");
        assertThat(session.resolvePath(null)).isEqualTo("/home");
    }

    @Test
    void testResolvePathEmpty() {
        var session = new FtpSession("client");
        session.setCurrentDirectory("/home");
        assertThat(session.resolvePath("")).isEqualTo("/home");
    }

    @Test
    void testNormalizePath() {
        assertThat(FtpSession.normalizePath("/a/b/c")).isEqualTo("/a/b/c");
        assertThat(FtpSession.normalizePath("/a/b/../c")).isEqualTo("/a/c");
        assertThat(FtpSession.normalizePath("/a/./b")).isEqualTo("/a/b");
        assertThat(FtpSession.normalizePath("/a/b/../../c")).isEqualTo("/c");
        assertThat(FtpSession.normalizePath("/")).isEqualTo("/");
        assertThat(FtpSession.normalizePath("/../..")).isEqualTo("/");
    }

    @Test
    void testNormalizePathRemovesDots() {
        assertThat(FtpSession.normalizePath("/a/./b/./c")).isEqualTo("/a/b/c");
    }

    @Test
    void testNormalizePathDoesNotEscapeRoot() {
        assertThat(FtpSession.normalizePath("/../../../../etc/passwd")).isEqualTo("/etc/passwd");
    }

    @Test
    void testToString() {
        var session = new FtpSession("127.0.0.1:1234");
        session.setUsername("testuser");
        String str = session.toString();
        assertThat(str).contains("testuser");
        assertThat(str).contains("127.0.0.1:1234");
    }

    @Test
    void testClientAddress() {
        var session = new FtpSession("10.0.0.1:5555");
        assertThat(session.clientAddress()).isEqualTo("10.0.0.1:5555");
    }
}
