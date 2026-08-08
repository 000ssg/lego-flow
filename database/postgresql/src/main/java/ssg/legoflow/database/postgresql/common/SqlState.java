package ssg.legoflow.database.postgresql.common;

/**
 * SQLSTATE error codes as defined by the SQL standard and PostgreSQL extensions.
 *
 * <p>Each code is a 5-character string: 2-character class + 3-character subclass.
 *
 * @since 0.1.0
 */
public enum SqlState {

    /** Successful completion. */
    SUCCESSFUL_COMPLETION("00000"),

    /** Warning. */
    WARNING("01000"),

    /** No data. */
    NO_DATA("02000"),

    /** Connection exception. */
    CONNECTION_EXCEPTION("08000"),

    /** Connection does not exist. */
    CONNECTION_DOES_NOT_EXIST("08003"),

    /** Connection failure. */
    CONNECTION_FAILURE("08006"),

    /** Feature not supported. */
    FEATURE_NOT_SUPPORTED("0A000"),

    /** Invalid cursor state. */
    INVALID_CURSOR_STATE("24000"),

    /** Invalid transaction state. */
    INVALID_TRANSACTION_STATE("25000"),

    /** Invalid SQL statement name. */
    INVALID_SQL_STATEMENT_NAME("26000"),

    /** Invalid authorization specification. */
    INVALID_AUTHORIZATION_SPECIFICATION("28000"),

    /** Invalid password. */
    INVALID_PASSWORD("28P01"),

    /** Syntax error. */
    SYNTAX_ERROR("42601"),

    /** Undefined table. */
    UNDEFINED_TABLE("42P01"),

    /** Undefined column. */
    UNDEFINED_COLUMN("42703"),

    /** Duplicate table. */
    DUPLICATE_TABLE("42P07"),

    /** Duplicate column. */
    DUPLICATE_COLUMN("42701"),

    /** Integrity constraint violation. */
    INTEGRITY_CONSTRAINT_VIOLATION("23000"),

    /** Unique violation. */
    UNIQUE_VIOLATION("23505"),

    /** Not null violation. */
    NOT_NULL_VIOLATION("23502"),

    /** Foreign key violation. */
    FOREIGN_KEY_VIOLATION("23503"),

    /** Data exception. */
    DATA_EXCEPTION("22000"),

    /** Division by zero. */
    DIVISION_BY_ZERO("22012"),

    /** Numeric value out of range. */
    NUMERIC_VALUE_OUT_OF_RANGE("22003"),

    /** Invalid text representation. */
    INVALID_TEXT_REPRESENTATION("22P02"),

    /** Internal error. */
    INTERNAL_ERROR("XX000"),

    /** Protocol violation. */
    PROTOCOL_VIOLATION("08P01"),

    /** Insufficient privilege. */
    INSUFFICIENT_PRIVILEGE("42501"),

    /** Query canceled. */
    QUERY_CANCELED("57014"),

    /** Admin shutdown. */
    ADMIN_SHUTDOWN("57P01"),

    /** Crash shutdown. */
    CRASH_SHUTDOWN("57P02"),

    /** Too many connections. */
    TOO_MANY_CONNECTIONS("53300");

    private final String code;

    SqlState(String code) {
        this.code = code;
    }

    /**
     * Returns the 5-character SQLSTATE code.
     *
     * @return the code string
     */
    public String code() {
        return code;
    }

    /**
     * Finds a SqlState by its 5-character code.
     *
     * @param code the SQLSTATE code
     * @return the matching SqlState, or {@link #INTERNAL_ERROR} if not found
     */
    public static SqlState fromCode(String code) {
        for (SqlState state : values()) {
            if (state.code.equals(code)) {
                return state;
            }
        }
        return INTERNAL_ERROR;
    }
}
