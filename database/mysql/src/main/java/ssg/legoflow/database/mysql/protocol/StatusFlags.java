package ssg.legoflow.database.mysql.protocol;

/**
 * MySQL server status flags.
 *
 * <p>Status flags are sent in OK, EOF, and handshake packets to communicate
 * the current server state to the client.
 *
 * @since 1.0.0
 */
public final class StatusFlags {

    private StatusFlags() {}

    /** A transaction is currently active. */
    public static final int SERVER_STATUS_IN_TRANS = 1;

    /** Auto-commit mode is enabled. */
    public static final int SERVER_STATUS_AUTOCOMMIT = 1 << 1;

    /** More results exist (multi-result sets). */
    public static final int SERVER_MORE_RESULTS_EXISTS = 1 << 3;

    /** No good index was used for the query. */
    public static final int SERVER_STATUS_NO_GOOD_INDEX_USED = 1 << 4;

    /** No index was used for the query. */
    public static final int SERVER_STATUS_NO_INDEX_USED = 1 << 5;

    /** Used by cursors: the server has opened a cursor. */
    public static final int SERVER_STATUS_CURSOR_EXISTS = 1 << 6;

    /** Used by cursors: the last row has been sent. */
    public static final int SERVER_STATUS_LAST_ROW_SENT = 1 << 7;

    /** Database was dropped. */
    public static final int SERVER_STATUS_DB_DROPPED = 1 << 8;

    /** No backslash escapes in string literals. */
    public static final int SERVER_STATUS_NO_BACKSLASH_ESCAPES = 1 << 9;

    /** Metadata has changed. */
    public static final int SERVER_STATUS_METADATA_CHANGED = 1 << 10;

    /** The query was slow. */
    public static final int SERVER_QUERY_WAS_SLOW = 1 << 11;

    /** PS output parameters are available. */
    public static final int SERVER_PS_OUT_PARAMS = 1 << 12;

    /** In a read-only transaction. */
    public static final int SERVER_STATUS_IN_TRANS_READONLY = 1 << 13;

    /** Session state info changed (for SESSION_TRACK). */
    public static final int SERVER_SESSION_STATE_CHANGED = 1 << 14;

    /** Default status: autocommit enabled. */
    public static final int DEFAULT_STATUS = SERVER_STATUS_AUTOCOMMIT;

    /**
     * Checks if a specific status flag is set.
     *
     * @param status the status flags bitmask
     * @param flag the flag to check
     * @return true if the flag is set
     */
    public static boolean hasStatus(int status, int flag) {
        return (status & flag) != 0;
    }

    /**
     * Returns a human-readable string of all set status flags.
     *
     * @param status the status flags bitmask
     * @return comma-separated list of flag names
     */
    public static String toString(int status) {
        var sb = new StringBuilder();
        if (hasStatus(status, SERVER_STATUS_IN_TRANS)) append(sb, "IN_TRANS");
        if (hasStatus(status, SERVER_STATUS_AUTOCOMMIT)) append(sb, "AUTOCOMMIT");
        if (hasStatus(status, SERVER_MORE_RESULTS_EXISTS)) append(sb, "MORE_RESULTS_EXISTS");
        if (hasStatus(status, SERVER_STATUS_NO_GOOD_INDEX_USED)) append(sb, "NO_GOOD_INDEX_USED");
        if (hasStatus(status, SERVER_STATUS_NO_INDEX_USED)) append(sb, "NO_INDEX_USED");
        if (hasStatus(status, SERVER_STATUS_CURSOR_EXISTS)) append(sb, "CURSOR_EXISTS");
        if (hasStatus(status, SERVER_STATUS_LAST_ROW_SENT)) append(sb, "LAST_ROW_SENT");
        if (hasStatus(status, SERVER_SESSION_STATE_CHANGED)) append(sb, "SESSION_STATE_CHANGED");
        return sb.toString();
    }

    private static void append(StringBuilder sb, String name) {
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(name);
    }
}
