package ssg.legoflow.database.postgresql.protocol;

import java.util.List;
import java.util.Map;

/**
 * Sealed interface for all frontend (client to server) messages.
 *
 * @since 1.0.0
 */
public sealed interface FrontendMessage extends PgMessage {

    /**
     * StartupMessage: sent as the first message to initiate a connection.
     * Has no type byte; starts with length + protocol version.
     *
     * @param protocolVersion the protocol version (196608 for 3.0)
     * @param parameters      key-value parameters (user, database, options, etc.)
     */
    record StartupMessage(int protocolVersion, Map<String, String> parameters) implements FrontendMessage {
        /** Protocol version 3.0 as a 32-bit integer. */
        public static final int PROTOCOL_VERSION_30 = 196608;

        @Override
        public byte type() { return 0; }
    }

    /**
     * SSLRequest: requests SSL/TLS upgrade before startup.
     * Has no type byte; consists of length (8) + SSL request code.
     */
    record SSLRequest() implements FrontendMessage {
        /** The SSL request code. */
        public static final int SSL_REQUEST_CODE = 80877103;

        @Override
        public byte type() { return 0; }
    }

    /**
     * CancelRequest: requests cancellation of the current query.
     * Has no type byte; length (16) + cancel request code + pid + secret key.
     *
     * @param processId the backend process ID
     * @param secretKey the secret key from BackendKeyData
     */
    record CancelRequest(int processId, int secretKey) implements FrontendMessage {
        /** The cancel request code. */
        public static final int CANCEL_REQUEST_CODE = 80877102;

        @Override
        public byte type() { return 0; }
    }

    /**
     * PasswordMessage: sends a password in response to an auth request.
     *
     * @param password the password (cleartext or MD5-hashed)
     */
    record PasswordMessage(String password) implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'p'; }
    }

    /**
     * SASLInitialResponse: first message in SASL authentication.
     *
     * @param mechanism       the SASL mechanism name (e.g., "SCRAM-SHA-256")
     * @param initialResponse the client-first-message bytes
     */
    record SASLInitialResponse(String mechanism, byte[] initialResponse) implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'p'; }
    }

    /**
     * SASLResponse: subsequent message in SASL authentication.
     *
     * @param data the client-final-message bytes
     */
    record SASLResponse(byte[] data) implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'p'; }
    }

    /**
     * Query: simple query protocol message containing a SQL string.
     *
     * @param sql the SQL query string (may contain multiple statements separated by semicolons)
     */
    record Query(String sql) implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'Q'; }
    }

    /**
     * Parse: creates a prepared statement from a SQL string.
     *
     * @param statementName the name of the prepared statement (empty string for unnamed)
     * @param sql           the SQL query with $1, $2, ... parameter placeholders
     * @param parameterTypes OIDs of parameter types (0 for unspecified)
     */
    record Parse(String statementName, String sql, int[] parameterTypes) implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'P'; }
    }

    /**
     * Bind: binds parameter values to a prepared statement, creating a portal.
     *
     * @param portalName       the name of the destination portal (empty for unnamed)
     * @param statementName    the name of the source prepared statement
     * @param parameterFormats format codes for parameters (0=text, 1=binary); empty for all text
     * @param parameterValues  parameter values (null entries for SQL NULL)
     * @param resultFormats    format codes for result columns; empty for all text
     */
    record Bind(String portalName, String statementName,
                short[] parameterFormats, byte[][] parameterValues,
                short[] resultFormats) implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'B'; }
    }

    /**
     * Describe: requests description of a prepared statement or portal.
     *
     * @param target 'S' for statement, 'P' for portal
     * @param name   the name of the statement or portal
     */
    record Describe(byte target, String name) implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'D'; }
    }

    /**
     * Execute: executes a portal with an optional row limit.
     *
     * @param portalName the name of the portal to execute
     * @param maxRows    maximum number of rows to return (0 for no limit)
     */
    record Execute(String portalName, int maxRows) implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'E'; }
    }

    /**
     * Sync: marks the end of an extended query sequence and requests a ReadyForQuery.
     */
    record Sync() implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'S'; }
    }

    /**
     * Flush: requests the server to flush its output buffer.
     */
    record Flush() implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'H'; }
    }

    /**
     * Close: closes a prepared statement or portal.
     *
     * @param target 'S' for statement, 'P' for portal
     * @param name   the name of the statement or portal
     */
    record Close(byte target, String name) implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'C'; }
    }

    /**
     * CopyData: a chunk of COPY data.
     *
     * @param data the data bytes
     */
    record CopyData(byte[] data) implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'd'; }
    }

    /**
     * CopyDone: signals the end of COPY IN data.
     */
    record CopyDone() implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'c'; }
    }

    /**
     * CopyFail: signals COPY IN failure.
     *
     * @param errorMessage the error description
     */
    record CopyFail(String errorMessage) implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'f'; }
    }

    /**
     * Terminate: requests the server to close the connection.
     */
    record Terminate() implements FrontendMessage {
        @Override
        public byte type() { return (byte) 'X'; }
    }
}
