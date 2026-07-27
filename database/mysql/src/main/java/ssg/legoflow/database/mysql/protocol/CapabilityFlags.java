package ssg.legoflow.database.mysql.protocol;

/**
 * MySQL client/server capability flags.
 *
 * <p>Capability flags are used during the handshake to negotiate features
 * supported by both client and server. Flags are represented as a 32-bit
 * bitmask split across two 16-bit words in the handshake packets.
 *
 * @since 1.0.0
 */
public final class CapabilityFlags {

    private CapabilityFlags() {}

    /** Use the improved version of Old Password Authentication. */
    public static final int CLIENT_LONG_PASSWORD = 1;

    /** Send found rows instead of affected rows in EOF_Packet. */
    public static final int CLIENT_FOUND_ROWS = 1 << 1;

    /** Longer flags in ColumnDefinition. */
    public static final int CLIENT_LONG_FLAG = 1 << 2;

    /** Database (schema) name can be specified on connect. */
    public static final int CLIENT_CONNECT_WITH_DB = 1 << 3;

    /** Do not allow database.table.column. */
    public static final int CLIENT_NO_SCHEMA = 1 << 4;

    /** Compression protocol supported. */
    public static final int CLIENT_COMPRESS = 1 << 5;

    /** Special handling of ODBC behavior. */
    public static final int CLIENT_ODBC = 1 << 6;

    /** Can use LOAD DATA LOCAL. */
    public static final int CLIENT_LOCAL_FILES = 1 << 7;

    /** Ignore spaces before '('. */
    public static final int CLIENT_IGNORE_SPACE = 1 << 8;

    /** Support the 4.1 protocol. */
    public static final int CLIENT_PROTOCOL_41 = 1 << 9;

    /** Wait for interactive timeout. */
    public static final int CLIENT_INTERACTIVE = 1 << 10;

    /** SSL support. */
    public static final int CLIENT_SSL = 1 << 11;

    /** Ignore sigpipe. */
    public static final int CLIENT_IGNORE_SIGPIPE = 1 << 12;

    /** Client knows about transactions. */
    public static final int CLIENT_TRANSACTIONS = 1 << 13;

    /** RESERVED: Old flag for 4.1 protocol. */
    public static final int CLIENT_RESERVED = 1 << 14;

    /** Old flag for 4.1 authentication. */
    public static final int CLIENT_SECURE_CONNECTION = 1 << 15;

    /** Enable/disable multi-stmt support. */
    public static final int CLIENT_MULTI_STATEMENTS = 1 << 16;

    /** Enable/disable multi-results. */
    public static final int CLIENT_MULTI_RESULTS = 1 << 17;

    /** Multi-results and OUT parameters in PS-protocol. */
    public static final int CLIENT_PS_MULTI_RESULTS = 1 << 18;

    /** Client supports authentication plugins. */
    public static final int CLIENT_PLUGIN_AUTH = 1 << 19;

    /** Client supports connection attributes. */
    public static final int CLIENT_CONNECT_ATTRS = 1 << 20;

    /** Length of auth response data is a length-encoded integer. */
    public static final int CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA = 1 << 21;

    /** Server can handle expired passwords. */
    public static final int CLIENT_CAN_HANDLE_EXPIRED_PASSWORDS = 1 << 22;

    /** Server sends session state changes after OK packet. */
    public static final int CLIENT_SESSION_TRACK = 1 << 23;

    /** Server can send OK after a Text Resultset. */
    public static final int CLIENT_DEPRECATE_EOF = 1 << 24;

    /** Server can handle optional result set metadata. */
    public static final int CLIENT_OPTIONAL_RESULTSET_METADATA = 1 << 25;

    /** Default capabilities for a basic server. */
    public static final int DEFAULT_SERVER_CAPABILITIES =
            CLIENT_LONG_PASSWORD
            | CLIENT_FOUND_ROWS
            | CLIENT_LONG_FLAG
            | CLIENT_CONNECT_WITH_DB
            | CLIENT_PROTOCOL_41
            | CLIENT_TRANSACTIONS
            | CLIENT_SECURE_CONNECTION
            | CLIENT_MULTI_STATEMENTS
            | CLIENT_MULTI_RESULTS
            | CLIENT_PS_MULTI_RESULTS
            | CLIENT_PLUGIN_AUTH
            | CLIENT_CONNECT_ATTRS
            | CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA
            | CLIENT_DEPRECATE_EOF
            | CLIENT_SESSION_TRACK;

    /** Default capabilities for a basic client. */
    public static final int DEFAULT_CLIENT_CAPABILITIES =
            CLIENT_LONG_PASSWORD
            | CLIENT_FOUND_ROWS
            | CLIENT_LONG_FLAG
            | CLIENT_CONNECT_WITH_DB
            | CLIENT_PROTOCOL_41
            | CLIENT_TRANSACTIONS
            | CLIENT_SECURE_CONNECTION
            | CLIENT_MULTI_STATEMENTS
            | CLIENT_MULTI_RESULTS
            | CLIENT_PS_MULTI_RESULTS
            | CLIENT_PLUGIN_AUTH
            | CLIENT_CONNECT_ATTRS
            | CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA
            | CLIENT_DEPRECATE_EOF;

    /**
     * Checks if a specific capability flag is set.
     *
     * @param capabilities the capabilities bitmask
     * @param flag the flag to check
     * @return true if the flag is set
     */
    public static boolean hasCapability(int capabilities, int flag) {
        return (capabilities & flag) != 0;
    }

    /**
     * Returns a human-readable string of all set capability flags.
     *
     * @param capabilities the capabilities bitmask
     * @return comma-separated list of flag names
     */
    public static String toString(int capabilities) {
        var sb = new StringBuilder();
        if (hasCapability(capabilities, CLIENT_LONG_PASSWORD)) append(sb, "LONG_PASSWORD");
        if (hasCapability(capabilities, CLIENT_FOUND_ROWS)) append(sb, "FOUND_ROWS");
        if (hasCapability(capabilities, CLIENT_LONG_FLAG)) append(sb, "LONG_FLAG");
        if (hasCapability(capabilities, CLIENT_CONNECT_WITH_DB)) append(sb, "CONNECT_WITH_DB");
        if (hasCapability(capabilities, CLIENT_PROTOCOL_41)) append(sb, "PROTOCOL_41");
        if (hasCapability(capabilities, CLIENT_SECURE_CONNECTION)) append(sb, "SECURE_CONNECTION");
        if (hasCapability(capabilities, CLIENT_MULTI_STATEMENTS)) append(sb, "MULTI_STATEMENTS");
        if (hasCapability(capabilities, CLIENT_MULTI_RESULTS)) append(sb, "MULTI_RESULTS");
        if (hasCapability(capabilities, CLIENT_PS_MULTI_RESULTS)) append(sb, "PS_MULTI_RESULTS");
        if (hasCapability(capabilities, CLIENT_PLUGIN_AUTH)) append(sb, "PLUGIN_AUTH");
        if (hasCapability(capabilities, CLIENT_CONNECT_ATTRS)) append(sb, "CONNECT_ATTRS");
        if (hasCapability(capabilities, CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA)) append(sb, "PLUGIN_AUTH_LENENC");
        if (hasCapability(capabilities, CLIENT_DEPRECATE_EOF)) append(sb, "DEPRECATE_EOF");
        if (hasCapability(capabilities, CLIENT_SESSION_TRACK)) append(sb, "SESSION_TRACK");
        return sb.toString();
    }

    private static void append(StringBuilder sb, String name) {
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(name);
    }
}
