package ssg.legoflow.ssh.sftp;

/**
 * SFTP status codes per draft-ietf-secsh-filexfer-02.
 *
 * @since 0.1.0
 */
public enum SftpStatusCode {

    SSH_FX_OK(0, "Success"),
    SSH_FX_EOF(1, "End of file"),
    SSH_FX_NO_SUCH_FILE(2, "No such file"),
    SSH_FX_PERMISSION_DENIED(3, "Permission denied"),
    SSH_FX_FAILURE(4, "Failure"),
    SSH_FX_BAD_MESSAGE(5, "Bad message"),
    SSH_FX_NO_CONNECTION(6, "No connection"),
    SSH_FX_CONNECTION_LOST(7, "Connection lost"),
    SSH_FX_OP_UNSUPPORTED(8, "Operation unsupported");

    private final int code;
    private final String description;

    SftpStatusCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /** @return the numeric code */
    public int code() { return code; }
    /** @return the description */
    public String description() { return description; }

    /**
     * Resolves a status code from its numeric value.
     *
     * @param code the numeric code
     * @return the status code
     */
    public static SftpStatusCode fromCode(int code) {
        for (SftpStatusCode sc : values()) {
            if (sc.code == code) return sc;
        }
        throw new IllegalArgumentException("Unknown SFTP status code: " + code);
    }
}
