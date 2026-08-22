package ssg.legoflow.database.postgresql.protocol;

import java.util.List;
import java.util.Map;
/**
 * Sealed interface for all backend (server to client) messages.
 *
 * @since 0.1.0
 */
public sealed interface BackendMessage extends PgMessage {

    // ---- Authentication messages ----

    /**
     * AuthenticationOk: authentication was successful.
     */
    record AuthenticationOk() implements BackendMessage {
        @Override
        public byte type() { return (byte) 'R'; }
    }

    /**
     * AuthenticationCleartextPassword: server requests a cleartext password.
     */
    record AuthenticationCleartextPassword() implements BackendMessage {
        @Override
        public byte type() { return (byte) 'R'; }
    }

    /**
     * AuthenticationMD5Password: server requests an MD5-hashed password.
     *
     * @param salt the 4-byte salt for MD5 hashing
     */
    record AuthenticationMD5Password(byte[] salt) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'R'; }
    }

    /**
     * AuthenticationSASL: server requests SASL authentication.
     *
     * @param mechanisms the list of supported SASL mechanisms
     */
    record AuthenticationSASL(List<String> mechanisms) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'R'; }
    }

    /**
     * AuthenticationSASLContinue: server's SASL challenge.
     *
     * @param data the server-first-message bytes
     */
    record AuthenticationSASLContinue(byte[] data) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'R'; }
    }

    /**
     * AuthenticationSASLFinal: server's final SASL message.
     *
     * @param data the server-final-message bytes
     */
    record AuthenticationSASLFinal(byte[] data) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'R'; }
    }

    // ---- Startup phase messages ----

    /**
     * ParameterStatus: reports a server parameter value.
     *
     * @param name  the parameter name
     * @param value the parameter value
     */
    record ParameterStatus(String name, String value) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'S'; }
    }

    /**
     * BackendKeyData: provides the backend process ID and secret key for cancel requests.
     *
     * @param processId the backend process ID
     * @param secretKey the secret key
     */
    record BackendKeyData(int processId, int secretKey) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'K'; }
    }

    /**
     * ReadyForQuery: indicates the server is ready for a new query cycle.
     *
     * @param status the current transaction status
     */
    record ReadyForQuery(TransactionStatus status) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'Z'; }
    }

    // ---- Query result messages ----

    /**
     * RowDescription: describes the columns of a query result.
     *
     * @param columns the column descriptors
     */
    record RowDescription(List<ColumnDescription> columns) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'T'; }
    }

    /**
     * DataRow: a single row of query results.
     *
     * @param values column values (null entries represent SQL NULL)
     */
    record DataRow(byte[][] values) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'D'; }
    }

    /**
     * CommandComplete: indicates successful completion of a command.
     *
     * @param tag the command tag (e.g., "SELECT 5", "INSERT 0 1", "UPDATE 3", "DELETE 2")
     */
    record CommandComplete(String tag) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'C'; }
    }

    /**
     * EmptyQueryResponse: response to an empty query string.
     */
    record EmptyQueryResponse() implements BackendMessage {
        @Override
        public byte type() { return (byte) 'I'; }
    }

    // ---- Extended query protocol messages ----

    /**
     * ParseComplete: response to a successful Parse message.
     */
    record ParseComplete() implements BackendMessage {
        @Override
        public byte type() { return (byte) '1'; }
    }

    /**
     * BindComplete: response to a successful Bind message.
     */
    record BindComplete() implements BackendMessage {
        @Override
        public byte type() { return (byte) '2'; }
    }

    /**
     * CloseComplete: response to a successful Close message.
     */
    record CloseComplete() implements BackendMessage {
        @Override
        public byte type() { return (byte) '3'; }
    }

    /**
     * NoData: indicates that a Describe returned no row description.
     */
    record NoData() implements BackendMessage {
        @Override
        public byte type() { return (byte) 'n'; }
    }

    /**
     * ParameterDescription: describes the parameters of a prepared statement.
     *
     * @param parameterOids the OIDs of the parameter types
     */
    record ParameterDescription(int[] parameterOids) implements BackendMessage {
        @Override
        public byte type() { return (byte) 't'; }
    }

    /**
     * PortalSuspended: indicates that Execute completed but more rows remain.
     */
    record PortalSuspended() implements BackendMessage {
        @Override
        public byte type() { return (byte) 's'; }
    }

    // ---- COPY messages ----

    /**
     * CopyInResponse: server is ready for COPY IN data.
     *
     * @param overallFormat  0 for text, 1 for binary
     * @param columnFormats  format codes per column
     */
    record CopyInResponse(byte overallFormat, short[] columnFormats) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'G'; }
    }

    /**
     * CopyOutResponse: server will send COPY OUT data.
     *
     * @param overallFormat  0 for text, 1 for binary
     * @param columnFormats  format codes per column
     */
    record CopyOutResponse(byte overallFormat, short[] columnFormats) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'H'; }
    }

    /**
     * CopyBothResponse: server will send/receive COPY data in both directions (streaming replication).
     *
     * @param overallFormat  0 for text, 1 for binary
     * @param columnFormats  format codes per column
     */
    record CopyBothResponse(byte overallFormat, short[] columnFormats) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'W'; }
    }

    /**
     * CopyData: a chunk of COPY data from server.
     *
     * @param data the data bytes
     */
    record CopyData(byte[] data) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'd'; }
    }

    /**
     * CopyDone: signals the end of COPY OUT data.
     */
    record CopyDone() implements BackendMessage {
        @Override
        public byte type() { return (byte) 'c'; }
    }

    // ---- Notification ----

    /**
     * NotificationResponse: an asynchronous notification from LISTEN/NOTIFY.
     *
     * @param processId the notifying backend's process ID
     * @param channel   the notification channel name
     * @param payload   the notification payload
     */
    record NotificationResponse(int processId, String channel, String payload) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'A'; }
    }

    // ---- Error/Notice ----

    /**
     * ErrorResponse: an error from the server.
     *
     * @param fields the error fields keyed by field type byte
     */
    record ErrorResponse(Map<Byte, String> fields) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'E'; }

        /** Returns the severity. */
        public String severity() { return fields.getOrDefault((byte) 'S', "ERROR"); }
        /** Returns the SQLSTATE code. */
        public String sqlState() { return fields.getOrDefault((byte) 'C', "XX000"); }
        /** Returns the primary message. */
        public String message() { return fields.getOrDefault((byte) 'M', "Unknown error"); }
        /** Returns the detail message, if any. */
        public String detail() { return fields.get((byte) 'D'); }
        /** Returns the hint, if any. */
        public String hint() { return fields.get((byte) 'H'); }
        /** Returns the position in the query string, if any. */
        public String position() { return fields.get((byte) 'P'); }
    }

    /**
     * NoticeResponse: a notice (non-error) from the server.
     *
     * @param fields the notice fields keyed by field type byte
     */
    record NoticeResponse(Map<Byte, String> fields) implements BackendMessage {
        @Override
        public byte type() { return (byte) 'N'; }

        /** Returns the severity. */
        public String severity() { return fields.getOrDefault((byte) 'S', "NOTICE"); }
        /** Returns the SQLSTATE code. */
        public String sqlState() { return fields.getOrDefault((byte) 'C', "00000"); }
        /** Returns the primary message. */
        public String message() { return fields.getOrDefault((byte) 'M', ""); }
    }

    // ---- Helper types ----

    /**
     * Describes a single column in a RowDescription.
     *
     * @param name         the column name
     * @param tableOid     the OID of the table (0 if not a table column)
     * @param columnIndex  the attribute number of the column (0 if not a table column)
     * @param typeOid      the OID of the column type
     * @param typeSize     the data type size (-1 for variable)
     * @param typeModifier the type modifier (-1 if not available)
     * @param formatCode   0 for text, 1 for binary
     */
    record ColumnDescription(String name, int tableOid, short columnIndex,
                             int typeOid, short typeSize, int typeModifier,
                             short formatCode) {}
}
