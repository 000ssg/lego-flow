package ssg.legoflow.database.mysql.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * MySQL ERR_Packet (0xFF header).
 *
 * <p>Sent by the server to indicate an error. Contains an error code,
 * SQL state marker, 5-character SQLSTATE, and human-readable error message.
 *
 * @param errorCode the MySQL error code
 * @param sqlState the 5-character SQLSTATE value
 * @param message the human-readable error message
 * @since 1.0.0
 */
public record ErrPacket(int errorCode, String sqlState, String message) {

    /** ERR packet header byte. */
    public static final int HEADER = 0xFF;

    /**
     * Decodes an ERR packet from payload bytes.
     *
     * @param payload the packet payload
     * @param capabilities the negotiated capabilities
     * @return the decoded ERR packet
     */
    public static ErrPacket decode(byte[] payload, int capabilities) {
        var buf = ByteBuffer.wrap(payload);
        buf.get(); // skip header 0xFF

        int errorCode = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);

        String sqlState = "HY000";
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PROTOCOL_41)) {
            buf.get(); // skip '#' marker
            var stateBytes = new byte[5];
            buf.get(stateBytes);
            sqlState = new String(stateBytes, StandardCharsets.US_ASCII);
        }

        var msgBytes = new byte[buf.remaining()];
        buf.get(msgBytes);
        String message = new String(msgBytes, StandardCharsets.UTF_8);

        return new ErrPacket(errorCode, sqlState, message);
    }

    /**
     * Encodes this ERR packet as payload bytes.
     *
     * @param capabilities the negotiated capabilities
     * @return the encoded payload
     */
    public byte[] encode(int capabilities) {
        var msgBytes = message.getBytes(StandardCharsets.UTF_8);
        var buf = ByteBuffer.allocate(1 + 2 + 1 + 5 + msgBytes.length);

        buf.put((byte) HEADER);
        buf.put((byte) (errorCode & 0xFF));
        buf.put((byte) ((errorCode >> 8) & 0xFF));

        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PROTOCOL_41)) {
            buf.put((byte) '#');
            var stateBytes = sqlState.getBytes(StandardCharsets.US_ASCII);
            buf.put(stateBytes, 0, Math.min(5, stateBytes.length));
            // pad if needed
            for (int i = stateBytes.length; i < 5; i++) {
                buf.put((byte) '0');
            }
        }

        buf.put(msgBytes);

        buf.flip();
        var result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Creates an ERR packet with default SQLSTATE.
     *
     * @param errorCode the MySQL error code
     * @param message the error message
     * @return the ERR packet
     */
    public static ErrPacket error(int errorCode, String message) {
        return new ErrPacket(errorCode, "HY000", message);
    }

    /**
     * Creates an access denied error.
     *
     * @param user the username
     * @param host the host
     * @return the ERR packet
     */
    public static ErrPacket accessDenied(String user, String host) {
        return new ErrPacket(1045, "28000",
                "Access denied for user '" + user + "'@'" + host + "'");
    }

    /**
     * Creates a syntax error.
     *
     * @param message the error details
     * @return the ERR packet
     */
    public static ErrPacket syntaxError(String message) {
        return new ErrPacket(1064, "42000", "You have an error in your SQL syntax: " + message);
    }

    /**
     * Creates an unknown database error.
     *
     * @param database the database name
     * @return the ERR packet
     */
    public static ErrPacket unknownDatabase(String database) {
        return new ErrPacket(1049, "42000", "Unknown database '" + database + "'");
    }

    /**
     * Creates a table already exists error.
     *
     * @param table the table name
     * @return the ERR packet
     */
    public static ErrPacket tableExists(String table) {
        return new ErrPacket(1050, "42S01", "Table '" + table + "' already exists");
    }

    /**
     * Creates an unknown table error.
     *
     * @param table the table name
     * @return the ERR packet
     */
    public static ErrPacket unknownTable(String table) {
        return new ErrPacket(1051, "42S02", "Unknown table '" + table + "'");
    }

    /**
     * Creates an unknown column error.
     *
     * @param column the column name
     * @return the ERR packet
     */
    public static ErrPacket unknownColumn(String column) {
        return new ErrPacket(1054, "42S22", "Unknown column '" + column + "'");
    }
}
