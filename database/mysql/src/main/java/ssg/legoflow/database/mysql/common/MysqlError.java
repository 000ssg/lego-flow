package ssg.legoflow.database.mysql.common;

/**
 * MySQL error codes and SQLSTATE mappings.
 *
 * <p>Common error codes used in ERR_Packet responses. Each error has a
 * numeric code, a 5-character SQLSTATE value, and a human-readable message template.
 *
 * @since 1.0.0
 */
public enum MysqlError {

    /** Access denied for user. */
    ER_ACCESS_DENIED(1045, "28000", "Access denied for user '%s'@'%s'"),

    /** Unknown database. */
    ER_BAD_DB_ERROR(1049, "42000", "Unknown database '%s'"),

    /** Table already exists. */
    ER_TABLE_EXISTS_ERROR(1050, "42S01", "Table '%s' already exists"),

    /** Unknown table. */
    ER_BAD_TABLE_ERROR(1051, "42S02", "Unknown table '%s'"),

    /** Unknown column. */
    ER_BAD_FIELD_ERROR(1054, "42S22", "Unknown column '%s'"),

    /** SQL syntax error. */
    ER_PARSE_ERROR(1064, "42000", "You have an error in your SQL syntax"),

    /** Duplicate entry for key. */
    ER_DUP_ENTRY(1062, "23000", "Duplicate entry '%s' for key '%s'"),

    /** Column count doesn't match value count. */
    ER_WRONG_VALUE_COUNT(1058, "21S01", "Column count doesn't match value count"),

    /** Unknown prepared statement. */
    ER_UNKNOWN_STMT_HANDLER(1243, "HY000", "Unknown prepared statement handler"),

    /** No database selected. */
    ER_NO_DB_ERROR(1046, "3D000", "No database selected"),

    /** Internal error. */
    ER_INTERNAL_ERROR(1815, "HY000", "Internal error: %s"),

    /** Not supported. */
    ER_NOT_SUPPORTED_YET(1235, "42000", "This version doesn't yet support '%s'"),

    /** Wrong arguments. */
    ER_WRONG_ARGUMENTS(1210, "HY000", "Incorrect arguments to %s"),

    /** Unknown command. */
    ER_UNKNOWN_COM_ERROR(1047, "08S01", "Unknown command");

    private final int code;
    private final String sqlState;
    private final String messageTemplate;

    MysqlError(int code, String sqlState, String messageTemplate) {
        this.code = code;
        this.sqlState = sqlState;
        this.messageTemplate = messageTemplate;
    }

    /**
     * Returns the MySQL error code.
     *
     * @return the error code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the SQLSTATE value.
     *
     * @return the 5-character SQLSTATE
     */
    public String sqlState() {
        return sqlState;
    }

    /**
     * Returns the message template.
     *
     * @return the message template with %s placeholders
     */
    public String messageTemplate() {
        return messageTemplate;
    }

    /**
     * Formats the error message with arguments.
     *
     * @param args the arguments for the message template
     * @return the formatted message
     */
    public String format(Object... args) {
        return String.format(messageTemplate, args);
    }
}
