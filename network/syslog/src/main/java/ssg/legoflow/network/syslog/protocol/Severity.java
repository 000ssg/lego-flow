package ssg.legoflow.network.syslog.protocol;

/**
 * Syslog severity levels as defined in RFC 5424 Section 6.2.1.
 *
 * <p>Severity values range from 0 (Emergency) to 7 (Debug), with
 * lower values indicating higher severity.
 *
 * @since 1.0.0
 */
public enum Severity {

    /** System is unusable (0). */
    EMERGENCY(0),
    /** Action must be taken immediately (1). */
    ALERT(1),
    /** Critical conditions (2). */
    CRITICAL(2),
    /** Error conditions (3). */
    ERROR(3),
    /** Warning conditions (4). */
    WARNING(4),
    /** Normal but significant condition (5). */
    NOTICE(5),
    /** Informational messages (6). */
    INFO(6),
    /** Debug-level messages (7). */
    DEBUG(7);

    private final int code;

    Severity(int code) {
        this.code = code;
    }

    /**
     * Returns the numeric severity code (0-7).
     *
     * @return the severity code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the severity for the given numeric code.
     *
     * @param code the severity code (0-7)
     * @return the corresponding severity
     * @throws IllegalArgumentException if the code is invalid
     */
    public static Severity of(int code) {
        for (Severity s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("Invalid severity code: " + code);
    }
}
