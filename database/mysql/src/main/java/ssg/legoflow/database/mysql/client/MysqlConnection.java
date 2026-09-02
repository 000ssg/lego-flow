package ssg.legoflow.database.mysql.client;

import ssg.legoflow.database.mysql.auth.*;
import ssg.legoflow.database.mysql.protocol.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * MySQL connection lifecycle: handshake, authentication, and connection management.
 *
 * <p>Handles the initial handshake with the server, auth plugin negotiation,
 * and provides low-level packet send/receive.
 *
 * @since 0.1.0
 */
public class MysqlConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MysqlConnection.class);

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    private HandshakeV10 serverHandshake;
    private int capabilities;
    private int sequenceId;

    /**
     * Creates a new connection from an existing socket.
     *
     * @param socket the connected socket
     * @throws IOException if streams cannot be obtained
     */
    public MysqlConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    /**
     * Performs the handshake and authentication.
     *
     * @param username the username
     * @param password the password
     * @param database the initial database (or null)
     * @param attributes connection attributes (or null)
     * @throws IOException if an I/O error occurs
     * @throws MysqlException if authentication fails
     */
    public void connect(String username, String password, String database,
                        Map<String, String> attributes) throws IOException {
        // Read server greeting
        var greetingPacket = MysqlPacket.readFrom(in);
        serverHandshake = HandshakeV10.decode(greetingPacket.payload());
        LOG.debug("Server version: {}, connection id: {}, auth plugin: {}",
                serverHandshake.serverVersion(), serverHandshake.connectionId(),
                serverHandshake.authPluginName());

        // Negotiate capabilities
        int serverCaps = serverHandshake.capabilityFlags();
        int clientCaps = CapabilityFlags.DEFAULT_CLIENT_CAPABILITIES;
        if (database != null) {
            clientCaps |= CapabilityFlags.CLIENT_CONNECT_WITH_DB;
        }
        capabilities = clientCaps & serverCaps;

        // Generate auth response
        String authPluginName = serverHandshake.authPluginName();
        byte[] scramble = serverHandshake.authPluginData();
        AuthPlugin authPlugin = resolveAuthPlugin(authPluginName);
        byte[] authResponse = authPlugin.generateAuthResponse(password, scramble);

        // Send handshake response
        var responsePayload = MysqlCodec.encodeHandshakeResponse(
                capabilities,
                MysqlPacket.MAX_PAYLOAD_SIZE,
                HandshakeV10.DEFAULT_CHARSET,
                username,
                authResponse,
                database,
                authPluginName,
                attributes != null ? attributes : ConnectionAttributes.defaults()
        );

        new MysqlPacket(1, responsePayload).writeTo(out);
        out.flush();

        // Read auth result
        var authResult = MysqlPacket.readFrom(in);
        sequenceId = authResult.sequenceId() + 1;
        var authPayload = authResult.payload();

        // Handle auth switch request
        if (AuthSwitchRequest.isAuthSwitch(authPayload)) {
            var switchRequest = AuthSwitchRequest.decode(authPayload);
            LOG.debug("Auth switch to: {}", switchRequest.pluginName());

            authPlugin = resolveAuthPlugin(switchRequest.pluginName());
            authResponse = authPlugin.generateAuthResponse(password, switchRequest.pluginData());

            new MysqlPacket(sequenceId++, authResponse).writeTo(out);
            out.flush();

            authResult = MysqlPacket.readFrom(in);
            authPayload = authResult.payload();
            sequenceId = authResult.sequenceId() + 1;
        }

        // Handle AuthMoreData (caching_sha2_password fast auth)
        if (AuthSwitchRequest.isAuthMoreData(authPayload)) {
            byte status = authPayload[1];
            if (status == CachingSha2Password.FAST_AUTH_SUCCESS) {
                // Read the final OK
                authResult = MysqlPacket.readFrom(in);
                authPayload = authResult.payload();
                sequenceId = authResult.sequenceId() + 1;
            }
            // FULL_AUTH_REQUIRED would need RSA — not supported
        }

        if (MysqlCodec.isErr(authPayload)) {
            var err = ErrPacket.decode(authPayload, capabilities);
            throw new MysqlException(err.errorCode(), err.sqlState(), err.message());
        }

        if (!MysqlCodec.isOk(authPayload)) {
            throw new MysqlException(0, "HY000", "Unexpected auth response: 0x"
                    + Integer.toHexString(authPayload[0] & 0xFF));
        }

        LOG.debug("Connected to {} as {} (database: {})",
                serverHandshake.serverVersion(), username, database);
    }

    /**
     * Sends a packet to the server.
     *
     * @param payload the packet payload
     * @throws IOException if an I/O error occurs
     */
    public void sendPacket(byte[] payload) throws IOException {
        sequenceId = 0;
        new MysqlPacket(sequenceId, payload).writeTo(out);
        out.flush();
    }

    /**
     * Receives a packet from the server.
     *
     * @return the received packet
     * @throws IOException if an I/O error occurs
     */
    public MysqlPacket receivePacket() throws IOException {
        var packet = MysqlPacket.readFrom(in);
        sequenceId = packet.sequenceId() + 1;
        return packet;
    }

    /**
     * Returns the server handshake.
     *
     * @return the HandshakeV10
     */
    public HandshakeV10 serverHandshake() {
        return serverHandshake;
    }

    /**
     * Returns the negotiated capabilities.
     *
     * @return the capabilities bitmask
     */
    public int capabilities() {
        return capabilities;
    }

    /**
     * Returns the input stream.
     *
     * @return the input stream
     */
    public InputStream inputStream() {
        return in;
    }

    /**
     * Returns the output stream.
     *
     * @return the output stream
     */
    public OutputStream outputStream() {
        return out;
    }

    /**
     * Checks if the connection is open.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return !socket.isClosed() && socket.isConnected();
    }

    @Override
    public void close() throws IOException {
        try {
            sendPacket(MysqlCodec.encodeQuit());
        } catch (IOException ignored) {}
        socket.close();
    }

    private AuthPlugin resolveAuthPlugin(String name) {
        return switch (name) {
            case "caching_sha2_password" -> CachingSha2Password.INSTANCE;
            default -> MysqlNativePassword.INSTANCE;
        };
    }

    /**
     * Exception thrown for MySQL protocol errors.
     */
    public static class MysqlException extends IOException {
        private final int errorCode;
        private final String sqlState;

        /**
         * Creates a new MySQL exception.
         *
         * @param errorCode the MySQL error code
         * @param sqlState the SQLSTATE value
         * @param message the error message
         */
        public MysqlException(int errorCode, String sqlState, String message) {
            super("MySQL error " + errorCode + " (" + sqlState + "): " + message);
            this.errorCode = errorCode;
            this.sqlState = sqlState;
        }

        /**
         * Returns the MySQL error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the SQLSTATE value.
         *
         * @return the SQLSTATE
         */
        public String sqlState() {
            return sqlState;
        }
    }
}
