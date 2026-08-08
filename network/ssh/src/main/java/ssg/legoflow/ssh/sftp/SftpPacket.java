package ssg.legoflow.ssh.sftp;

/**
 * Base sealed interface for all SFTP packets.
 *
 * @since 0.1.0
 */
public sealed interface SftpPacket permits
        SftpPacket.Init, SftpPacket.Version,
        SftpPacket.Open, SftpPacket.Close, SftpPacket.Read, SftpPacket.Write,
        SftpPacket.Lstat, SftpPacket.Fstat, SftpPacket.Setstat, SftpPacket.Fsetstat,
        SftpPacket.Opendir, SftpPacket.Readdir, SftpPacket.Remove, SftpPacket.Mkdir,
        SftpPacket.Rmdir, SftpPacket.Realpath, SftpPacket.Stat, SftpPacket.Rename,
        SftpPacket.Readlink, SftpPacket.Symlink,
        SftpPacket.Status, SftpPacket.Handle, SftpPacket.Data, SftpPacket.Name,
        SftpPacket.Attrs, SftpPacket.Extended, SftpPacket.ExtendedReply {

    /** @return the SFTP packet type */
    SftpPacketType type();

    record Init(int version) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_INIT; }
    }
    record Version(int version) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_VERSION; }
    }
    record Open(int id, String filename, int pflags, SftpFileAttributes attrs) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_OPEN; }
    }
    record Close(int id, byte[] handle) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_CLOSE; }
    }
    record Read(int id, byte[] handle, long offset, int length) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_READ; }
    }
    record Write(int id, byte[] handle, long offset, byte[] data) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_WRITE; }
    }
    record Lstat(int id, String path) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_LSTAT; }
    }
    record Fstat(int id, byte[] handle) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_FSTAT; }
    }
    record Setstat(int id, String path, SftpFileAttributes attrs) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_SETSTAT; }
    }
    record Fsetstat(int id, byte[] handle, SftpFileAttributes attrs) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_FSETSTAT; }
    }
    record Opendir(int id, String path) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_OPENDIR; }
    }
    record Readdir(int id, byte[] handle) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_READDIR; }
    }
    record Remove(int id, String filename) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_REMOVE; }
    }
    record Mkdir(int id, String path, SftpFileAttributes attrs) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_MKDIR; }
    }
    record Rmdir(int id, String path) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_RMDIR; }
    }
    record Realpath(int id, String path) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_REALPATH; }
    }
    record Stat(int id, String path) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_STAT; }
    }
    record Rename(int id, String oldPath, String newPath) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_RENAME; }
    }
    record Readlink(int id, String path) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_READLINK; }
    }
    record Symlink(int id, String linkPath, String targetPath) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_SYMLINK; }
    }
    record Status(int id, SftpStatusCode statusCode, String message, String language) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_STATUS; }
    }
    record Handle(int id, byte[] handle) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_HANDLE; }
    }
    record Data(int id, byte[] data) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_DATA; }
    }
    record Name(int id, java.util.List<NameEntry> entries) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_NAME; }
    }
    record Attrs(int id, SftpFileAttributes attrs) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_ATTRS; }
    }

    /**
     * An extended request (SSH_FXP_EXTENDED).
     *
     * @param id            the request ID
     * @param extendedRequest the extension name (e.g. "posix-rename@openssh.com")
     * @param data          the extension-specific data
     */
    record Extended(int id, String extendedRequest, byte[] data) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_EXTENDED; }
    }

    /**
     * An extended reply (SSH_FXP_EXTENDED_REPLY).
     *
     * @param id   the request ID
     * @param data the extension-specific reply data
     */
    record ExtendedReply(int id, byte[] data) implements SftpPacket {
        @Override public SftpPacketType type() { return SftpPacketType.SSH_FXP_EXTENDED_REPLY; }
    }

    /**
     * A directory entry in an SSH_FXP_NAME response.
     *
     * @param filename the file name
     * @param longname the long name (ls -l style)
     * @param attrs    the file attributes
     */
    record NameEntry(String filename, String longname, SftpFileAttributes attrs) {}
}
