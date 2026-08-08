package ssg.legoflow.database.postgresql.common;

/**
 * PostgreSQL error/notice severity levels.
 *
 * @since 0.1.0
 */
public enum PgSeverity {

    /** Fatal error that terminates the connection. */
    FATAL,

    /** Unrecoverable error. */
    PANIC,

    /** Recoverable error. */
    ERROR,

    /** Warning message. */
    WARNING,

    /** Informational notice. */
    NOTICE,

    /** Debug message. */
    DEBUG,

    /** Informational message. */
    INFO,

    /** Log message. */
    LOG;

    /**
     * Returns the severity label as used in the wire protocol.
     *
     * @return the severity string
     */
    public String label() {
        return name();
    }

    /**
     * Parses a severity string from the wire protocol.
     *
     * @param label the severity label
     * @return the matching severity, or {@link #ERROR} if not recognized
     */
    public static PgSeverity fromLabel(String label) {
        try {
            return valueOf(label.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ERROR;
        }
    }
}
