package ssg.legoflow.network.syslog.protocol;

/**
 * Syslog facility codes as defined in RFC 5424 Section 6.2.1.
 *
 * <p>The facility value indicates the subsystem that generated the message.
 * Facility codes 0-15 are well-known; 16-23 are reserved for local use.
 *
 * @since 0.1.0
 */
public enum Facility {

    /** Kernel messages (0). */
    KERN(0),
    /** User-level messages (1). */
    USER(1),
    /** Mail system (2). */
    MAIL(2),
    /** System daemons (3). */
    DAEMON(3),
    /** Security/authorization messages (4). */
    AUTH(4),
    /** Messages generated internally by syslogd (5). */
    SYSLOG(5),
    /** Line printer subsystem (6). */
    LPR(6),
    /** Network news subsystem (7). */
    NEWS(7),
    /** UUCP subsystem (8). */
    UUCP(8),
    /** Clock daemon (9). */
    CRON(9),
    /** Security/authorization messages — private (10). */
    AUTHPRIV(10),
    /** FTP daemon (11). */
    FTP(11),
    /** NTP subsystem (12). */
    NTP(12),
    /** Log audit (13). */
    AUDIT(13),
    /** Log alert (14). */
    ALERT(14),
    /** Clock daemon (15). */
    CLOCK(15),
    /** Local use 0 (16). */
    LOCAL0(16),
    /** Local use 1 (17). */
    LOCAL1(17),
    /** Local use 2 (18). */
    LOCAL2(18),
    /** Local use 3 (19). */
    LOCAL3(19),
    /** Local use 4 (20). */
    LOCAL4(20),
    /** Local use 5 (21). */
    LOCAL5(21),
    /** Local use 6 (22). */
    LOCAL6(22),
    /** Local use 7 (23). */
    LOCAL7(23);

    private final int code;

    Facility(int code) {
        this.code = code;
    }

    /**
     * Returns the numeric facility code (0-23).
     *
     * @return the facility code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the facility for the given numeric code.
     *
     * @param code the facility code (0-23)
     * @return the corresponding facility
     * @throws IllegalArgumentException if the code is invalid
     */
    public static Facility of(int code) {
        for (Facility f : values()) {
            if (f.code == code) {
                return f;
            }
        }
        throw new IllegalArgumentException("Invalid facility code: " + code);
    }
}
