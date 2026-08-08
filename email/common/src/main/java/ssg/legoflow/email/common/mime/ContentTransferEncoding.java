package ssg.legoflow.email.common.mime;

/**
 * Content-Transfer-Encoding values per RFC 2045 section 6.
 *
 * @since 0.1.0
 */
public enum ContentTransferEncoding {

    /** 7-bit ASCII data, lines limited to 998 octets (RFC 2045 default). */
    SEVEN_BIT("7bit"),

    /** 8-bit data, lines limited to 998 octets. */
    EIGHT_BIT("8bit"),

    /** Arbitrary binary data, no line length restriction. */
    BINARY("binary"),

    /** Quoted-Printable encoding (RFC 2045 section 6.7). */
    QUOTED_PRINTABLE("quoted-printable"),

    /** Base64 encoding (RFC 2045 section 6.8). */
    BASE64("base64");

    private final String value;

    ContentTransferEncoding(String value) {
        this.value = value;
    }

    /**
     * Returns the wire format value.
     *
     * @return the encoding name as used in headers
     */
    public String value() {
        return value;
    }

    /**
     * Parses a Content-Transfer-Encoding header value.
     *
     * @param value the header value (case-insensitive)
     * @return the matching enum constant
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static ContentTransferEncoding parse(String value) {
        if (value == null || value.isBlank()) {
            return SEVEN_BIT;
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "7bit" -> SEVEN_BIT;
            case "8bit" -> EIGHT_BIT;
            case "binary" -> BINARY;
            case "quoted-printable" -> QUOTED_PRINTABLE;
            case "base64" -> BASE64;
            default -> throw new IllegalArgumentException(
                    "Unknown Content-Transfer-Encoding: " + value);
        };
    }

    @Override
    public String toString() {
        return value;
    }
}
