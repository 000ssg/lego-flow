package ssg.legoflow.database.mysql.common;

/**
 * MySQL charset/collation IDs.
 *
 * <p>Maps commonly used MySQL character sets and collations to their
 * numeric IDs as used in the protocol. The charset ID is sent in
 * handshake packets and column definitions.
 *
 * @since 1.0.0
 */
public enum Charset {

    /** latin1 with Swedish collation (default for older MySQL). */
    LATIN1_SWEDISH_CI(8, "latin1", "latin1_swedish_ci"),

    /** UTF-8 general collation (3-byte). */
    UTF8_GENERAL_CI(33, "utf8", "utf8_general_ci"),

    /** UTF-8 Unicode collation (3-byte). */
    UTF8_UNICODE_CI(192, "utf8", "utf8_unicode_ci"),

    /** UTF-8 binary collation (3-byte). */
    UTF8_BIN(83, "utf8", "utf8_bin"),

    /** utf8mb4 general collation (4-byte, default for MySQL 8.0). */
    UTF8MB4_GENERAL_CI(45, "utf8mb4", "utf8mb4_general_ci"),

    /** utf8mb4 Unicode 9.0 collation (MySQL 8.0 default). */
    UTF8MB4_0900_AI_CI(255, "utf8mb4", "utf8mb4_0900_ai_ci"),

    /** utf8mb4 binary collation. */
    UTF8MB4_BIN(46, "utf8mb4", "utf8mb4_bin"),

    /** Binary charset/collation. */
    BINARY(63, "binary", "binary"),

    /** ASCII charset. */
    ASCII_GENERAL_CI(11, "ascii", "ascii_general_ci");

    /** Default charset ID (utf8mb4_general_ci). */
    public static final int DEFAULT_CHARSET_ID = 45;

    private final int id;
    private final String charsetName;
    private final String collationName;

    Charset(int id, String charsetName, String collationName) {
        this.id = id;
        this.charsetName = charsetName;
        this.collationName = collationName;
    }

    /**
     * Returns the numeric charset/collation ID.
     *
     * @return the ID used in the protocol
     */
    public int id() {
        return id;
    }

    /**
     * Returns the charset name.
     *
     * @return the charset name
     */
    public String charsetName() {
        return charsetName;
    }

    /**
     * Returns the collation name.
     *
     * @return the collation name
     */
    public String collationName() {
        return collationName;
    }

    /**
     * Returns the Charset for the given ID.
     *
     * @param id the charset/collation ID
     * @return the matching Charset, or UTF8MB4_GENERAL_CI if not found
     */
    public static Charset fromId(int id) {
        for (var cs : values()) {
            if (cs.id == id) {
                return cs;
            }
        }
        return UTF8MB4_GENERAL_CI;
    }

    /**
     * Returns the Charset for the given charset name (first match).
     *
     * @param name the charset name
     * @return the matching Charset, or UTF8MB4_GENERAL_CI if not found
     */
    public static Charset fromName(String name) {
        for (var cs : values()) {
            if (cs.charsetName.equalsIgnoreCase(name)) {
                return cs;
            }
        }
        return UTF8MB4_GENERAL_CI;
    }
}
