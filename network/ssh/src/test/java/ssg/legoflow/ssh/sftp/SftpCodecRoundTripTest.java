package ssg.legoflow.ssh.sftp;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SftpCodecRoundTripTest {

    @Test void testReadPacket() {
        var original = new SftpPacket.Read(1, new byte[]{1, 2}, 0L, 1024);
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Read) SftpCodec.decode(encoded);
        assertThat(decoded.id()).isEqualTo(1);
        assertThat(decoded.offset()).isEqualTo(0L);
        assertThat(decoded.length()).isEqualTo(1024);
    }

    @Test void testWritePacket() {
        var original = new SftpPacket.Write(2, new byte[]{3, 4}, 0L, "hello".getBytes());
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Write) SftpCodec.decode(encoded);
        assertThat(decoded.id()).isEqualTo(2);
        assertThat(decoded.data()).isEqualTo("hello".getBytes());
    }

    @Test void testLstatPacket() {
        var original = new SftpPacket.Lstat(5, "/path/to/file");
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Lstat) SftpCodec.decode(encoded);
        assertThat(decoded.path()).isEqualTo("/path/to/file");
    }

    @Test void testSetstatEncodes() {
        SftpFileAttributes attrs = new SftpFileAttributes(
                SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS, 0, 0, 0, 0644, 0, 0);
        var original = new SftpPacket.Setstat(7, "/file", attrs);
        var encoded = SftpCodec.encode(original);
        assertThat(encoded).isNotEmpty();
    }

    @Test void testFsetstatEncodes() {
        SftpFileAttributes attrs = SftpFileAttributes.empty();
        var original = new SftpPacket.Fsetstat(8, new byte[]{11}, attrs);
        var encoded = SftpCodec.encode(original);
        assertThat(encoded).isNotEmpty();
    }

    @Test void testOpendirPacket() {
        var original = new SftpPacket.Opendir(9, "/directory");
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Opendir) SftpCodec.decode(encoded);
        assertThat(decoded.path()).isEqualTo("/directory");
    }

    @Test void testReaddirPacket() {
        var original = new SftpPacket.Readdir(10, new byte[]{12});
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Readdir) SftpCodec.decode(encoded);
        assertThat(decoded.id()).isEqualTo(10);
    }

    @Test void testRemovePacket() {
        var original = new SftpPacket.Remove(11, "/file.txt");
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Remove) SftpCodec.decode(encoded);
        assertThat(decoded.filename()).isEqualTo("/file.txt");
    }

    @Test void testRmdirPacket() {
        var original = new SftpPacket.Rmdir(12, "/dir");
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Rmdir) SftpCodec.decode(encoded);
        assertThat(decoded.path()).isEqualTo("/dir");
    }

    @Test void testSymlinkEncodes() {
        var original = new SftpPacket.Symlink(13, "/link", "/target");
        var encoded = SftpCodec.encode(original);
        assertThat(encoded).isNotEmpty();
    }

    @Test void testStatPacket() {
        var original = new SftpPacket.Stat(14, "/path/to/stat");
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Stat) SftpCodec.decode(encoded);
        assertThat(decoded.path()).isEqualTo("/path/to/stat");
    }

    @Test void testStatusWithFailure() {
        var original = new SftpPacket.Status(
                15, SftpStatusCode.SSH_FX_NO_SUCH_FILE, "no such file", "en");
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Status) SftpCodec.decode(encoded);
        assertThat(decoded.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_NO_SUCH_FILE);
    }

    @Test void testNameWithMultipleEntries() {
        var entry1 = new SftpPacket.NameEntry("file.txt", "-rw-r--r-- 1 user group 100 Jan 1 file.txt",
                SftpFileAttributes.empty());
        var entry2 = new SftpPacket.NameEntry("dir/", "drwxr-xr-x 1 user group 4096 Jan 1 dir",
                SftpFileAttributes.empty());
        var original = new SftpPacket.Name(16, List.of(entry1, entry2));
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Name) SftpCodec.decode(encoded);
        assertThat(decoded.entries()).hasSize(2);
    }

    @Test void testWriteWithLargeOffset() {
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) data[i] = (byte) i;
        var original = new SftpPacket.Write(17, new byte[]{1}, 99999L, data);
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Write) SftpCodec.decode(encoded);
        assertThat(decoded.offset()).isEqualTo(99999L);
        assertThat(decoded.data()).hasSize(256);
    }

    @Test void testReadWithLargeOffset() {
        var original = new SftpPacket.Read(18, new byte[]{5}, 268435456L, 8192);
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Read) SftpCodec.decode(encoded);
        assertThat(decoded.offset()).isEqualTo(268435456L);
    }

    @Test void testOpenWithFullAttributes() {
        SftpFileAttributes attrs = new SftpFileAttributes(
                SftpFileAttributes.SSH_FILEXFER_ATTR_SIZE | SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS,
                1024, 1000, 1000, 0644, 1700000000L, 1700000000L);
        var original = new SftpPacket.Open(19, "newfile.txt", SftpCodec.SSH_FXF_WRITE | SftpCodec.SSH_FXF_CREAT, attrs);
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Open) SftpCodec.decode(encoded);
        assertThat(decoded.filename()).isEqualTo("newfile.txt");
    }

    @Test void testDataWithLargePayload() {
        byte[] payload = new byte[4096];
        for (int i = 0; i < 4096; i++) payload[i] = (byte)(i & 0xFF);
        var original = new SftpPacket.Data(20, payload);
        var encoded = SftpCodec.encode(original);
        var decoded = (SftpPacket.Data) SftpCodec.decode(encoded);
        assertThat(decoded.data()).hasSize(4096);
    }

    @Test void testDecodeUnknownPacketType() {
        // Unknown packet type should throw IllegalArgumentException
        byte[] buf = new byte[]{100, 0, 0, 0};
        assertThatThrownBy(() -> SftpCodec.decode(buf))
                .isInstanceOf(RuntimeException.class);
    }
}
