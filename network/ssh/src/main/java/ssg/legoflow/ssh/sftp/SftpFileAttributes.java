package ssg.legoflow.ssh.sftp;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * SFTP file attributes per draft-ietf-secsh-filexfer-02.
 *
 * @since 1.0.0
 */
public final class SftpFileAttributes {

    /** Attribute flags. */
    public static final int SSH_FILEXFER_ATTR_SIZE = 0x00000001;
    public static final int SSH_FILEXFER_ATTR_UIDGID = 0x00000002;
    public static final int SSH_FILEXFER_ATTR_PERMISSIONS = 0x00000004;
    public static final int SSH_FILEXFER_ATTR_ACMODTIME = 0x00000008;

    private final int flags;
    private final long size;
    private final int uid;
    private final int gid;
    private final int permissions;
    private final long atime;
    private final long mtime;

    /**
     * Creates file attributes.
     *
     * @param flags       attribute flags
     * @param size        file size
     * @param uid         user ID
     * @param gid         group ID
     * @param permissions file permissions (Unix mode)
     * @param atime       access time (Unix epoch)
     * @param mtime       modification time (Unix epoch)
     */
    public SftpFileAttributes(int flags, long size, int uid, int gid,
                              int permissions, long atime, long mtime) {
        this.flags = flags;
        this.size = size;
        this.uid = uid;
        this.gid = gid;
        this.permissions = permissions;
        this.atime = atime;
        this.mtime = mtime;
    }

    /** @return attribute flags */
    public int flags() { return flags; }
    /** @return file size */
    public long size() { return size; }
    /** @return user ID */
    public int uid() { return uid; }
    /** @return group ID */
    public int gid() { return gid; }
    /** @return file permissions */
    public int permissions() { return permissions; }
    /** @return access time */
    public long atime() { return atime; }
    /** @return modification time */
    public long mtime() { return mtime; }

    /** @return true if this is a directory */
    public boolean isDirectory() { return (permissions & 0040000) != 0; }
    /** @return true if this is a regular file */
    public boolean isRegularFile() { return (permissions & 0100000) != 0; }
    /** @return true if this is a symbolic link */
    public boolean isSymlink() { return (permissions & 0120000) != 0; }

    /**
     * Encodes these attributes to wire format.
     *
     * @return the encoded bytes
     */
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(64);
        buf.putInt(flags);
        if ((flags & SSH_FILEXFER_ATTR_SIZE) != 0) {
            buf.putLong(size);
        }
        if ((flags & SSH_FILEXFER_ATTR_UIDGID) != 0) {
            buf.putInt(uid);
            buf.putInt(gid);
        }
        if ((flags & SSH_FILEXFER_ATTR_PERMISSIONS) != 0) {
            buf.putInt(permissions);
        }
        if ((flags & SSH_FILEXFER_ATTR_ACMODTIME) != 0) {
            buf.putInt((int) atime);
            buf.putInt((int) mtime);
        }
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Decodes attributes from wire format.
     *
     * @param buf the buffer to read from
     * @return the decoded attributes
     */
    public static SftpFileAttributes decode(ByteBuffer buf) {
        int flags = buf.getInt();
        long size = 0;
        int uid = 0, gid = 0, permissions = 0;
        long atime = 0, mtime = 0;

        if ((flags & SSH_FILEXFER_ATTR_SIZE) != 0) {
            size = buf.getLong();
        }
        if ((flags & SSH_FILEXFER_ATTR_UIDGID) != 0) {
            uid = buf.getInt();
            gid = buf.getInt();
        }
        if ((flags & SSH_FILEXFER_ATTR_PERMISSIONS) != 0) {
            permissions = buf.getInt();
        }
        if ((flags & SSH_FILEXFER_ATTR_ACMODTIME) != 0) {
            atime = buf.getInt() & 0xFFFFFFFFL;
            mtime = buf.getInt() & 0xFFFFFFFFL;
        }
        return new SftpFileAttributes(flags, size, uid, gid, permissions, atime, mtime);
    }

    /**
     * Creates empty attributes (no flags set).
     *
     * @return empty attributes
     */
    public static SftpFileAttributes empty() {
        return new SftpFileAttributes(0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public String toString() {
        return String.format("SftpFileAttributes{size=%d, perms=%o, uid=%d, gid=%d}",
                size, permissions, uid, gid);
    }
}
