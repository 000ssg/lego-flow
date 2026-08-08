package ssg.legoflow.email.common.mime;

/**
 * Common multipart subtypes per RFC 2046 and related RFCs.
 *
 * @since 0.1.0
 */
public enum MultipartType {

    /** Mixed content — independent parts (RFC 2046 section 5.1.3). */
    MIXED("mixed"),

    /** Alternative representations of the same content (RFC 2046 section 5.1.4). */
    ALTERNATIVE("alternative"),

    /** Related parts with a root document (RFC 2387). */
    RELATED("related"),

    /** Digest — each part defaults to message/rfc822 (RFC 2046 section 5.1.5). */
    DIGEST("digest"),

    /** Delivery status or disposition notification (RFC 3462). */
    REPORT("report"),

    /** Signed content (RFC 1847). */
    SIGNED("signed");

    private final String subtype;

    MultipartType(String subtype) {
        this.subtype = subtype;
    }

    /**
     * Returns the subtype value for use in Content-Type.
     *
     * @return the subtype (e.g., "mixed")
     */
    public String subtype() {
        return subtype;
    }

    /**
     * Returns the full Content-Type value.
     *
     * @return "multipart/" followed by the subtype
     */
    public String contentType() {
        return "multipart/" + subtype;
    }

    /**
     * Parses a multipart subtype string.
     *
     * @param subtype the subtype (case-insensitive)
     * @return the matching enum constant
     * @throws IllegalArgumentException if the subtype is not recognized
     */
    public static MultipartType parse(String subtype) {
        if (subtype == null) {
            throw new IllegalArgumentException("Subtype must not be null");
        }
        String normalized = subtype.trim().toLowerCase();
        for (MultipartType type : values()) {
            if (type.subtype.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown multipart subtype: " + subtype);
    }

    /**
     * Attempts to parse a multipart subtype, returning null if not recognized.
     *
     * @param subtype the subtype string
     * @return the matching enum constant, or null
     */
    public static MultipartType tryParse(String subtype) {
        try {
            return parse(subtype);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return subtype;
    }
}
