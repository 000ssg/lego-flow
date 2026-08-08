package ssg.legoflow.ssh.sftp;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SftpFileAttributes}.
 */
class SftpFileAttributesTest {

    @Test void testFlagsConstants() {
        assertThat(SftpFileAttributes.SSH_FILEXFER_ATTR_SIZE).isEqualTo(0x00000001);
        assertThat(SftpFileAttributes.SSH_FILEXFER_ATTR_UIDGID).isEqualTo(0x00000002);
        assertThat(SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS).isEqualTo(0x00000004);
        assertThat(SftpFileAttributes.SSH_FILEXFER_ATTR_ACMODTIME).isEqualTo(0x00000008);
    }

    @Test void testBasicAttributes() {
        var attr = new SftpFileAttributes(0, 0, 0, 0, 0, 0, 0);
        assertThat(attr.flags()).isEqualTo(0);
        assertThat(attr.size()).isEqualTo(0);
        assertThat(attr.uid()).isEqualTo(0);
        assertThat(attr.gid()).isEqualTo(0);
        assertThat(attr.permissions()).isEqualTo(0);
        assertThat(attr.atime()).isEqualTo(0);
        assertThat(attr.mtime()).isEqualTo(0);
    }

    @Test void testFullAttributes() {
        int flags = SftpFileAttributes.SSH_FILEXFER_ATTR_SIZE
                  | SftpFileAttributes.SSH_FILEXFER_ATTR_UIDGID
                  | SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS
                  | SftpFileAttributes.SSH_FILEXFER_ATTR_ACMODTIME;
        var attr = new SftpFileAttributes(flags, 1024, 1000, 1000, 0644, 1700000000L, 1700001000L);
        assertThat(attr.flags()).isEqualTo(flags);
        assertThat(attr.size()).isEqualTo(1024);
        assertThat(attr.uid()).isEqualTo(1000);
        assertThat(attr.gid()).isEqualTo(1000);
        assertThat(attr.permissions()).isEqualTo(0644);
        assertThat(attr.atime()).isEqualTo(1700000000L);
        assertThat(attr.mtime()).isEqualTo(1700001000L);
    }

    @Test void testIsDirectory() {
        var dir = new SftpFileAttributes(SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS, 0, 0, 0, 040755, 0, 0);
        assertThat(dir.isDirectory()).isTrue();
        var file = new SftpFileAttributes(SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS, 0, 0, 0, 010644, 0, 0);
        assertThat(file.isDirectory()).isFalse();
    }

    @Test void testIsRegularFile() {
        // Unix file type bits: regular file = 0100000, directory = 040000
        var file = new SftpFileAttributes(SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS, 0, 0, 0, 0100644, 0, 0);
        assertThat(file.isRegularFile()).isTrue();
        var dir = new SftpFileAttributes(SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS, 0, 0, 0, 040755, 0, 0);
        assertThat(dir.isRegularFile()).isFalse();
    }

    @Test void testIsSymlink() {
        var link = new SftpFileAttributes(SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS, 0, 0, 0, 0120777, 0, 0);
        assertThat(link.isSymlink()).isTrue();
        var file = new SftpFileAttributes(SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS, 0, 0, 0, 010644, 0, 0);
        assertThat(file.isSymlink()).isFalse();
    }

    @Test void testEncodeMinimal() {
        var attr = new SftpFileAttributes(0, 0, 0, 0, 0, 0, 0);
        byte[] encoded = attr.encode();
        // Just flags (4 bytes)
        assertThat(encoded).hasSize(4);
    }

    @Test void testEncodeWithSize() {
        var attr = new SftpFileAttributes(SftpFileAttributes.SSH_FILEXFER_ATTR_SIZE, 42, 0, 0, 0, 0, 0);
        byte[] encoded = attr.encode();
        // flags(4) + size(8) = 12 bytes
        assertThat(encoded).hasSize(12);
    }

    @Test void testEncodeAllFields() {
        int flags = SftpFileAttributes.SSH_FILEXFER_ATTR_SIZE
                  | SftpFileAttributes.SSH_FILEXFER_ATTR_UIDGID
                  | SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS
                  | SftpFileAttributes.SSH_FILEXFER_ATTR_ACMODTIME;
        var attr = new SftpFileAttributes(flags, 1024, 1000, 1000, 0644, 1700000000L, 1700001000L);
        byte[] encoded = attr.encode();
        // flags(4) + size(8) + uid(4) + gid(4) + perms(4) + atime(4) + mtime(4) = 32 bytes
        assertThat(encoded).hasSize(32);
    }

    @Test void testEncodeDecodeRoundTrip() {
        int flags = SftpFileAttributes.SSH_FILEXFER_ATTR_SIZE
                  | SftpFileAttributes.SSH_FILEXFER_ATTR_UIDGID
                  | SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS
                  | SftpFileAttributes.SSH_FILEXFER_ATTR_ACMODTIME;
        var original = new SftpFileAttributes(flags, 1024, 1000, 1000, 0644, 1700000000L, 1700001000L);
        byte[] encoded = original.encode();
        // Decode back and verify round-trip using ByteBuffer
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(encoded);
        var decoded = SftpFileAttributes.decode(buf);
        assertThat(decoded.flags()).isEqualTo(flags);
        assertThat(decoded.size()).isEqualTo(1024);
        assertThat(decoded.uid()).isEqualTo(1000);
        assertThat(decoded.gid()).isEqualTo(1000);
    }
}
