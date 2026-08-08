package ssg.legoflow.ftp.client;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a parsed directory listing entry.
 *
 * <p>Contains file metadata extracted from LIST or MLSD output.
 *
 * @since 0.1.0
 */
public final class FtpFileEntry {

    /**
     * File type indicator.
     */
    public enum Type {
        /** Regular file. */
        FILE,
        /** Directory. */
        DIRECTORY,
        /** Symbolic link. */
        SYMLINK,
        /** Unknown type. */
        UNKNOWN
    }

    private final String name;
    private final long size;
    private final LocalDateTime modified;
    private final Type type;
    private final String permissions;
    private final String owner;
    private final String group;
    private final int linkCount;
    private final String rawLine;

    /**
     * Creates a file entry.
     *
     * @param name        the file name
     * @param size        the file size in bytes
     * @param modified    the last modification time
     * @param type        the file type
     * @param permissions the permission string (e.g., "rwxr-xr-x")
     * @param owner       the file owner
     * @param group       the file group
     * @param linkCount   the number of hard links
     * @param rawLine     the original listing line
     */
    public FtpFileEntry(String name, long size, LocalDateTime modified, Type type,
                        String permissions, String owner, String group, int linkCount,
                        String rawLine) {
        this.name = Objects.requireNonNull(name, "name");
        this.size = size;
        this.modified = modified;
        this.type = type != null ? type : Type.UNKNOWN;
        this.permissions = permissions;
        this.owner = owner;
        this.group = group;
        this.linkCount = linkCount;
        this.rawLine = rawLine;
    }

    /**
     * Creates a simple file entry with minimal metadata.
     *
     * @param name     the file name
     * @param size     the file size
     * @param modified the modification time
     * @param type     the type
     * @return a new file entry
     */
    public static FtpFileEntry of(String name, long size, LocalDateTime modified, Type type) {
        return new FtpFileEntry(name, size, modified, type, null, null, null, 0, null);
    }

    /** Returns the file name. */
    public String name() { return name; }
    /** Returns the file size in bytes. */
    public long size() { return size; }
    /** Returns the last modification time, or {@code null} if unknown. */
    public LocalDateTime modified() { return modified; }
    /** Returns the file type. */
    public Type type() { return type; }
    /** Returns the permission string, or {@code null}. */
    public String permissions() { return permissions; }
    /** Returns the owner name, or {@code null}. */
    public String owner() { return owner; }
    /** Returns the group name, or {@code null}. */
    public String group() { return group; }
    /** Returns the hard link count. */
    public int linkCount() { return linkCount; }
    /** Returns the original listing line, or {@code null}. */
    public String rawLine() { return rawLine; }

    /** Returns {@code true} if this entry is a directory. */
    public boolean isDirectory() { return type == Type.DIRECTORY; }
    /** Returns {@code true} if this entry is a regular file. */
    public boolean isFile() { return type == Type.FILE; }
    /** Returns {@code true} if this entry is a symbolic link. */
    public boolean isSymlink() { return type == Type.SYMLINK; }

    @Override
    public String toString() {
        return String.format("FtpFileEntry[name=%s, size=%d, type=%s]", name, size, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FtpFileEntry other)) return false;
        return name.equals(other.name) && size == other.size && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, size, type);
    }
}
