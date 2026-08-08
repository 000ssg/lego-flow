package ssg.legoflow.ftp.protocol;

/**
 * FTP transfer mode as defined in RFC 959 Section 3.4.
 *
 * <p>The MODE command sets how data is transmitted over the data connection.
 *
 * @since 0.1.0
 */
public enum FtpTransferMode {

    /** Stream mode — data is a continuous stream; EOF signalled by closing connection. */
    STREAM("S"),

    /** Block mode — data is sent in blocks with headers. */
    BLOCK("B"),

    /** Compressed mode — data is compressed using run-length encoding. */
    COMPRESSED("C");

    private final String code;

    FtpTransferMode(String code) {
        this.code = code;
    }

    /**
     * Returns the single-character mode code.
     *
     * @return the code ("S", "B", or "C")
     */
    public String code() {
        return code;
    }

    /**
     * Parses a mode code from a MODE command argument.
     *
     * @param code the mode code (case-insensitive)
     * @return the matching transfer mode
     * @throws IllegalArgumentException if the code is not recognized
     */
    public static FtpTransferMode fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Mode code must not be null or blank");
        }
        return switch (code.trim().toUpperCase()) {
            case "S" -> STREAM;
            case "B" -> BLOCK;
            case "C" -> COMPRESSED;
            default -> throw new IllegalArgumentException("Unknown transfer mode code: " + code);
        };
    }
}
