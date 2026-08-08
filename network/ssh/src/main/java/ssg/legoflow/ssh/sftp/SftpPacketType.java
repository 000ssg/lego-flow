package ssg.legoflow.ssh.sftp;

/**
 * SFTP packet type constants per draft-ietf-secsh-filexfer-02.
 *
 * @since 0.1.0
 */
public enum SftpPacketType {

    SSH_FXP_INIT(1),
    SSH_FXP_VERSION(2),
    SSH_FXP_OPEN(3),
    SSH_FXP_CLOSE(4),
    SSH_FXP_READ(5),
    SSH_FXP_WRITE(6),
    SSH_FXP_LSTAT(7),
    SSH_FXP_FSTAT(8),
    SSH_FXP_SETSTAT(9),
    SSH_FXP_FSETSTAT(10),
    SSH_FXP_OPENDIR(11),
    SSH_FXP_READDIR(12),
    SSH_FXP_REMOVE(13),
    SSH_FXP_MKDIR(14),
    SSH_FXP_RMDIR(15),
    SSH_FXP_REALPATH(16),
    SSH_FXP_STAT(17),
    SSH_FXP_RENAME(18),
    SSH_FXP_READLINK(19),
    SSH_FXP_SYMLINK(20),
    SSH_FXP_STATUS(101),
    SSH_FXP_HANDLE(102),
    SSH_FXP_DATA(103),
    SSH_FXP_NAME(104),
    SSH_FXP_ATTRS(105),
    SSH_FXP_EXTENDED(200),
    SSH_FXP_EXTENDED_REPLY(201);

    private final int code;

    SftpPacketType(int code) {
        this.code = code;
    }

    /**
     * Returns the numeric code.
     *
     * @return the packet type code
     */
    public int code() { return code; }

    /**
     * Resolves a packet type from its code.
     *
     * @param code the numeric code
     * @return the packet type
     * @throws IllegalArgumentException if not found
     */
    public static SftpPacketType fromCode(int code) {
        for (SftpPacketType type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("Unknown SFTP packet type: " + code);
    }
}
