package ssg.legoflow.ssh.sftp;

import ssg.legoflow.ssh.transport.SshTransportCodec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SFTP packet encoding and decoding.
 *
 * @since 1.0.0
 */
public final class SftpCodec {

    /** SFTP open flags. */
    public static final int SSH_FXF_READ = 0x00000001;
    public static final int SSH_FXF_WRITE = 0x00000002;
    public static final int SSH_FXF_APPEND = 0x00000004;
    public static final int SSH_FXF_CREAT = 0x00000008;
    public static final int SSH_FXF_TRUNC = 0x00000010;
    public static final int SSH_FXF_EXCL = 0x00000020;

    private SftpCodec() {}

    /**
     * Encodes an SFTP packet to wire format.
     *
     * @param packet the packet to encode
     * @return the encoded bytes (length + type + data)
     */
    public static byte[] encode(SftpPacket packet) {
        ByteBuffer buf = ByteBuffer.allocate(65536);
        buf.putInt(0); // placeholder for length

        return switch (packet) {
            case SftpPacket.Init p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.version());
                yield finish(buf);
            }
            case SftpPacket.Version p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.version());
                yield finish(buf);
            }
            case SftpPacket.Open p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.filename());
                buf.putInt(p.pflags());
                buf.put(p.attrs().encode());
                yield finish(buf);
            }
            case SftpPacket.Close p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeBinary(buf, p.handle());
                yield finish(buf);
            }
            case SftpPacket.Read p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeBinary(buf, p.handle());
                buf.putLong(p.offset());
                buf.putInt(p.length());
                yield finish(buf);
            }
            case SftpPacket.Write p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeBinary(buf, p.handle());
                buf.putLong(p.offset());
                SshTransportCodec.writeBinary(buf, p.data());
                yield finish(buf);
            }
            case SftpPacket.Stat p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.path());
                yield finish(buf);
            }
            case SftpPacket.Lstat p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.path());
                yield finish(buf);
            }
            case SftpPacket.Fstat p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeBinary(buf, p.handle());
                yield finish(buf);
            }
            case SftpPacket.Opendir p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.path());
                yield finish(buf);
            }
            case SftpPacket.Readdir p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeBinary(buf, p.handle());
                yield finish(buf);
            }
            case SftpPacket.Remove p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.filename());
                yield finish(buf);
            }
            case SftpPacket.Mkdir p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.path());
                buf.put(p.attrs().encode());
                yield finish(buf);
            }
            case SftpPacket.Rmdir p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.path());
                yield finish(buf);
            }
            case SftpPacket.Realpath p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.path());
                yield finish(buf);
            }
            case SftpPacket.Rename p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.oldPath());
                SshTransportCodec.writeString(buf, p.newPath());
                yield finish(buf);
            }
            case SftpPacket.Readlink p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.path());
                yield finish(buf);
            }
            case SftpPacket.Symlink p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.linkPath());
                SshTransportCodec.writeString(buf, p.targetPath());
                yield finish(buf);
            }
            case SftpPacket.Setstat p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.path());
                buf.put(p.attrs().encode());
                yield finish(buf);
            }
            case SftpPacket.Fsetstat p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeBinary(buf, p.handle());
                buf.put(p.attrs().encode());
                yield finish(buf);
            }
            case SftpPacket.Status p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                buf.putInt(p.statusCode().code());
                SshTransportCodec.writeString(buf, p.message());
                SshTransportCodec.writeString(buf, p.language());
                yield finish(buf);
            }
            case SftpPacket.Handle p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeBinary(buf, p.handle());
                yield finish(buf);
            }
            case SftpPacket.Data p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeBinary(buf, p.data());
                yield finish(buf);
            }
            case SftpPacket.Name p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                buf.putInt(p.entries().size());
                for (SftpPacket.NameEntry entry : p.entries()) {
                    SshTransportCodec.writeString(buf, entry.filename());
                    SshTransportCodec.writeString(buf, entry.longname());
                    buf.put(entry.attrs().encode());
                }
                yield finish(buf);
            }
            case SftpPacket.Attrs p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                buf.put(p.attrs().encode());
                yield finish(buf);
            }
            case SftpPacket.Extended p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                SshTransportCodec.writeString(buf, p.extendedRequest());
                if (p.data() != null && p.data().length > 0) {
                    buf.put(p.data());
                }
                yield finish(buf);
            }
            case SftpPacket.ExtendedReply p -> {
                buf.put((byte) p.type().code());
                buf.putInt(p.id());
                if (p.data() != null && p.data().length > 0) {
                    buf.put(p.data());
                }
                yield finish(buf);
            }
        };
    }

    /**
     * Decodes an SFTP packet from wire format.
     *
     * @param data the raw bytes (length + type + data)
     * @return the decoded packet
     */
    public static SftpPacket decode(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int length = buf.getInt();
        int typeCode = buf.get() & 0xFF;
        SftpPacketType type = SftpPacketType.fromCode(typeCode);

        return switch (type) {
            case SSH_FXP_INIT -> new SftpPacket.Init(buf.getInt());
            case SSH_FXP_VERSION -> new SftpPacket.Version(buf.getInt());
            case SSH_FXP_STATUS -> {
                int id = buf.getInt();
                int code = buf.getInt();
                String msg = SshTransportCodec.readString(buf);
                String lang = SshTransportCodec.readString(buf);
                yield new SftpPacket.Status(id, SftpStatusCode.fromCode(code), msg, lang);
            }
            case SSH_FXP_HANDLE -> {
                int id = buf.getInt();
                byte[] handle = SshTransportCodec.readBinary(buf);
                yield new SftpPacket.Handle(id, handle);
            }
            case SSH_FXP_DATA -> {
                int id = buf.getInt();
                byte[] d = SshTransportCodec.readBinary(buf);
                yield new SftpPacket.Data(id, d);
            }
            case SSH_FXP_NAME -> {
                int id = buf.getInt();
                int count = buf.getInt();
                List<SftpPacket.NameEntry> entries = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String filename = SshTransportCodec.readString(buf);
                    String longname = SshTransportCodec.readString(buf);
                    SftpFileAttributes attrs = SftpFileAttributes.decode(buf);
                    entries.add(new SftpPacket.NameEntry(filename, longname, attrs));
                }
                yield new SftpPacket.Name(id, entries);
            }
            case SSH_FXP_ATTRS -> {
                int id = buf.getInt();
                SftpFileAttributes attrs = SftpFileAttributes.decode(buf);
                yield new SftpPacket.Attrs(id, attrs);
            }
            case SSH_FXP_EXTENDED -> {
                int id = buf.getInt();
                String extRequest = SshTransportCodec.readString(buf);
                byte[] extData = new byte[buf.remaining()];
                buf.get(extData);
                yield new SftpPacket.Extended(id, extRequest, extData);
            }
            case SSH_FXP_EXTENDED_REPLY -> {
                int id = buf.getInt();
                byte[] replyData = new byte[buf.remaining()];
                buf.get(replyData);
                yield new SftpPacket.ExtendedReply(id, replyData);
            }
            // Request types (decoded by server)
            case SSH_FXP_OPEN -> {
                int id = buf.getInt();
                String filename = SshTransportCodec.readString(buf);
                int pflags = buf.getInt();
                SftpFileAttributes attrs = SftpFileAttributes.decode(buf);
                yield new SftpPacket.Open(id, filename, pflags, attrs);
            }
            case SSH_FXP_CLOSE -> {
                int id = buf.getInt();
                byte[] handle = SshTransportCodec.readBinary(buf);
                yield new SftpPacket.Close(id, handle);
            }
            case SSH_FXP_READ -> {
                int id = buf.getInt();
                byte[] handle = SshTransportCodec.readBinary(buf);
                long offset = buf.getLong();
                int len = buf.getInt();
                yield new SftpPacket.Read(id, handle, offset, len);
            }
            case SSH_FXP_WRITE -> {
                int id = buf.getInt();
                byte[] handle = SshTransportCodec.readBinary(buf);
                long offset = buf.getLong();
                byte[] d = SshTransportCodec.readBinary(buf);
                yield new SftpPacket.Write(id, handle, offset, d);
            }
            case SSH_FXP_LSTAT -> {
                int id = buf.getInt();
                String path = SshTransportCodec.readString(buf);
                yield new SftpPacket.Lstat(id, path);
            }
            case SSH_FXP_STAT -> {
                int id = buf.getInt();
                String path = SshTransportCodec.readString(buf);
                yield new SftpPacket.Stat(id, path);
            }
            case SSH_FXP_OPENDIR -> {
                int id = buf.getInt();
                String path = SshTransportCodec.readString(buf);
                yield new SftpPacket.Opendir(id, path);
            }
            case SSH_FXP_READDIR -> {
                int id = buf.getInt();
                byte[] handle = SshTransportCodec.readBinary(buf);
                yield new SftpPacket.Readdir(id, handle);
            }
            case SSH_FXP_REMOVE -> {
                int id = buf.getInt();
                String filename = SshTransportCodec.readString(buf);
                yield new SftpPacket.Remove(id, filename);
            }
            case SSH_FXP_MKDIR -> {
                int id = buf.getInt();
                String path = SshTransportCodec.readString(buf);
                SftpFileAttributes attrs = SftpFileAttributes.decode(buf);
                yield new SftpPacket.Mkdir(id, path, attrs);
            }
            case SSH_FXP_RMDIR -> {
                int id = buf.getInt();
                String path = SshTransportCodec.readString(buf);
                yield new SftpPacket.Rmdir(id, path);
            }
            case SSH_FXP_REALPATH -> {
                int id = buf.getInt();
                String path = SshTransportCodec.readString(buf);
                yield new SftpPacket.Realpath(id, path);
            }
            case SSH_FXP_RENAME -> {
                int id = buf.getInt();
                String oldPath = SshTransportCodec.readString(buf);
                String newPath = SshTransportCodec.readString(buf);
                yield new SftpPacket.Rename(id, oldPath, newPath);
            }
            default -> throw new IllegalArgumentException("Cannot decode SFTP packet type: " + type);
        };
    }

    private static byte[] finish(ByteBuffer buf) {
        int pos = buf.position();
        buf.putInt(0, pos - 4); // write length
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }
}
