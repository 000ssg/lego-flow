package ssg.legoflow.database.mysql.client;

import ssg.legoflow.database.mysql.auth.*;
import ssg.legoflow.database.mysql.protocol.*;
import ssg.legoflow.database.mysql.transport.MysqlTransport;
import ssg.legoflow.database.mysql.transport.MysqlTransportStreamAdapter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MySQL connection lifecycle using a {@link MysqlTransport}.
 *
 * <p>Handles handshake, authentication, and provides low-level packet send/receive.
 *
 * @since 0.3.0
 */
public class MysqlConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MysqlConnection.class);

    private final MysqlTransport transport;
    private final InputStream in;
    private final OutputStream out;

    private HandshakeV10 serverHandshake;
    private int capabilities;
    private int sequenceId;

    /**
     * Creates a new connection from a MysqlTransport.
     */
    public MysqlConnection(MysqlTransport transport) throws IOException {
        this.transport = transport;
        var adapter = new MysqlTransportStreamAdapter(transport);
        this.in = adapter.getInputStream();
        this.out = adapter.getOutputStream();
    }

    /**
     * Performs the handshake and authentication.
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

        // Handle AuthMoreData
        if (AuthSwitchRequest.isAuthMoreData(authPayload)) {
            byte status = authPayload[1];
            if (status == CachingSha2Password.FAST_AUTH_SUCCESS) {
                authResult = MysqlPacket.readFrom(in);
                authPayload = authResult.payload();
                sequenceId = authResult.sequenceId() + 1;
            }
        }

        if (MysqlCodec.isErr(authPayload)) {
            var err = ErrPacket.decode(authPayload, capabilities);
            throw new MysqlException(err.errorCode(), err.sqlState(), err.message());
        }

        if (!MysqlCodec.isOk(authPayload)) {
            throw new MysqlException(0, "HY000", "Unexpected auth response: 0x"
                    + Integer.toHexString(authPayload[0] & 0xFF));
        }

        LOG.debug("Connected as {} (database: {})", username, database);
    }

    /**
     * Sends a packet to the server.
     */
    public void sendPacket(byte[] payload) throws IOException {
        sequenceId = 0;
        new MysqlPacket(sequenceId, payload).writeTo(out);
        out.flush();
    }

    /**
     * Receives a packet from the server.
     */
    public MysqlPacket receivePacket() throws IOException {
        var packet = MysqlPacket.readFrom(in);
        sequenceId = packet.sequenceId() + 1;
        return packet;
    }

    /**
     * Returns the server handshake.
     */
    public HandshakeV10 serverHandshake() {
        return serverHandshake;
    }

    /**
     * Returns the negotiated capabilities.
     */
    public int capabilities() {
        return capabilities;
    }

    /**
     * Returns the input stream.
     */
    public InputStream inputStream() {
        return in;
    }

    /**
     * Returns the output stream.
     */
    public OutputStream outputStream() {
        return out;
    }

    /**
     * Checks if the connection is open.
     */
    public boolean isConnected() {
        return transport.isOpen();
    }

    @Override
    public void close() throws IOException {
        try {
            sendPacket(MysqlCodec.encodeQuit());
        } catch (IOException ignored) {}
        transport.close();
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

        public MysqlException(int errorCode, String sqlState, String message) {
            super("MySQL error " + errorCode + " (" + sqlState + "): " + message);
            this.errorCode = errorCode;
            this.sqlState = sqlState;
        }

        public int errorCode() {
            return errorCode;
        }

        public String sqlState() {
            return sqlState;
        }
    }
}
