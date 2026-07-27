package ssg.legoflow.ftp.protocol;

/**
 * FTP file structure as defined in RFC 959 Section 3.1.2.
 *
 * <p>The STRU command sets how the data is structured within a file.
 *
 * @since 1.0.0
 */
public enum FtpStructure {

    /** File structure — no internal structure, continuous byte stream. */
    FILE("F"),

    /** Record structure — file consists of sequential records. */
    RECORD("R"),

    /** Page structure — file consists of independent indexed pages. */
    PAGE("P");

    private final String code;

    FtpStructure(String code) {
        this.code = code;
    }

    /**
     * Returns the single-character structure code.
     *
     * @return the code ("F", "R", or "P")
     */
    public String code() {
        return code;
    }

    /**
     * Parses a structure code from a STRU command argument.
     *
     * @param code the structure code (case-insensitive)
     * @return the matching structure
     * @throws IllegalArgumentException if the code is not recognized
     */
    public static FtpStructure fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Structure code must not be null or blank");
        }
        return switch (code.trim().toUpperCase()) {
            case "F" -> FILE;
            case "R" -> RECORD;
            case "P" -> PAGE;
            default -> throw new IllegalArgumentException("Unknown structure code: " + code);
        };
    }
}
