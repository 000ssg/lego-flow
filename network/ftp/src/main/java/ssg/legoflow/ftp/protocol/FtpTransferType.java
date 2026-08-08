package ssg.legoflow.ftp.protocol;

/**
 * FTP representation type for data transfers (RFC 959 Section 3.1.1).
 *
 * <p>The TYPE command sets the transfer type, which determines how data
 * is encoded on the wire.
 *
 * @since 0.1.0
 */
public enum FtpTransferType {

    /** ASCII type — text mode with CRLF line endings on the wire. */
    ASCII("A"),

    /** IMAGE (binary) type — byte-for-byte transfer, no conversion. */
    BINARY("I"),

    /** EBCDIC type — for EBCDIC host transfers. */
    EBCDIC("E");

    private final String typeCode;

    FtpTransferType(String typeCode) {
        this.typeCode = typeCode;
    }

    /**
     * Returns the single-character type code used in the TYPE command.
     *
     * @return the type code ("A", "I", or "E")
     */
    public String typeCode() {
        return typeCode;
    }

    /**
     * Parses a type code from a TYPE command argument.
     *
     * @param code the type code (case-insensitive)
     * @return the matching transfer type
     * @throws IllegalArgumentException if the code is not recognized
     */
    public static FtpTransferType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Type code must not be null or blank");
        }
        return switch (code.trim().toUpperCase()) {
            case "A" -> ASCII;
            case "I" -> BINARY;
            case "E" -> EBCDIC;
            default -> throw new IllegalArgumentException("Unknown transfer type code: " + code);
        };
    }
}
