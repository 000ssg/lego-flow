package ssg.legoflow.ssh.server;

import ssg.legoflow.ssh.auth.AuthContext;
import ssg.legoflow.ssh.auth.AuthResult;
import ssg.legoflow.ssh.hostkey.SshKeyPair;
import ssg.legoflow.ssh.kex.KexInit;
import ssg.legoflow.ssh.scp.ScpServer;
import ssg.legoflow.ssh.sftp.SftpServer;
import ssg.legoflow.ssh.transport.SshTransport;
import ssg.legoflow.ssh.transport.SshTransportCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * SSH server implementation supporting multiple concurrent connections.
 *
 * <p>Uses virtual threads for connection handling.
 *
 * @since 0.1.0
 */
public final class SshServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SshServer.class);

    private final SshServerConfig config;
    private SshKeyPair hostKey;
    private AuthContext authContext;
    private ShellFactory shellFactory;
    private CommandFactory commandFactory;
    private ForwardingFilter forwardingFilter = ForwardingFilter.denyAll();
    private Path rootDirectory;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    /** Latch for tests to wait until a new connection is accepted (signaled synchronously in accept loop). */
    private volatile CountDownLatch connectionLatch;
    private final ConcurrentHashMap<Integer, SshTransport> connections = new ConcurrentHashMap<>();

    /**
     * Creates a new SSH server with default configuration.
     */
    public SshServer() {
        this(SshServerConfig.defaults());
    }

    /**
     * Creates a new SSH server with the given configuration.
     *
     * @param config the server configuration
     */
    public SshServer(SshServerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Sets the host key for this server.
     *
     * @param hostKey the host key pair
     * @return this server for chaining
     */
    public SshServer setHostKey(SshKeyPair hostKey) {
        this.hostKey = Objects.requireNonNull(hostKey, "hostKey");
        return this;
    }

    /**
     * Sets the authenticator.
     *
     * @param authContext the authentication context
     * @return this server for chaining
     */
    public SshServer setAuthenticator(AuthContext authContext) {
        this.authContext = Objects.requireNonNull(authContext, "authContext");
        return this;
    }

    /**
     * Sets the shell factory.
     *
     * @param shellFactory the shell factory
     * @return this server for chaining
     */
    public SshServer setShellFactory(ShellFactory shellFactory) {
        this.shellFactory = shellFactory;
        return this;
    }

    /**
     * Sets the command factory.
     *
     * @param commandFactory the command factory
     * @return this server for chaining
     */
    public SshServer setCommandFactory(CommandFactory commandFactory) {
        this.commandFactory = commandFactory;
        return this;
    }

    /**
     * Sets the forwarding filter.
     *
     * @param filter the forwarding filter
     * @return this server for chaining
     */
    public SshServer setForwardingFilter(ForwardingFilter filter) {
        this.forwardingFilter = Objects.requireNonNull(filter, "filter");
        return this;
    }

    /**
     * Sets the root directory for SFTP and SCP file operations.
     *
     * @param rootDirectory the root directory
     * @return this server for chaining
     */
    public SshServer setRootDirectory(Path rootDirectory) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory");
        return this;
    }

    /**
     * Starts the SSH server and binds to the configured port.
     *
     * @throws IOException if binding fails
     */
    public void bind() throws IOException {
        bind(config.port());
    }

    /**
     * Starts the SSH server on the specified port.
     *
     * @param port the port to listen on
     * @throws IOException if binding fails
     */
    public void bind(int port) throws IOException {
        if (hostKey == null) {
            throw new IllegalStateException("Host key not set");
        }
        if (authContext == null) {
            throw new IllegalStateException("Authenticator not set");
        }

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(config.bindAddress(), port));
        running.set(true);
        executor = Executors.newVirtualThreadPerTaskExecutor();

        LOG.info("SSH server started on port {}", port);

        executor.submit(this::acceptLoop);
    }

    /**
     * Returns the actual port the server is listening on.
     *
     * @return the port number
     */
    public int port() {
        return serverSocket != null ? serverSocket.getLocalPort() : config.port();
    }

    /**
     * Returns whether the server is running.
     *
     * @return true if running
     */
    public boolean isRunning() { return running.get(); }

    /**
     * Returns the number of active connections.
     *
     * @return connection count
     */
    public int connectionCount() { return connectionCount.get(); }

    /** Reset the connection latch so tests can await the next accepted connection. */
    public void resetConnectionLatch() {
        this.connectionLatch = new CountDownLatch(1);
    }

    /** Await a single connection to be accepted by the server, up to timeoutMs. */
    public boolean awaitConnection(long timeoutMs) throws InterruptedException {
        var latch = this.connectionLatch;
        if (latch == null) {
            return false;
        }
        return latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        for (SshTransport transport : connections.values()) {
            try { transport.close(); } catch (IOException ignored) {}
        }
        connections.clear();
        if (executor != null) {
            executor.shutdownNow();
        }
        LOG.info("SSH server stopped");
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                // Increment count and signal latch synchronously in the accept thread
                // to avoid virtual-thread scheduling delays on CI runners.
                int connId = connectionCount.incrementAndGet();
                if (connectionLatch != null) {
                    connectionLatch.countDown();
                    connectionLatch = null;
                }
                if (connId > config.maxConcurrentConnections()) {
                    connectionCount.decrementAndGet();
                    clientSocket.close();
                    continue;
                }
                executor.submit(() -> handleConnection(clientSocket, connId));
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error accepting connection", e);
                }
            }
        }
    }

    private void handleConnection(Socket clientSocket, int connId) {
        try {
            SshTransport transport = new SshTransport(clientSocket, true);
            connections.put(connId, transport);

            // Version exchange
            transport.exchangeVersions();

            // Read client KEXINIT
            byte[] clientKexPayload = transport.readPacket();
            KexInit clientKexInit = KexInit.decode(clientKexPayload);

            // Send server KEXINIT
            KexInit serverKexInit = KexInit.defaultKexInit();
            transport.sendKexInit(serverKexInit);

            // Negotiate
            transport.negotiateAlgorithms(serverKexInit, clientKexInit);

            // Send NEWKEYS
            transport.sendNewKeys();
            transport.readPacket(); // read client NEWKEYS

            // Handle service request for ssh-userauth
            byte[] serviceReq = transport.readPacket();
            transport.sendServiceAccept("ssh-userauth");

            // Handle authentication
            byte[] authReq = transport.readPacket();
            String authenticatedUser = handleAuth(transport, authReq);
            if (authenticatedUser == null) {
                return; // auth failed, connection closed
            }
            LOG.info("Client '{}' authenticated, connection {}", authenticatedUser, connId);

            // Handle service request for ssh-connection
            byte[] connServiceReq = transport.readPacket();
            if (connServiceReq.length > 0 && (connServiceReq[0] & 0xFF) == 5) {
                transport.sendServiceAccept("ssh-connection");
            }

            // Process connection-layer packets
            processConnectionPackets(transport, connId);

        } catch (IOException e) {
            LOG.debug("Connection {} error: {}", connId, e.getMessage());
        } finally {
            connections.remove(connId);
            connectionCount.decrementAndGet();
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    private String handleAuth(SshTransport transport, byte[] authReq) throws IOException {
        if (authReq.length == 0) return null;
        ByteBuffer buf = ByteBuffer.wrap(authReq);
        buf.get(); // skip message type (50)
        String username = SshTransportCodec.readString(buf);
        String serviceName = SshTransportCodec.readString(buf);
        String method = SshTransportCodec.readString(buf);

        if ("password".equals(method) && authContext != null) {
            SshTransportCodec.readBoolean(buf); // skip 'new password' flag
            String password = SshTransportCodec.readString(buf);
            AuthResult result = authContext.authenticatePassword(username, password);
            if (result instanceof AuthResult.Success) {
                transport.sendPacket(new byte[]{52}); // SSH_MSG_USERAUTH_SUCCESS
                return username;
            } else {
                // Send SSH_MSG_USERAUTH_FAILURE with allowed methods
                var failure = (AuthResult.Failure) result;
                ByteBuffer failureBuf = ByteBuffer.allocate(256);
                failureBuf.put((byte) 51); // SSH_MSG_USERAUTH_FAILURE
                SshTransportCodec.writeNameList(failureBuf, failure.authMethodsThatCanContinue());
                failureBuf.put((byte) (failure.partialSuccess() ? 1 : 0));
                failureBuf.flip();
                byte[] failureData = new byte[failureBuf.remaining()];
                failureBuf.get(failureData);
                transport.sendPacket(failureData);
                return null; // Auth failed
            }
        }

        // Handle keyboard-interactive if auth context supports it
        if ("keyboard-interactive".equals(method) && authContext != null) {
            String submethods = SshTransportCodec.readString(buf);
            SshTransportCodec.readUint32(buf); // language tag length
            byte[] langTag = SshTransportCodec.readBinary(buf);
            // For now, respond with failure since keyboard-interactive is not fully implemented
            ByteBuffer failureBuf = ByteBuffer.allocate(256);
            failureBuf.put((byte) 51); // SSH_MSG_USERAUTH_FAILURE
            SshTransportCodec.writeNameList(failureBuf, java.util.List.copyOf(authContext.allowedMethods()));
            failureBuf.put((byte) 0); // no partial success
            failureBuf.flip();
            byte[] failureData = new byte[failureBuf.remaining()];
            failureBuf.get(failureData);
            transport.sendPacket(failureData);
            return null;
        }

        // Accept publickey auth if validator is set, otherwise respond with failure
        if ("publickey".equals(method) && authContext != null) {
            String keyType = SshTransportCodec.readString(buf);
            byte[] publicKeyBlob = SshTransportCodec.readBinary(buf);
            
            AuthResult result = authContext.authenticatePublicKey(username, 
                publicKeyBlob);
            if (result instanceof AuthResult.Success) {
                transport.sendPacket(new byte[]{52}); // SSH_MSG_USERAUTH_SUCCESS
                return username;
            } else {
                var failure = (AuthResult.Failure) result;
                ByteBuffer failureBuf = ByteBuffer.allocate(256);
                failureBuf.put((byte) 51); // SSH_MSG_USERAUTH_FAILURE
                SshTransportCodec.writeNameList(failureBuf, failure.authMethodsThatCanContinue());
                failureBuf.put((byte) (failure.partialSuccess() ? 1 : 0));
                failureBuf.flip();
                byte[] failureData = new byte[failureBuf.remaining()];
                failureBuf.get(failureData);
                transport.sendPacket(failureData);
                return null;
            }
        }

        // No matching auth method or no auth context configured
        return null;
    }
    private void processConnectionPackets(SshTransport transport, int connId) throws IOException {
        // Track server-side channels: channelId -> piped streams for communication
        ConcurrentHashMap<Integer, ChannelState> channels = new ConcurrentHashMap<>();
        AtomicInteger nextChannelId = new AtomicInteger(0);

        while (!transport.isClosed()) {
            try {
                byte[] packet = transport.readPacket();
                if (packet.length == 0) continue;
                int msgType = packet[0] & 0xFF;

                switch (msgType) {
                    case 1 -> { return; } // SSH_MSG_DISCONNECT
                    case 90 -> handleChannelOpen(transport, packet, channels, nextChannelId);
                    case 94 -> handleChannelData(transport, packet, channels);
                    case 96 -> handleChannelEof(transport, packet, channels);
                    case 97 -> handleChannelClose(transport, packet, channels);
                    case 98 -> handleChannelRequest(transport, packet, channels);
                    default -> LOG.debug("Server ignoring message type: {}", msgType);
                }
            } catch (IOException e) {
                break;
            }
        }
    }

    private void handleChannelOpen(SshTransport transport, byte[] packet,
                                   ConcurrentHashMap<Integer, ChannelState> channels,
                                   AtomicInteger nextChannelId) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(packet);
        buf.get(); // skip type
        String channelType = SshTransportCodec.readString(buf);
        int senderChannel = buf.getInt();
        long initialWindow = SshTransportCodec.readUint32(buf);
        long maxPacket = SshTransportCodec.readUint32(buf);

        int localId = nextChannelId.getAndIncrement();
        ChannelState state = new ChannelState(localId, senderChannel);
        channels.put(localId, state);

        if ("direct-tcpip".equals(channelType)) {
            // Parse direct-tcpip extra data
            String targetHost = SshTransportCodec.readString(buf);
            int targetPort = buf.getInt();
            String originatorAddress = SshTransportCodec.readString(buf);
            int originatorPort = buf.getInt();

            // Check forwarding filter
            if (forwardingFilter != null && !forwardingFilter.allow("", targetHost, targetPort)) {
                sendChannelOpenFailure(transport, senderChannel, 1, "Forwarding denied");
                channels.remove(localId);
                return;
            }
        }

        // Send channel open confirmation
        ByteBuffer reply = ByteBuffer.allocate(256);
        reply.put((byte) 91); // SSH_MSG_CHANNEL_OPEN_CONFIRMATION
        reply.putInt(senderChannel);
        reply.putInt(localId);
        reply.putInt(2 * 1024 * 1024); // initial window 2MB
        reply.putInt(32 * 1024); // max packet 32KB
        reply.flip();
        byte[] payload = new byte[reply.remaining()];
        reply.get(payload);
        transport.sendPacket(payload);

        LOG.debug("Opened channel {} (type={}, remote={})", localId, channelType, senderChannel);
    }

    private void handleChannelRequest(SshTransport transport, byte[] packet,
                                      ConcurrentHashMap<Integer, ChannelState> channels)
            throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(packet);
        buf.get(); // skip type
        int recipientChannel = buf.getInt();
        String requestType = SshTransportCodec.readString(buf);
        boolean wantReply = SshTransportCodec.readBoolean(buf);

        ChannelState state = findByRemoteId(channels, recipientChannel);
        if (state == null) {
            if (wantReply) sendChannelFailure(transport, recipientChannel);
            return;
        }

        LOG.debug("Channel request: type={}, channel={}", requestType, recipientChannel);

        switch (requestType) {
            case "exec" -> {
                String command = SshTransportCodec.readString(buf);
                if (wantReply) sendChannelSuccess(transport, state.remoteId);

                // Handle SCP commands
                if (command.startsWith("scp ")) {
                    handleScpCommand(transport, state, command, channels);
                } else if (commandFactory != null) {
                    // Execute via command factory
                    executeCommand(transport, state, command, channels);
                } else {
                    // Echo back the command
                    String result = "Executed: " + command + "\n";
                    sendChannelData(transport, state.remoteId,
                            result.getBytes(StandardCharsets.UTF_8));
                    sendChannelEof(transport, state.remoteId);
                    sendChannelClose(transport, state.remoteId);
                }
            }
            case "shell" -> {
                if (wantReply) sendChannelSuccess(transport, state.remoteId);

                if (shellFactory != null) {
                    executeShell(transport, state, channels);
                } else {
                    sendChannelData(transport, state.remoteId,
                            "No shell configured\n".getBytes(StandardCharsets.UTF_8));
                    sendChannelEof(transport, state.remoteId);
                }
            }
            case "pty-req" -> {
                // Accept PTY request
                if (wantReply) sendChannelSuccess(transport, state.remoteId);
            }
            case "subsystem" -> {
                String subsystem = SshTransportCodec.readString(buf);
                if ("sftp".equals(subsystem)) {
                    if (wantReply) sendChannelSuccess(transport, state.remoteId);
                    state.sftpMode = true;
                    state.sftpServer = new SftpServer(
                            rootDirectory != null ? rootDirectory : Path.of(System.getProperty("java.io.tmpdir")));
                } else {
                    if (wantReply) sendChannelFailure(transport, state.remoteId);
                }
            }
            default -> {
                if (wantReply) sendChannelSuccess(transport, state.remoteId);
            }
        }
    }

    private void handleChannelData(SshTransport transport, byte[] packet,
                                   ConcurrentHashMap<Integer, ChannelState> channels)
            throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(packet);
        buf.get(); // skip type
        int recipientChannel = buf.getInt();
        byte[] data = SshTransportCodec.readBinary(buf);

        ChannelState state = findByRemoteId(channels, recipientChannel);
        if (state == null) return;

        if (state.sftpMode && state.sftpServer != null) {
            // Forward to SFTP server
            byte[] response = state.sftpServer.handlePacket(data);
            sendChannelData(transport, state.remoteId, response);
        } else if (state.dataHandler != null) {
            state.dataHandler.accept(data);
        }
    }

    private void handleChannelEof(SshTransport transport, byte[] packet,
                                  ConcurrentHashMap<Integer, ChannelState> channels)
            throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(packet);
        buf.get(); // skip type
        int recipientChannel = buf.getInt();
        LOG.debug("Channel EOF received for {}", recipientChannel);
    }

    private void handleChannelClose(SshTransport transport, byte[] packet,
                                    ConcurrentHashMap<Integer, ChannelState> channels)
            throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(packet);
        buf.get(); // skip type
        int recipientChannel = buf.getInt();

        ChannelState state = findByRemoteId(channels, recipientChannel);
        if (state != null) {
            // Send close back
            sendChannelClose(transport, state.remoteId);
            channels.remove(state.localId);
            LOG.debug("Channel {} closed", state.localId);
        }
    }

    private void executeCommand(SshTransport transport, ChannelState state, String command,
                                ConcurrentHashMap<Integer, ChannelState> channels) {
        executor.submit(() -> {
            try {
                PipedOutputStream cmdOut = new PipedOutputStream();
                PipedInputStream cmdOutRead = new PipedInputStream(cmdOut);
                PipedOutputStream cmdErr = new PipedOutputStream();
                PipedInputStream cmdErrRead = new PipedInputStream(cmdErr);

                // Start reader thread for stdout
                executor.submit(() -> {
                    try {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = cmdOutRead.read(buf)) != -1) {
                            byte[] chunk = new byte[n];
                            System.arraycopy(buf, 0, chunk, 0, n);
                            sendChannelData(transport, state.remoteId, chunk);
                        }
                    } catch (IOException ignored) {}
                });

                int exitCode = commandFactory.executeCommand(command, cmdOut, cmdErr);
                cmdOut.close();
                cmdErr.close();

                Thread.sleep(50); // Allow reader thread to flush

                // Send exit status
                sendExitStatus(transport, state.remoteId, exitCode);
                sendChannelEof(transport, state.remoteId);
            } catch (Exception e) {
                LOG.debug("Command execution error: {}", e.getMessage());
            }
        });
    }

    private void executeShell(SshTransport transport, ChannelState state,
                              ConcurrentHashMap<Integer, ChannelState> channels) {
        PipedOutputStream shellInWrite;
        try {
            PipedInputStream shellIn = new PipedInputStream();
            shellInWrite = new PipedOutputStream(shellIn);
            PipedOutputStream shellOut = new PipedOutputStream();
            PipedInputStream shellOutRead = new PipedInputStream(shellOut);
            PipedOutputStream shellErr = new PipedOutputStream();

            // Set up data handler to forward client data to shell stdin
            state.dataHandler = data -> {
                try {
                    shellInWrite.write(data);
                    shellInWrite.flush();
                } catch (IOException ignored) {}
            };

            // Start reader thread for shell stdout
            executor.submit(() -> {
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = shellOutRead.read(buf)) != -1) {
                        byte[] chunk = new byte[n];
                        System.arraycopy(buf, 0, chunk, 0, n);
                        sendChannelData(transport, state.remoteId, chunk);
                    }
                } catch (IOException ignored) {}
            });

            // Run shell on a separate thread
            executor.submit(() -> {
                try {
                    shellFactory.createShell(shellIn, shellOut, shellErr);
                    shellOut.close();
                    shellErr.close();
                } catch (Exception e) {
                    LOG.debug("Shell error: {}", e.getMessage());
                }
            });
        } catch (IOException e) {
            LOG.debug("Shell setup error: {}", e.getMessage());
        }
    }

    private void handleScpCommand(SshTransport transport, ChannelState state, String command,
                                  ConcurrentHashMap<Integer, ChannelState> channels) {
        Path scpRoot = rootDirectory != null ? rootDirectory : Path.of(System.getProperty("java.io.tmpdir"));
        ScpServer scpServer = new ScpServer(scpRoot);

        // Parse SCP command: scp [-tp|-f|-pf] <path>
        boolean isSink = command.contains(" -t ") || command.contains(" -tp ");
        boolean isSource = command.contains(" -f ") || command.contains(" -pf ");
        String remotePath = command.substring(command.lastIndexOf(' ') + 1);

        // Set up piped streams for SCP protocol communication
        PipedOutputStream scpInWrite;
        try {
            PipedInputStream scpIn = new PipedInputStream(65536);
            scpInWrite = new PipedOutputStream(scpIn);
            PipedOutputStream scpOut = new PipedOutputStream();
            PipedInputStream scpOutRead = new PipedInputStream(scpOut, 65536);

            // Forward client data to SCP stdin
            state.dataHandler = data -> {
                try {
                    scpInWrite.write(data);
                    scpInWrite.flush();
                } catch (IOException ignored) {}
            };

            // Reader thread: SCP stdout -> channel data to client
            executor.submit(() -> {
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = scpOutRead.read(buf)) != -1) {
                        byte[] chunk = new byte[n];
                        System.arraycopy(buf, 0, chunk, 0, n);
                        sendChannelData(transport, state.remoteId, chunk);
                    }
                } catch (IOException ignored) {}
            });

            // Run SCP handler on a separate thread
            executor.submit(() -> {
                try {
                    if (isSink) {
                        scpServer.handleSink(remotePath, scpIn, scpOut);
                    } else if (isSource) {
                        scpServer.handleSource(remotePath, scpIn, scpOut);
                    }
                    scpOut.close();
                    Thread.sleep(50);
                    sendChannelEof(transport, state.remoteId);
                } catch (Exception e) {
                    LOG.debug("SCP error: {}", e.getMessage());
                }
            });
        } catch (IOException e) {
            LOG.debug("SCP setup error: {}", e.getMessage());
        }
    }

    // --- Packet helpers ---

    private void sendChannelData(SshTransport transport, int remoteId, byte[] data) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(9 + data.length);
        buf.put((byte) 94); // SSH_MSG_CHANNEL_DATA
        buf.putInt(remoteId);
        SshTransportCodec.writeBinary(buf, data);
        buf.flip();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        transport.sendPacket(payload);
    }

    private void sendChannelEof(SshTransport transport, int remoteId) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(5);
        buf.put((byte) 96); // SSH_MSG_CHANNEL_EOF
        buf.putInt(remoteId);
        transport.sendPacket(buf.array());
    }

    private void sendChannelClose(SshTransport transport, int remoteId) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(5);
        buf.put((byte) 97); // SSH_MSG_CHANNEL_CLOSE
        buf.putInt(remoteId);
        transport.sendPacket(buf.array());
    }

    private void sendChannelSuccess(SshTransport transport, int remoteId) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(5);
        buf.put((byte) 99); // SSH_MSG_CHANNEL_SUCCESS
        buf.putInt(remoteId);
        transport.sendPacket(buf.array());
    }

    private void sendChannelFailure(SshTransport transport, int remoteId) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(5);
        buf.put((byte) 100); // SSH_MSG_CHANNEL_FAILURE
        buf.putInt(remoteId);
        transport.sendPacket(buf.array());
    }

    private void sendChannelOpenFailure(SshTransport transport, int remoteId, int reason,
                                        String description) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(256);
        buf.put((byte) 92); // SSH_MSG_CHANNEL_OPEN_FAILURE
        buf.putInt(remoteId);
        buf.putInt(reason);
        SshTransportCodec.writeString(buf, description);
        SshTransportCodec.writeString(buf, "en");
        buf.flip();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        transport.sendPacket(payload);
    }

    private void sendExitStatus(SshTransport transport, int remoteId, int exitCode) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(64);
        buf.put((byte) 98); // SSH_MSG_CHANNEL_REQUEST
        buf.putInt(remoteId);
        SshTransportCodec.writeString(buf, "exit-status");
        SshTransportCodec.writeBoolean(buf, false);
        buf.putInt(exitCode);
        buf.flip();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        transport.sendPacket(payload);
    }

    private ChannelState findByRemoteId(ConcurrentHashMap<Integer, ChannelState> channels,
                                        int remoteId) {
        for (ChannelState state : channels.values()) {
            if (state.remoteId == remoteId) return state;
        }
        return null;
    }

    /**
     * Tracks server-side channel state.
     */
    private static final class ChannelState {
        final int localId;
        final int remoteId;
        volatile boolean sftpMode;
        volatile SftpServer sftpServer;
        volatile java.util.function.Consumer<byte[]> dataHandler;

        ChannelState(int localId, int remoteId) {
            this.localId = localId;
            this.remoteId = remoteId;
        }
    }
}
