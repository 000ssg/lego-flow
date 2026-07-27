package ssg.legoflow.database.mysql.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MySQL wire protocol codec for encoding and decoding all packet types.
 *
 * <p>Handles the command-phase packets including COM_QUERY, COM_STMT_PREPARE,
 * COM_STMT_EXECUTE, COM_PING, COM_QUIT, COM_INIT_DB, and the
 * HandshakeResponse41 client authentication response.
 *
 * @since 1.0.0
 */
public final class MysqlCodec {

    // Command bytes
    /** COM_QUIT command. */
    public static final int COM_QUIT = 0x01;
    /** COM_INIT_DB command. */
    public static final int COM_INIT_DB = 0x02;
    /** COM_QUERY command. */
    public static final int COM_QUERY = 0x03;
    /** COM_FIELD_LIST command. */
    public static final int COM_FIELD_LIST = 0x04;
    /** COM_STATISTICS command. */
    public static final int COM_STATISTICS = 0x09;
    /** COM_PING command. */
    public static final int COM_PING = 0x0E;
    /** COM_STMT_PREPARE command. */
    public static final int COM_STMT_PREPARE = 0x16;
    /** COM_STMT_EXECUTE command. */
    public static final int COM_STMT_EXECUTE = 0x17;
    /** COM_STMT_SEND_LONG_DATA command. */
    public static final int COM_STMT_SEND_LONG_DATA = 0x18;
    /** COM_STMT_CLOSE command. */
    public static final int COM_STMT_CLOSE = 0x19;
    /** COM_STMT_RESET command. */
    public static final int COM_STMT_RESET = 0x1A;
    /** COM_SET_OPTION command. */
    public static final int COM_SET_OPTION = 0x1B;
    /** COM_RESET_CONNECTION command. */
    public static final int COM_RESET_CONNECTION = 0x1F;

    private MysqlCodec() {}

    /**
     * Returns the command byte from a payload.
     *
     * @param payload the packet payload
     * @return the command byte
     */
    public static int commandByte(byte[] payload) {
        return payload[0] & 0xFF;
    }

    /**
     * Checks if a payload is an OK packet.
     *
     * @param payload the packet payload
     * @return true if this is an OK packet (0x00 header and length >= 7)
     */
    public static boolean isOk(byte[] payload) {
        return payload.length > 0 && (payload[0] & 0xFF) == OkPacket.HEADER;
    }

    /**
     * Checks if a payload is an ERR packet.
     *
     * @param payload the packet payload
     * @return true if this is an ERR packet (0xFF header)
     */
    public static boolean isErr(byte[] payload) {
        return payload.length > 0 && (payload[0] & 0xFF) == ErrPacket.HEADER;
    }

    /**
     * Checks if a payload is an EOF packet.
     *
     * @param payload the packet payload
     * @return true if this is an EOF packet
     */
    public static boolean isEof(byte[] payload) {
        return EofPacket.isEof(payload);
    }

    // ---- COM_QUERY ----

    /**
     * Encodes a COM_QUERY packet payload.
     *
     * @param query the SQL query string
     * @return the packet payload
     */
    public static byte[] encodeQuery(String query) {
        var queryBytes = query.getBytes(StandardCharsets.UTF_8);
        var payload = new byte[1 + queryBytes.length];
        payload[0] = COM_QUERY;
        System.arraycopy(queryBytes, 0, payload, 1, queryBytes.length);
        return payload;
    }

    /**
     * Decodes the query string from a COM_QUERY payload.
     *
     * @param payload the packet payload
     * @return the SQL query string
     */
    public static String decodeQuery(byte[] payload) {
        return new String(payload, 1, payload.length - 1, StandardCharsets.UTF_8);
    }

    // ---- COM_STMT_PREPARE ----

    /**
     * Encodes a COM_STMT_PREPARE packet payload.
     *
     * @param query the SQL query to prepare
     * @return the packet payload
     */
    public static byte[] encodePrepare(String query) {
        var queryBytes = query.getBytes(StandardCharsets.UTF_8);
        var payload = new byte[1 + queryBytes.length];
        payload[0] = COM_STMT_PREPARE;
        System.arraycopy(queryBytes, 0, payload, 1, queryBytes.length);
        return payload;
    }

    /**
     * Decodes the query string from a COM_STMT_PREPARE payload.
     *
     * @param payload the packet payload
     * @return the SQL query string
     */
    public static String decodePrepare(byte[] payload) {
        return new String(payload, 1, payload.length - 1, StandardCharsets.UTF_8);
    }

    // ---- COM_STMT_EXECUTE ----

    /**
     * Encodes a COM_STMT_EXECUTE payload header (without parameter data).
     *
     * @param statementId the prepared statement ID
     * @param flags cursor flags (0 = no cursor)
     * @param iterationCount iteration count (always 1)
     * @return the basic payload header
     */
    public static byte[] encodeExecuteHeader(int statementId, int flags, int iterationCount) {
        var buf = ByteBuffer.allocate(1 + 4 + 1 + 4);
        buf.put((byte) COM_STMT_EXECUTE);
        buf.put((byte) (statementId & 0xFF));
        buf.put((byte) ((statementId >> 8) & 0xFF));
        buf.put((byte) ((statementId >> 16) & 0xFF));
        buf.put((byte) ((statementId >> 24) & 0xFF));
        buf.put((byte) flags);
        buf.put((byte) (iterationCount & 0xFF));
        buf.put((byte) ((iterationCount >> 8) & 0xFF));
        buf.put((byte) ((iterationCount >> 16) & 0xFF));
        buf.put((byte) ((iterationCount >> 24) & 0xFF));
        return buf.array();
    }

    /**
     * Decodes the statement ID from a COM_STMT_EXECUTE payload.
     *
     * @param payload the packet payload
     * @return the statement ID
     */
    public static int decodeExecuteStatementId(byte[] payload) {
        return (payload[1] & 0xFF)
                | ((payload[2] & 0xFF) << 8)
                | ((payload[3] & 0xFF) << 16)
                | ((payload[4] & 0xFF) << 24);
    }

    // ---- COM_STMT_CLOSE ----

    /**
     * Encodes a COM_STMT_CLOSE payload.
     *
     * @param statementId the prepared statement ID to close
     * @return the packet payload
     */
    public static byte[] encodeStmtClose(int statementId) {
        var buf = ByteBuffer.allocate(5);
        buf.put((byte) COM_STMT_CLOSE);
        buf.put((byte) (statementId & 0xFF));
        buf.put((byte) ((statementId >> 8) & 0xFF));
        buf.put((byte) ((statementId >> 16) & 0xFF));
        buf.put((byte) ((statementId >> 24) & 0xFF));
        return buf.array();
    }

    /**
     * Decodes the statement ID from a COM_STMT_CLOSE payload.
     *
     * @param payload the packet payload
     * @return the statement ID
     */
    public static int decodeStmtClose(byte[] payload) {
        return decodeExecuteStatementId(payload);
    }

    // ---- COM_STMT_RESET ----

    /**
     * Encodes a COM_STMT_RESET payload.
     *
     * @param statementId the prepared statement ID to reset
     * @return the packet payload
     */
    public static byte[] encodeStmtReset(int statementId) {
        var buf = ByteBuffer.allocate(5);
        buf.put((byte) COM_STMT_RESET);
        buf.put((byte) (statementId & 0xFF));
        buf.put((byte) ((statementId >> 8) & 0xFF));
        buf.put((byte) ((statementId >> 16) & 0xFF));
        buf.put((byte) ((statementId >> 24) & 0xFF));
        return buf.array();
    }

    // ---- COM_STMT_SEND_LONG_DATA ----

    /**
     * Encodes a COM_STMT_SEND_LONG_DATA payload.
     *
     * @param statementId the prepared statement ID
     * @param paramId the parameter index (0-based)
     * @param data the data chunk
     * @return the packet payload
     */
    public static byte[] encodeSendLongData(int statementId, int paramId, byte[] data) {
        var buf = ByteBuffer.allocate(1 + 4 + 2 + data.length);
        buf.put((byte) COM_STMT_SEND_LONG_DATA);
        buf.put((byte) (statementId & 0xFF));
        buf.put((byte) ((statementId >> 8) & 0xFF));
        buf.put((byte) ((statementId >> 16) & 0xFF));
        buf.put((byte) ((statementId >> 24) & 0xFF));
        buf.put((byte) (paramId & 0xFF));
        buf.put((byte) ((paramId >> 8) & 0xFF));
        buf.put(data);
        return buf.array();
    }

    // ---- COM_INIT_DB ----

    /**
     * Encodes a COM_INIT_DB payload.
     *
     * @param database the database name
     * @return the packet payload
     */
    public static byte[] encodeInitDb(String database) {
        var dbBytes = database.getBytes(StandardCharsets.UTF_8);
        var payload = new byte[1 + dbBytes.length];
        payload[0] = COM_INIT_DB;
        System.arraycopy(dbBytes, 0, payload, 1, dbBytes.length);
        return payload;
    }

    /**
     * Decodes the database name from a COM_INIT_DB payload.
     *
     * @param payload the packet payload
     * @return the database name
     */
    public static String decodeInitDb(byte[] payload) {
        return new String(payload, 1, payload.length - 1, StandardCharsets.UTF_8);
    }

    // ---- COM_PING ----

    /**
     * Encodes a COM_PING payload.
     *
     * @return the packet payload
     */
    public static byte[] encodePing() {
        return new byte[]{COM_PING};
    }

    // ---- COM_QUIT ----

    /**
     * Encodes a COM_QUIT payload.
     *
     * @return the packet payload
     */
    public static byte[] encodeQuit() {
        return new byte[]{COM_QUIT};
    }

    // ---- COM_FIELD_LIST ----

    /**
     * Encodes a COM_FIELD_LIST payload.
     *
     * @param table the table name
     * @param fieldWildcard optional wildcard pattern for field names
     * @return the packet payload
     */
    public static byte[] encodeFieldList(String table, String fieldWildcard) {
        var buf = ByteBuffer.allocate(256);
        buf.put((byte) COM_FIELD_LIST);
        LengthEncodedString.writeNullTerminated(buf, table);
        if (fieldWildcard != null) {
            buf.put(fieldWildcard.getBytes(StandardCharsets.UTF_8));
        }
        buf.flip();
        var result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    // ---- COM_STATISTICS ----

    /**
     * Encodes a COM_STATISTICS payload.
     *
     * @return the packet payload
     */
    public static byte[] encodeStatistics() {
        return new byte[]{COM_STATISTICS};
    }

    // ---- COM_SET_OPTION ----

    /**
     * Encodes a COM_SET_OPTION payload.
     *
     * @param option 0 for MYSQL_OPTION_MULTI_STATEMENTS_ON, 1 for OFF
     * @return the packet payload
     */
    public static byte[] encodeSetOption(int option) {
        return new byte[]{COM_SET_OPTION, (byte) (option & 0xFF), (byte) ((option >> 8) & 0xFF)};
    }

    // ---- COM_RESET_CONNECTION ----

    /**
     * Encodes a COM_RESET_CONNECTION payload.
     *
     * @return the packet payload
     */
    public static byte[] encodeResetConnection() {
        return new byte[]{COM_RESET_CONNECTION};
    }

    // ---- HandshakeResponse41 ----

    /**
     * Encodes a HandshakeResponse41 packet payload.
     *
     * @param capabilities client capability flags
     * @param maxPacketSize maximum packet size (default: 16MB)
     * @param charset character set
     * @param username the username
     * @param authResponse the auth response data
     * @param database the database name (or null)
     * @param authPluginName the auth plugin name
     * @param attributes connection attributes (or null)
     * @return the encoded payload
     */
    public static byte[] encodeHandshakeResponse(
            int capabilities, int maxPacketSize, int charset,
            String username, byte[] authResponse, String database,
            String authPluginName, Map<String, String> attributes) {

        var buf = ByteBuffer.allocate(4096);

        // capability flags (4 bytes LE)
        buf.put((byte) (capabilities & 0xFF));
        buf.put((byte) ((capabilities >> 8) & 0xFF));
        buf.put((byte) ((capabilities >> 16) & 0xFF));
        buf.put((byte) ((capabilities >> 24) & 0xFF));

        // max packet size (4 bytes LE)
        buf.put((byte) (maxPacketSize & 0xFF));
        buf.put((byte) ((maxPacketSize >> 8) & 0xFF));
        buf.put((byte) ((maxPacketSize >> 16) & 0xFF));
        buf.put((byte) ((maxPacketSize >> 24) & 0xFF));

        // charset (1 byte)
        buf.put((byte) charset);

        // reserved (23 zero bytes)
        for (int i = 0; i < 23; i++) {
            buf.put((byte) 0);
        }

        // username (null-terminated)
        LengthEncodedString.writeNullTerminated(buf, username);

        // auth response
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA)) {
            LengthEncodedString.writeBytes(buf, authResponse);
        } else if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_SECURE_CONNECTION)) {
            buf.put((byte) authResponse.length);
            buf.put(authResponse);
        } else {
            buf.put(authResponse);
            buf.put((byte) 0);
        }

        // database
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_CONNECT_WITH_DB) && database != null) {
            LengthEncodedString.writeNullTerminated(buf, database);
        }

        // auth plugin name
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
            LengthEncodedString.writeNullTerminated(buf, authPluginName);
        }

        // connection attributes
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_CONNECT_ATTRS) && attributes != null) {
            var attrBuf = ByteBuffer.allocate(2048);
            for (var entry : attributes.entrySet()) {
                LengthEncodedString.write(attrBuf, entry.getKey());
                LengthEncodedString.write(attrBuf, entry.getValue());
            }
            attrBuf.flip();
            LengthEncodedInt.write(buf, attrBuf.remaining());
            buf.put(attrBuf);
        }

        buf.flip();
        var result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Result of decoding a HandshakeResponse41.
     *
     * @param capabilities client capability flags
     * @param maxPacketSize maximum packet size
     * @param charset character set
     * @param username the username
     * @param authResponse the auth response data
     * @param database the database name (may be null)
     * @param authPluginName the auth plugin name (may be null)
     * @param attributes connection attributes (may be empty)
     */
    public record HandshakeResponse(
            int capabilities, int maxPacketSize, int charset,
            String username, byte[] authResponse, String database,
            String authPluginName, Map<String, String> attributes) {}

    /**
     * Decodes a HandshakeResponse41 from payload bytes.
     *
     * @param payload the packet payload
     * @return the decoded handshake response
     */
    public static HandshakeResponse decodeHandshakeResponse(byte[] payload) {
        var buf = ByteBuffer.wrap(payload);

        int capabilities = (buf.get() & 0xFF)
                | ((buf.get() & 0xFF) << 8)
                | ((buf.get() & 0xFF) << 16)
                | ((buf.get() & 0xFF) << 24);

        int maxPacketSize = (buf.get() & 0xFF)
                | ((buf.get() & 0xFF) << 8)
                | ((buf.get() & 0xFF) << 16)
                | ((buf.get() & 0xFF) << 24);

        int charset = buf.get() & 0xFF;

        // skip 23 reserved bytes
        for (int i = 0; i < 23; i++) {
            buf.get();
        }

        String username = LengthEncodedString.readNullTerminated(buf);

        byte[] authResponse;
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA)) {
            authResponse = LengthEncodedString.readBytes(buf);
        } else if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_SECURE_CONNECTION)) {
            int authLen = buf.get() & 0xFF;
            authResponse = new byte[authLen];
            buf.get(authResponse);
        } else {
            authResponse = LengthEncodedString.readRestOfPacketBytes(buf);
        }

        String database = null;
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_CONNECT_WITH_DB) && buf.hasRemaining()) {
            database = LengthEncodedString.readNullTerminated(buf);
        }

        String authPluginName = null;
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PLUGIN_AUTH) && buf.hasRemaining()) {
            authPluginName = LengthEncodedString.readNullTerminated(buf);
        }

        Map<String, String> attributes = new LinkedHashMap<>();
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_CONNECT_ATTRS) && buf.hasRemaining()) {
            long totalLen = LengthEncodedInt.read(buf);
            int endPos = buf.position() + (int) totalLen;
            while (buf.position() < endPos && buf.hasRemaining()) {
                String key = LengthEncodedString.read(buf);
                String value = LengthEncodedString.read(buf);
                if (key != null && value != null) {
                    attributes.put(key, value);
                }
            }
        }

        return new HandshakeResponse(capabilities, maxPacketSize, charset,
                username, authResponse, database, authPluginName, attributes);
    }

    // ---- Prepared Statement Response ----

    /**
     * Result of a COM_STMT_PREPARE OK response.
     *
     * @param statementId the server-assigned statement ID
     * @param numColumns number of columns in the result set
     * @param numParams number of parameters in the statement
     * @param warningCount number of warnings
     */
    public record PrepareOk(int statementId, int numColumns, int numParams, int warningCount) {}

    /**
     * Encodes a COM_STMT_PREPARE OK response payload.
     *
     * @param ok the prepare response data
     * @return the encoded payload
     */
    public static byte[] encodePrepareOk(PrepareOk ok) {
        var buf = ByteBuffer.allocate(12);
        buf.put((byte) 0x00); // status

        buf.put((byte) (ok.statementId & 0xFF));
        buf.put((byte) ((ok.statementId >> 8) & 0xFF));
        buf.put((byte) ((ok.statementId >> 16) & 0xFF));
        buf.put((byte) ((ok.statementId >> 24) & 0xFF));

        buf.put((byte) (ok.numColumns & 0xFF));
        buf.put((byte) ((ok.numColumns >> 8) & 0xFF));

        buf.put((byte) (ok.numParams & 0xFF));
        buf.put((byte) ((ok.numParams >> 8) & 0xFF));

        buf.put((byte) 0); // filler

        buf.put((byte) (ok.warningCount & 0xFF));
        buf.put((byte) ((ok.warningCount >> 8) & 0xFF));

        return buf.array();
    }

    /**
     * Decodes a COM_STMT_PREPARE OK response.
     *
     * @param payload the packet payload
     * @return the prepare response data
     */
    public static PrepareOk decodePrepareOk(byte[] payload) {
        var buf = ByteBuffer.wrap(payload);
        buf.get(); // skip status

        int statementId = (buf.get() & 0xFF)
                | ((buf.get() & 0xFF) << 8)
                | ((buf.get() & 0xFF) << 16)
                | ((buf.get() & 0xFF) << 24);

        int numColumns = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);
        int numParams = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);

        buf.get(); // filler

        int warningCount = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);

        return new PrepareOk(statementId, numColumns, numParams, warningCount);
    }

    /**
     * Returns a human-readable name for a command byte.
     *
     * @param command the command byte
     * @return the command name
     */
    public static String commandName(int command) {
        return switch (command) {
            case COM_QUIT -> "COM_QUIT";
            case COM_INIT_DB -> "COM_INIT_DB";
            case COM_QUERY -> "COM_QUERY";
            case COM_FIELD_LIST -> "COM_FIELD_LIST";
            case COM_STATISTICS -> "COM_STATISTICS";
            case COM_PING -> "COM_PING";
            case COM_STMT_PREPARE -> "COM_STMT_PREPARE";
            case COM_STMT_EXECUTE -> "COM_STMT_EXECUTE";
            case COM_STMT_SEND_LONG_DATA -> "COM_STMT_SEND_LONG_DATA";
            case COM_STMT_CLOSE -> "COM_STMT_CLOSE";
            case COM_STMT_RESET -> "COM_STMT_RESET";
            case COM_SET_OPTION -> "COM_SET_OPTION";
            case COM_RESET_CONNECTION -> "COM_RESET_CONNECTION";
            default -> "UNKNOWN(0x" + Integer.toHexString(command) + ")";
        };
    }
}
