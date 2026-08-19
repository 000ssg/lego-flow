package ssg.legoflow.ssh.client;

import ssg.legoflow.ssh.auth.AuthMethod;
import ssg.legoflow.ssh.auth.AuthResult;
import ssg.legoflow.ssh.connection.*;
import ssg.legoflow.ssh.kex.KexInit;
import ssg.legoflow.ssh.transport.SshTransport;
import ssg.legoflow.ssh.transport.SshTransportCodec;
import ssg.legoflow.ssh.transport.SshVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
/**
 * SSH client implementation providing connect, authenticate, and session management.
 *
 * <p>Usage:
 * <pre>{@code
 * try (SshClient client = new SshClient()) {
 *     client.connect("example.com", 22);
 *     client.authenticate("user", new PasswordAuth("secret"));
 *     SessionChannel session = client.openSession();
 *     session.requestExec("ls -la");
 *     // read output...
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public final class SshClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SshClient.class);

    private final SshClientConfig config;
    private SshTransport transport;
    private SshConnection connection;
    private boolean authenticated;
    private volatile Thread readerThread;

    /**
     * Creates a new SSH client with default configuration.
     */
    public SshClient() {
        this(SshClientConfig.defaults());
    }

    /**
     * Creates a new SSH client with the given configuration.
     *
     * @param config the client configuration
     */
    public SshClient(SshClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Connects to an SSH server.
     *
     * @param host the server hostname
     * @param port the server port
     * @throws IOException if connection fails
     */
    public void connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port),
                (int) config.connectTimeout().toMillis());
        socket.setSoTimeout((int) config.connectTimeout().toMillis());

        transport = new SshTransport(socket, false);

        // Version exchange
        SshVersion remoteVersion = transport.exchangeVersions();
        LOG.info("Connected to {} ({})", host, remoteVersion);

        // Send KEXINIT
        KexInit localKexInit = KexInit.defaultKexInit();
        transport.sendKexInit(localKexInit);

        // Read remote KEXINIT
        byte[] remoteKexPayload = transport.readPacket();
        KexInit remoteKexInit = KexInit.decode(remoteKexPayload);

        // Negotiate algorithms
        transport.negotiateAlgorithms(localKexInit, remoteKexInit);

        // Send NEWKEYS
        transport.sendNewKeys();

        // Read NEWKEYS
        byte[] newKeysPayload = transport.readPacket();
        LOG.debug("Key exchange completed");

        // Request ssh-userauth service
        transport.sendServiceRequest("ssh-userauth");
        byte[] serviceAccept = transport.readPacket();

        connection = new SshConnection(transport);
    }

    /**
     * Authenticates with the server.
     *
     * @param username the username
     * @param method   the authentication method
     * @return the authentication result
     * @throws IOException if an I/O error occurs
     */
    public AuthResult authenticate(String username, AuthMethod method) throws IOException {
        byte[] request = method.encodeRequest(username, "ssh-connection");
        transport.sendPacket(request);

        byte[] response = transport.readPacket();
        if (response.length == 0) {
            throw new IOException("Empty authentication response");
        }

        int msgType = response[0] & 0xFF;
        return switch (msgType) {
            case 52 -> { // SSH_MSG_USERAUTH_SUCCESS
                authenticated = true;
                // Request ssh-connection service
                transport.sendServiceRequest("ssh-connection");
                transport.readPacket(); // service accept
                // Start background reader for connection-layer packets
                startReaderThread();
                yield new AuthResult.Success();
            }
            case 51 -> { // SSH_MSG_USERAUTH_FAILURE
                ByteBuffer buf = ByteBuffer.wrap(response);
                buf.get(); // skip type
                List<String> methods = SshTransportCodec.readNameList(buf);
                boolean partial = SshTransportCodec.readBoolean(buf);
                yield new AuthResult.Failure(methods, partial);
            }
            default -> new AuthResult.Failure(List.of(), false);
        };
    }

    /**
     * Opens a new session channel.
     *
     * @return the session channel
     * @throws IOException if not authenticated or I/O error occurs
     */
    public SessionChannel openSession() throws IOException {
        ensureAuthenticated();
        return connection.openSession();
    }

    /**
     * Creates a local port forward.
     *
     * @param localPort  the local port to listen on
     * @param remoteHost the remote host to forward to
     * @param remotePort the remote port
     * @return the direct-tcpip channel
     * @throws IOException if an I/O error occurs
     */
    public DirectTcpIpChannel createLocalForward(int localPort, String remoteHost, int remotePort)
            throws IOException {
        ensureAuthenticated();
        return connection.openDirectTcpIp(remoteHost, remotePort, "127.0.0.1", localPort);
    }

    /**
     * Creates a remote port forward.
     *
     * @param remotePort the remote port to listen on
     * @param localHost  the local host to forward to
     * @param localPort  the local port
     * @throws IOException if an I/O error occurs
     */
    public void createRemoteForward(int remotePort, String localHost, int localPort)
            throws IOException {
        ensureAuthenticated();
        byte[] request = GlobalRequest.encodeTcpIpForward("", remotePort);
        transport.sendPacket(request);
    }

    /**
     * Opens an SFTP subsystem channel.
     *
     * @return the session channel configured for SFTP
     * @throws IOException if an I/O error occurs
     */
    public SessionChannel openSftpChannel() throws IOException {
        SessionChannel channel = openSession();
        channel.requestSubsystem("sftp");
        return channel;
    }

    /**
     * Disconnects from the server.
     *
     * @throws IOException if an I/O error occurs
     */
    public void disconnect() throws IOException {
        if (connection != null) {
            connection.close();
        }
        if (transport != null) {
            transport.disconnect(11, "disconnected by user");
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
    }

    /**
     * Returns whether the client is authenticated.
     *
     * @return true if authenticated
     */
    public boolean isAuthenticated() { return authenticated; }

    /**
     * Returns whether the client is connected.
     *
     * @return true if connected
     */
    public boolean isConnected() { return transport != null && !transport.isClosed(); }

    /**
     * Returns the transport layer.
     *
     * @return the transport
     */
    public SshTransport transport() { return transport; }

    /**
     * Returns the connection layer.
     *
     * @return the connection
     */
    public SshConnection connection() { return connection; }

    /**
     * Returns the client configuration.
     *
     * @return the config
     */
    public SshClientConfig config() { return config; }

    @Override
    public void close() throws IOException {
        disconnect();
    }

    private void startReaderThread() {
        readerThread = Thread.ofVirtual().name("ssh-client-reader").start(() -> {
            while (!transport.isClosed()) {
                try {
                    byte[] packet = transport.readPacket();
                    if (packet.length > 0) {
                        int msgType = packet[0] & 0xFF;
                        if (msgType == 1) { // DISCONNECT
                            break;
                        }
                        if (connection != null && msgType >= 80) {
                            connection.handlePacket(packet);
                        }
                    }
                } catch (IOException e) {
                    if (!transport.isClosed()) {
                        LOG.debug("Reader thread error: {}", e.getMessage());
                    }
                    break;
                }
            }
        });
    }

    private void ensureAuthenticated() throws IOException {
        if (!authenticated) {
            throw new IOException("Not authenticated");
        }
    }
}
