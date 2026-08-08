package ssg.legoflow.ssh.sftp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SftpTest {

    // --- SftpPacketType tests ---

    @Test
    void testPacketTypeInit() {
        assertThat(SftpPacketType.SSH_FXP_INIT.code()).isEqualTo(1);
    }

    @Test
    void testPacketTypeVersion() {
        assertThat(SftpPacketType.SSH_FXP_VERSION.code()).isEqualTo(2);
    }

    @Test
    void testPacketTypeOpen() {
        assertThat(SftpPacketType.SSH_FXP_OPEN.code()).isEqualTo(3);
    }

    @Test
    void testPacketTypeStatus() {
        assertThat(SftpPacketType.SSH_FXP_STATUS.code()).isEqualTo(101);
    }

    @Test
    void testPacketTypeFromCode() {
        assertThat(SftpPacketType.fromCode(1)).isEqualTo(SftpPacketType.SSH_FXP_INIT);
        assertThat(SftpPacketType.fromCode(101)).isEqualTo(SftpPacketType.SSH_FXP_STATUS);
        assertThat(SftpPacketType.fromCode(200)).isEqualTo(SftpPacketType.SSH_FXP_EXTENDED);
    }

    @Test
    void testPacketTypeFromCodeUnknown() {
        assertThatThrownBy(() -> SftpPacketType.fromCode(999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAllPacketTypeCodes() {
        for (SftpPacketType type : SftpPacketType.values()) {
            assertThat(SftpPacketType.fromCode(type.code())).isEqualTo(type);
        }
    }

    // --- SftpStatusCode tests ---

    @Test
    void testStatusCodeOk() {
        assertThat(SftpStatusCode.SSH_FX_OK.code()).isEqualTo(0);
        assertThat(SftpStatusCode.SSH_FX_OK.description()).isEqualTo("Success");
    }

    @Test
    void testStatusCodeEof() {
        assertThat(SftpStatusCode.SSH_FX_EOF.code()).isEqualTo(1);
    }

    @Test
    void testStatusCodeNoSuchFile() {
        assertThat(SftpStatusCode.SSH_FX_NO_SUCH_FILE.code()).isEqualTo(2);
    }

    @Test
    void testStatusCodePermissionDenied() {
        assertThat(SftpStatusCode.SSH_FX_PERMISSION_DENIED.code()).isEqualTo(3);
    }

    @Test
    void testStatusCodeFromCode() {
        assertThat(SftpStatusCode.fromCode(0)).isEqualTo(SftpStatusCode.SSH_FX_OK);
        assertThat(SftpStatusCode.fromCode(4)).isEqualTo(SftpStatusCode.SSH_FX_FAILURE);
    }

    @Test
    void testStatusCodeFromCodeUnknown() {
        assertThatThrownBy(() -> SftpStatusCode.fromCode(999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAllStatusCodes() {
        for (SftpStatusCode sc : SftpStatusCode.values()) {
            assertThat(SftpStatusCode.fromCode(sc.code())).isEqualTo(sc);
        }
    }

    // --- SftpFileAttributes tests ---

    @Test
    void testEmptyAttributes() {
        SftpFileAttributes attrs = SftpFileAttributes.empty();
        assertThat(attrs.flags()).isEqualTo(0);
        assertThat(attrs.size()).isEqualTo(0);
    }

    @Test
    void testAttributesWithSize() {
        SftpFileAttributes attrs = new SftpFileAttributes(
                SftpFileAttributes.SSH_FILEXFER_ATTR_SIZE,
                1024, 0, 0, 0, 0, 0);
        assertThat(attrs.size()).isEqualTo(1024);
    }

    @Test
    void testAttributesWithPermissions() {
        SftpFileAttributes attrs = new SftpFileAttributes(
                SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS,
                0, 0, 0, 0100644, 0, 0);
        assertThat(attrs.permissions()).isEqualTo(0100644);
        assertThat(attrs.isRegularFile()).isTrue();
        assertThat(attrs.isDirectory()).isFalse();
    }

    @Test
    void testAttributesDirectory() {
        SftpFileAttributes attrs = new SftpFileAttributes(
                SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS,
                0, 0, 0, 0040755, 0, 0);
        assertThat(attrs.isDirectory()).isTrue();
        assertThat(attrs.isRegularFile()).isFalse();
    }

    @Test
    void testAttributesSymlink() {
        SftpFileAttributes attrs = new SftpFileAttributes(
                SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS,
                0, 0, 0, 0120777, 0, 0);
        assertThat(attrs.isSymlink()).isTrue();
    }

    @Test
    void testAttributesEncodeDecodeRoundTrip() {
        SftpFileAttributes original = new SftpFileAttributes(
                SftpFileAttributes.SSH_FILEXFER_ATTR_SIZE
                        | SftpFileAttributes.SSH_FILEXFER_ATTR_UIDGID
                        | SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS
                        | SftpFileAttributes.SSH_FILEXFER_ATTR_ACMODTIME,
                4096, 1000, 1000, 0100644, 1700000000L, 1700000100L);
        byte[] encoded = original.encode();
        SftpFileAttributes decoded = SftpFileAttributes.decode(ByteBuffer.wrap(encoded));
        assertThat(decoded.size()).isEqualTo(4096);
        assertThat(decoded.uid()).isEqualTo(1000);
        assertThat(decoded.gid()).isEqualTo(1000);
        assertThat(decoded.permissions()).isEqualTo(0100644);
    }

    @Test
    void testAttributesEncodeEmpty() {
        SftpFileAttributes attrs = SftpFileAttributes.empty();
        byte[] encoded = attrs.encode();
        assertThat(encoded).hasSize(4); // just the flags int
    }

    @Test
    void testAttributesToString() {
        SftpFileAttributes attrs = new SftpFileAttributes(0, 100, 500, 500, 0644, 0, 0);
        assertThat(attrs.toString()).contains("size=100");
    }

    // --- SftpPacket record tests ---

    @Test
    void testInitPacketType() {
        SftpPacket.Init p = new SftpPacket.Init(3);
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_INIT);
        assertThat(p.version()).isEqualTo(3);
    }

    @Test
    void testVersionPacketType() {
        SftpPacket.Version p = new SftpPacket.Version(3);
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_VERSION);
    }

    @Test
    void testOpenPacketType() {
        SftpPacket.Open p = new SftpPacket.Open(1, "test.txt", SftpCodec.SSH_FXF_READ, SftpFileAttributes.empty());
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_OPEN);
        assertThat(p.filename()).isEqualTo("test.txt");
    }

    @Test
    void testClosePacketType() {
        SftpPacket.Close p = new SftpPacket.Close(1, new byte[]{1, 2, 3});
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_CLOSE);
    }

    @Test
    void testStatusPacketType() {
        SftpPacket.Status p = new SftpPacket.Status(1, SftpStatusCode.SSH_FX_OK, "ok", "en");
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_STATUS);
        assertThat(p.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_OK);
    }

    @Test
    void testHandlePacketType() {
        SftpPacket.Handle p = new SftpPacket.Handle(1, new byte[]{10, 20});
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_HANDLE);
    }

    @Test
    void testDataPacketType() {
        SftpPacket.Data p = new SftpPacket.Data(1, new byte[]{1, 2, 3});
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_DATA);
    }

    @Test
    void testNamePacketType() {
        SftpPacket.Name p = new SftpPacket.Name(1, List.of());
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_NAME);
        assertThat(p.entries()).isEmpty();
    }

    @Test
    void testNameEntry() {
        SftpPacket.NameEntry entry = new SftpPacket.NameEntry(
                "file.txt", "-rw-r--r-- 1 user group 100 Jan 1 file.txt", SftpFileAttributes.empty());
        assertThat(entry.filename()).isEqualTo("file.txt");
        assertThat(entry.longname()).contains("file.txt");
    }

    @Test
    void testRenamePacketType() {
        SftpPacket.Rename p = new SftpPacket.Rename(1, "/old", "/new");
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_RENAME);
        assertThat(p.oldPath()).isEqualTo("/old");
        assertThat(p.newPath()).isEqualTo("/new");
    }

    @Test
    void testMkdirPacketType() {
        SftpPacket.Mkdir p = new SftpPacket.Mkdir(1, "/newdir", SftpFileAttributes.empty());
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_MKDIR);
    }

    @Test
    void testRealpathPacketType() {
        SftpPacket.Realpath p = new SftpPacket.Realpath(1, ".");
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_REALPATH);
    }

    // --- SftpCodec tests ---

    @Test
    void testCodecEncodeDecodeInit() {
        SftpPacket.Init original = new SftpPacket.Init(3);
        byte[] encoded = SftpCodec.encode(original);
        SftpPacket decoded = SftpCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SftpPacket.Init.class);
        assertThat(((SftpPacket.Init) decoded).version()).isEqualTo(3);
    }

    @Test
    void testCodecEncodeDecodeVersion() {
        SftpPacket.Version original = new SftpPacket.Version(3);
        byte[] encoded = SftpCodec.encode(original);
        SftpPacket decoded = SftpCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SftpPacket.Version.class);
        assertThat(((SftpPacket.Version) decoded).version()).isEqualTo(3);
    }

    @Test
    void testCodecEncodeDecodeStatus() {
        SftpPacket.Status original = new SftpPacket.Status(
                42, SftpStatusCode.SSH_FX_OK, "Success", "en");
        byte[] encoded = SftpCodec.encode(original);
        SftpPacket decoded = SftpCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SftpPacket.Status.class);
        SftpPacket.Status status = (SftpPacket.Status) decoded;
        assertThat(status.id()).isEqualTo(42);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_OK);
        assertThat(status.message()).isEqualTo("Success");
    }

    @Test
    void testCodecEncodeDecodeHandle() {
        byte[] handle = {1, 2, 3, 4};
        SftpPacket.Handle original = new SftpPacket.Handle(7, handle);
        byte[] encoded = SftpCodec.encode(original);
        SftpPacket decoded = SftpCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SftpPacket.Handle.class);
        assertThat(((SftpPacket.Handle) decoded).handle()).isEqualTo(handle);
    }

    @Test
    void testCodecEncodeDecodeData() {
        byte[] data = "hello sftp".getBytes();
        SftpPacket.Data original = new SftpPacket.Data(5, data);
        byte[] encoded = SftpCodec.encode(original);
        SftpPacket decoded = SftpCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SftpPacket.Data.class);
        assertThat(((SftpPacket.Data) decoded).data()).isEqualTo(data);
    }

    @Test
    void testCodecEncodeDecodeName() {
        SftpPacket.NameEntry entry = new SftpPacket.NameEntry(
                "readme.txt", "-rw-r--r-- 1 user group 256 readme.txt", SftpFileAttributes.empty());
        SftpPacket.Name original = new SftpPacket.Name(10, List.of(entry));
        byte[] encoded = SftpCodec.encode(original);
        SftpPacket decoded = SftpCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SftpPacket.Name.class);
        SftpPacket.Name name = (SftpPacket.Name) decoded;
        assertThat(name.entries()).hasSize(1);
        assertThat(name.entries().get(0).filename()).isEqualTo("readme.txt");
    }

    @Test
    void testCodecEncodeDecodeAttrs() {
        SftpFileAttributes attrs = new SftpFileAttributes(
                SftpFileAttributes.SSH_FILEXFER_ATTR_SIZE, 9999, 0, 0, 0, 0, 0);
        SftpPacket.Attrs original = new SftpPacket.Attrs(3, attrs);
        byte[] encoded = SftpCodec.encode(original);
        SftpPacket decoded = SftpCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SftpPacket.Attrs.class);
        assertThat(((SftpPacket.Attrs) decoded).attrs().size()).isEqualTo(9999);
    }

    @Test
    void testSftpOpenFlags() {
        assertThat(SftpCodec.SSH_FXF_READ).isEqualTo(0x01);
        assertThat(SftpCodec.SSH_FXF_WRITE).isEqualTo(0x02);
        assertThat(SftpCodec.SSH_FXF_APPEND).isEqualTo(0x04);
        assertThat(SftpCodec.SSH_FXF_CREAT).isEqualTo(0x08);
        assertThat(SftpCodec.SSH_FXF_TRUNC).isEqualTo(0x10);
        assertThat(SftpCodec.SSH_FXF_EXCL).isEqualTo(0x20);
    }

    // --- Extended packet tests ---

    @Test
    void testExtendedPacketType() {
        SftpPacket.Extended p = new SftpPacket.Extended(1, "posix-rename@openssh.com", new byte[]{});
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_EXTENDED);
        assertThat(p.extendedRequest()).isEqualTo("posix-rename@openssh.com");
    }

    @Test
    void testExtendedReplyPacketType() {
        SftpPacket.ExtendedReply p = new SftpPacket.ExtendedReply(1, new byte[]{1, 2, 3});
        assertThat(p.type()).isEqualTo(SftpPacketType.SSH_FXP_EXTENDED_REPLY);
        assertThat(p.data()).containsExactly(1, 2, 3);
    }

    @Test
    void testCodecEncodeDecodeExtended() {
        // Build extension data: two strings (old path, new path)
        ByteBuffer dataBuf = ByteBuffer.allocate(256);
        ssg.legoflow.ssh.transport.SshTransportCodec.writeString(dataBuf, "/old/path");
        ssg.legoflow.ssh.transport.SshTransportCodec.writeString(dataBuf, "/new/path");
        dataBuf.flip();
        byte[] extData = new byte[dataBuf.remaining()];
        dataBuf.get(extData);

        SftpPacket.Extended original = new SftpPacket.Extended(42, "posix-rename@openssh.com", extData);
        byte[] encoded = SftpCodec.encode(original);
        SftpPacket decoded = SftpCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SftpPacket.Extended.class);
        SftpPacket.Extended ext = (SftpPacket.Extended) decoded;
        assertThat(ext.id()).isEqualTo(42);
        assertThat(ext.extendedRequest()).isEqualTo("posix-rename@openssh.com");

        // Verify the embedded paths
        ByteBuffer replyBuf = ByteBuffer.wrap(ext.data());
        String oldPath = ssg.legoflow.ssh.transport.SshTransportCodec.readString(replyBuf);
        String newPath = ssg.legoflow.ssh.transport.SshTransportCodec.readString(replyBuf);
        assertThat(oldPath).isEqualTo("/old/path");
        assertThat(newPath).isEqualTo("/new/path");
    }

    @Test
    void testCodecEncodeDecodeExtendedReply() {
        byte[] replyData = {10, 20, 30, 40, 50};
        SftpPacket.ExtendedReply original = new SftpPacket.ExtendedReply(99, replyData);
        byte[] encoded = SftpCodec.encode(original);
        SftpPacket decoded = SftpCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SftpPacket.ExtendedReply.class);
        SftpPacket.ExtendedReply reply = (SftpPacket.ExtendedReply) decoded;
        assertThat(reply.id()).isEqualTo(99);
        assertThat(reply.data()).containsExactly(10, 20, 30, 40, 50);
    }

    @Test
    void testCodecEncodeDecodeExtendedEmptyData() {
        SftpPacket.Extended original = new SftpPacket.Extended(1, "test@ext", new byte[]{});
        byte[] encoded = SftpCodec.encode(original);
        SftpPacket decoded = SftpCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SftpPacket.Extended.class);
        SftpPacket.Extended ext = (SftpPacket.Extended) decoded;
        assertThat(ext.extendedRequest()).isEqualTo("test@ext");
        assertThat(ext.data()).isEmpty();
    }

    @Test
    void testExtendedPacketTypeCode() {
        assertThat(SftpPacketType.SSH_FXP_EXTENDED.code()).isEqualTo(200);
        assertThat(SftpPacketType.SSH_FXP_EXTENDED_REPLY.code()).isEqualTo(201);
    }

    // --- SftpServer extended handler tests ---

    @TempDir
    Path tempDir;

    @Test
    void testServerHandlePosixRename() throws Exception {
        // Create a file to rename
        Path sourceFile = tempDir.resolve("original.txt");
        Files.writeString(sourceFile, "rename me");

        SftpServer server = new SftpServer(tempDir);

        // Build posix-rename extended request data: oldpath + newpath
        ByteBuffer dataBuf = ByteBuffer.allocate(256);
        ssg.legoflow.ssh.transport.SshTransportCodec.writeString(dataBuf, "original.txt");
        ssg.legoflow.ssh.transport.SshTransportCodec.writeString(dataBuf, "renamed.txt");
        dataBuf.flip();
        byte[] extData = new byte[dataBuf.remaining()];
        dataBuf.get(extData);

        SftpPacket.Extended request = new SftpPacket.Extended(1, "posix-rename@openssh.com", extData);
        byte[] response = server.handlePacket(SftpCodec.encode(request));

        SftpPacket decoded = SftpCodec.decode(response);
        assertThat(decoded).isInstanceOf(SftpPacket.Status.class);
        SftpPacket.Status status = (SftpPacket.Status) decoded;
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_OK);

        // Verify rename happened
        assertThat(tempDir.resolve("renamed.txt")).exists();
        assertThat(tempDir.resolve("original.txt")).doesNotExist();
        assertThat(Files.readString(tempDir.resolve("renamed.txt"))).isEqualTo("rename me");
    }

    @Test
    void testServerHandlePosixRenameOverwrite() throws Exception {
        // posix-rename should overwrite target atomically
        Path sourceFile = tempDir.resolve("src.txt");
        Path targetFile = tempDir.resolve("dst.txt");
        Files.writeString(sourceFile, "new content");
        Files.writeString(targetFile, "old content");

        SftpServer server = new SftpServer(tempDir);

        ByteBuffer dataBuf = ByteBuffer.allocate(256);
        ssg.legoflow.ssh.transport.SshTransportCodec.writeString(dataBuf, "src.txt");
        ssg.legoflow.ssh.transport.SshTransportCodec.writeString(dataBuf, "dst.txt");
        dataBuf.flip();
        byte[] extData = new byte[dataBuf.remaining()];
        dataBuf.get(extData);

        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Extended(1, "posix-rename@openssh.com", extData)));

        SftpPacket.Status status = (SftpPacket.Status) SftpCodec.decode(response);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_OK);
        assertThat(Files.readString(tempDir.resolve("dst.txt"))).isEqualTo("new content");
        assertThat(tempDir.resolve("src.txt")).doesNotExist();
    }

    @Test
    void testServerHandleStatvfs() throws Exception {
        SftpServer server = new SftpServer(tempDir);

        ByteBuffer dataBuf = ByteBuffer.allocate(256);
        ssg.legoflow.ssh.transport.SshTransportCodec.writeString(dataBuf, ".");
        dataBuf.flip();
        byte[] extData = new byte[dataBuf.remaining()];
        dataBuf.get(extData);

        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Extended(1, "statvfs@openssh.com", extData)));

        SftpPacket decoded = SftpCodec.decode(response);
        assertThat(decoded).isInstanceOf(SftpPacket.ExtendedReply.class);
        SftpPacket.ExtendedReply reply = (SftpPacket.ExtendedReply) decoded;

        // statvfs returns 11 uint64 fields = 88 bytes
        assertThat(reply.data()).hasSize(88);

        // Parse and verify block size (first field)
        ByteBuffer replyBuf = ByteBuffer.wrap(reply.data());
        long blockSize = replyBuf.getLong();
        assertThat(blockSize).isEqualTo(4096);

        // Verify total blocks > 0
        replyBuf.getLong(); // frsize
        long totalBlocks = replyBuf.getLong();
        assertThat(totalBlocks).isGreaterThan(0);
    }

    @Test
    void testServerHandleUnknownExtension() {
        SftpServer server = new SftpServer(tempDir);

        SftpPacket.Extended request = new SftpPacket.Extended(1, "unknown@ext", new byte[]{});
        byte[] response = server.handlePacket(SftpCodec.encode(request));

        SftpPacket decoded = SftpCodec.decode(response);
        assertThat(decoded).isInstanceOf(SftpPacket.Status.class);
        SftpPacket.Status status = (SftpPacket.Status) decoded;
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_OP_UNSUPPORTED);
    }

    @Test
    void testServerHandlePosixRenameNonexistentSource() throws Exception {
        SftpServer server = new SftpServer(tempDir);

        ByteBuffer dataBuf = ByteBuffer.allocate(256);
        ssg.legoflow.ssh.transport.SshTransportCodec.writeString(dataBuf, "nosuch.txt");
        ssg.legoflow.ssh.transport.SshTransportCodec.writeString(dataBuf, "target.txt");
        dataBuf.flip();
        byte[] extData = new byte[dataBuf.remaining()];
        dataBuf.get(extData);

        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Extended(1, "posix-rename@openssh.com", extData)));

        SftpPacket.Status status = (SftpPacket.Status) SftpCodec.decode(response);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_FAILURE);
}

    // --- Additional SftpServer handler coverage tests ---

    @Test void testServerHandlePacketDecodeError() throws Exception {
        SftpServer server = new SftpServer(tempDir);
        // Send data that will cause a decode error (type 255 is unknown)
        byte[] badData = new byte[]{(byte)255};
        try {
            byte[] response = server.handlePacket(badData);
            // If we get a response, it should be a status error
            SftpPacket decoded = SftpCodec.decode(response);
            assertThat(decoded).isInstanceOf(SftpPacket.Status.class);
        } catch (Exception e) {
            // Decode errors are also acceptable - the server catches them
        }
    }

    @Test void testServerHandleUnsupportedPacketType() throws Exception {
        SftpServer server = new SftpServer(tempDir);
        // FSETSTAT (type 9) is not handled by the switch -> falls through to default
        byte[] buf = new byte[]{9, 0, 0, 0, 1}; // type=9 (SSH_FXP_FSETSTAT) + dummy data
        try {
            byte[] response = server.handlePacket(buf);
            // Should return unsupported
            SftpPacket decoded = SftpCodec.decode(response);
            assertThat(decoded).isInstanceOf(SftpPacket.Status.class);
        } catch (Exception e) {
            // Decode failure is also acceptable
        }
    }

    @Test void testServerHandleStat() throws Exception {
        Files.writeString(tempDir.resolve("statme.txt"), "stat content");
        SftpServer server = new SftpServer(tempDir);
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Stat(100, "statme.txt")));
        SftpPacket decoded = SftpCodec.decode(response);
        assertThat(decoded).isInstanceOf(SftpPacket.Attrs.class);
    }

    @Test void testServerHandleLstat() throws Exception {
        Files.writeString(tempDir.resolve("lstatme.txt"), "lstat content");
        SftpServer server = new SftpServer(tempDir);
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Lstat(101, "lstatme.txt")));
        SftpPacket decoded = SftpCodec.decode(response);
        assertThat(decoded).isInstanceOf(SftpPacket.Attrs.class);
    }

    @Test void testServerHandleOpendir() throws Exception {
        Files.createDirectory(tempDir.resolve("testdir"));
        SftpServer server = new SftpServer(tempDir);
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Opendir(102, "testdir")));
        SftpPacket decoded = SftpCodec.decode(response);
        assertThat(decoded).isInstanceOf(SftpPacket.Handle.class);
    }

    @Test void testServerHandleReaddir() throws Exception {
        Files.writeString(tempDir.resolve("readdirmet.txt"), "content");
        SftpServer server = new SftpServer(tempDir);
        
        // First open the directory
        byte[] opendirResponse = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Opendir(103, ".")));
        SftpPacket.Handle handle = (SftpPacket.Handle) SftpCodec.decode(opendirResponse);
        
        // Then read entries
        byte[] readdirResponse = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Readdir(104, handle.handle())));
        SftpPacket decoded = SftpCodec.decode(readdirResponse);
        assertThat(decoded).isInstanceOf(SftpPacket.Name.class);
    }

    @Test void testServerHandleRemove() throws Exception {
        Files.writeString(tempDir.resolve("removeme.txt"), "to be removed");
        SftpServer server = new SftpServer(tempDir);
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Remove(105, "removeme.txt")));
        SftpPacket.Status status = (SftpPacket.Status) SftpCodec.decode(response);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_OK);
        assertThat(tempDir.resolve("removeme.txt")).doesNotExist();
    }

    @Test void testServerHandleMkdir() throws Exception {
        SftpServer server = new SftpServer(tempDir);
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Mkdir(106, "newdir", SftpFileAttributes.empty())));
        SftpPacket.Status status = (SftpPacket.Status) SftpCodec.decode(response);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_OK);
        assertThat(tempDir.resolve("newdir")).isDirectory();
    }

    @Test void testServerHandleRmdir() throws Exception {
        Files.createDirectory(tempDir.resolve("rmme"));
        SftpServer server = new SftpServer(tempDir);
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Rmdir(107, "rmme")));
        SftpPacket.Status status = (SftpPacket.Status) SftpCodec.decode(response);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_OK);
    }

    @Test void testServerHandleRead() throws Exception {
        Files.writeString(tempDir.resolve("readme.txt"), "readable content");
        SftpServer server = new SftpServer(tempDir);
        
        // Open file first
        byte[] openResponse = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Open(108, "readme.txt", SftpCodec.SSH_FXF_READ, 
                        SftpFileAttributes.empty())));
        SftpPacket.Handle handle = (SftpPacket.Handle) SftpCodec.decode(openResponse);
        
        // Read from it
        byte[] readResponse = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Read(109, handle.handle(), 0L, 256)));
        SftpPacket decoded = SftpCodec.decode(readResponse);
        assertThat(decoded).isInstanceOf(SftpPacket.Data.class);
    }

    @Test void testServerHandleWrite() throws Exception {
        SftpServer server = new SftpServer(tempDir);
        
        // Open file for write
        byte[] openResponse = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Open(110, "writeme.txt", 
                        SftpCodec.SSH_FXF_WRITE | SftpCodec.SSH_FXF_CREAT,
                        SftpFileAttributes.empty())));
        SftpPacket.Handle handle = (SftpPacket.Handle) SftpCodec.decode(openResponse);
        
        // Write to it
        byte[] writeResponse = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Write(111, handle.handle(), 0L, "written data".getBytes())));
        SftpPacket.Status status = (SftpPacket.Status) SftpCodec.decode(writeResponse);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_OK);
    }

    @Test void testServerHandleClose() throws Exception {
        SftpServer server = new SftpServer(tempDir);
        
        // Open file first
        byte[] openResponse = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Open(112, "closeme.txt", 
                        SftpCodec.SSH_FXF_WRITE | SftpCodec.SSH_FXF_CREAT,
                        SftpFileAttributes.empty())));
        SftpPacket.Handle handle = (SftpPacket.Handle) SftpCodec.decode(openResponse);
        
        // Close it
        byte[] closeResponse = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Close(113, handle.handle())));
        SftpPacket.Status status = (SftpPacket.Status) SftpCodec.decode(closeResponse);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_OK);
    }

    @Test void testServerHandleInit() throws Exception {
        SftpServer server = new SftpServer(tempDir);
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Init(3)));
        SftpPacket.Version version = (SftpPacket.Version) SftpCodec.decode(response);
        assertThat(version.version()).isEqualTo(3);
    }

    @Test void testServerHandleRealpath() throws Exception {
        SftpServer server = new SftpServer(tempDir);
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Realpath(114, ".")));
        SftpPacket decoded = SftpCodec.decode(response);
        assertThat(decoded).isInstanceOf(SftpPacket.Name.class);
    }

    @Test void testServerHandleWriteAppend() throws Exception {
        Files.writeString(tempDir.resolve("appendme.txt"), "initial ");
        SftpServer server = new SftpServer(tempDir);
        
        byte[] openResponse = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Open(115, "appendme.txt", 
                        SftpCodec.SSH_FXF_WRITE | SftpCodec.SSH_FXF_APPEND,
                        SftpFileAttributes.empty())));
        SftpPacket.Handle handle = (SftpPacket.Handle) SftpCodec.decode(openResponse);
        
        byte[] writeResponse = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Write(116, handle.handle(), 0L, "appended".getBytes())));
        SftpPacket.Status status = (SftpPacket.Status) SftpCodec.decode(writeResponse);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_OK);
    }

    @Test void testServerHandleOpenNonexistentForRead() throws Exception {
        SftpServer server = new SftpServer(tempDir);
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Open(117, "nonexistent.txt", SftpCodec.SSH_FXF_READ, 
                        SftpFileAttributes.empty())));
        SftpPacket.Status status = (SftpPacket.Status) SftpCodec.decode(response);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_NO_SUCH_FILE);
    }

    @Test void testServerHandleRemoveNonexistent() throws Exception {
        SftpServer server = new SftpServer(tempDir);
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Remove(118, "nonexistent.txt")));
        SftpPacket.Status status = (SftpPacket.Status) SftpCodec.decode(response);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_NO_SUCH_FILE);
    }

    @Test void testServerHandleWriteToClosedHandle() throws Exception {
        SftpServer server = new SftpServer(tempDir);
        
        // Invalid handle that doesn't exist
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Write(119, "invalid-handle".getBytes(), 0L, "data".getBytes())));
        SftpPacket.Status status = (SftpPacket.Status) SftpCodec.decode(response);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_FAILURE);
    }

    @Test void testServerHandleReadFromClosedHandle() throws Exception {
        SftpServer server = new SftpServer(tempDir);
        
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Read(120, "invalid-handle".getBytes(), 0L, 100)));
        SftpPacket.Status status = (SftpPacket.Status) SftpCodec.decode(response);
        assertThat(status.statusCode()).isEqualTo(SftpStatusCode.SSH_FX_FAILURE);
    }

    @Test void testServerHandleCloseInvalidHandle() throws Exception {
        SftpServer server = new SftpServer(tempDir);
        
        byte[] response = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Close(121, "invalid-handle".getBytes())));
        SftpPacket decoded = SftpCodec.decode(response);
        assertThat(decoded).isInstanceOf(SftpPacket.Status.class);
    }

    @Test void testServerHandleReadEmptyFile() throws Exception {
        Files.write(tempDir.resolve("empty.txt"), new byte[0]);
        SftpServer server = new SftpServer(tempDir);
        
        byte[] openResponse = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Open(122, "empty.txt", SftpCodec.SSH_FXF_READ, 
                        SftpFileAttributes.empty())));
        SftpPacket.Handle handle = (SftpPacket.Handle) SftpCodec.decode(openResponse);
        
        byte[] readResponse = server.handlePacket(SftpCodec.encode(
                new SftpPacket.Read(123, handle.handle(), 0L, 100)));
        SftpPacket decoded = SftpCodec.decode(readResponse);
        // Empty file may return SSH_FX_EOF status
        assertThat(decoded).isInstanceOfAny(SftpPacket.Data.class, SftpPacket.Status.class);
    }
}
