package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.protocol.*;
import ssg.legoflow.ftp.security.FtpsHandler;
import ssg.legoflow.ftp.security.FtpsMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * FTP server implementation supporting RFC 959 and RFC 4217 (FTPS).
 *
 * <p>Accepts client connections on a control port, manages sessions, and handles
 * FTP commands including directory listings and file transfers. Uses virtual threads
 * for concurrent connection handling.
 *
 * <p>Usage example:
 * <pre>{@code
 *   var config = FtpServerConfig.builder()
 *       .host("0.0.0.0")
 *       .port(2121)
 *       .build();
 *   var fs = new InMemoryFileSystem();
 *   try (var server = new FtpServer(config)) {
 *       server.setFileSystem(fs);
 *       server.setAuthenticator(FtpAuthenticator.anonymous());
 *       server.start();
 *       // server is now accepting connections
 *   }
 * }</pre>
 *
 * @since 0.1.0
 */
public final class FtpServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(FtpServer.class);

    private final FtpServerConfig config;
    private volatile FtpFileSystem fileSystem;
    private volatile FtpAuthenticator authenticator = FtpAuthenticator.acceptAll();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private final Map<String, FtpSession> sessions = new ConcurrentHashMap<>();
    private volatile ServerSocket serverSocket;
    private volatile int boundPort;

    /**
     * Creates an FTP server with the given configuration.
     *
     * @param config the server configuration
     */
    public FtpServer(FtpServerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Sets the filesystem for the server.
     *
     * @param fileSystem the filesystem implementation
     */
    public void setFileSystem(FtpFileSystem fileSystem) {
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
    }

    /**
     * Sets the authenticator for the server.
     *
     * @param authenticator the authenticator
     */
    public void setAuthenticator(FtpAuthenticator authenticator) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
    }

    /**
     * Starts the server and binds to the configured port.
     *
     * @throws IOException if the server cannot bind
     */
    public void start() throws IOException {
        if (fileSystem == null) {
            throw new IllegalStateException("FileSystem must be set before starting");
        }
        bind(config.port());
    }

    /**
     * Binds the server to a specific port.
     *
     * @param port the port (0 for ephemeral)
     * @throws IOException if binding fails
     */
    public void bind(int port) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(config.host(), port));
        boundPort = serverSocket.getLocalPort();
        running.set(true);
        LOG.info("FTP server started on {}:{}", config.host(), boundPort);
        executor.submit(this::acceptLoop);
    }

    /**
     * Stops the server and disconnects all clients.
     */
    public void stop() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOG.debug("Error closing server socket", e);
            }
        }
        executor.shutdownNow();
        sessions.clear();
        LOG.info("FTP server stopped");
    }

    /**
     * Returns the port the server is bound to.
     *
     * @return the bound port
     */
    public int getPort() {
        return boundPort;
    }

    /**
     * Returns whether the server is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Returns the current number of active connections.
     *
     * @return the connection count
     */
    public int getConnectionCount() {
        return connectionCount.get();
    }

    /**
     * Returns the active sessions.
     *
     * @return unmodifiable map of session IDs to sessions
     */
    public Map<String, FtpSession> getSessions() {
        return Map.copyOf(sessions);
    }

    @Override
    public void close() {
        stop();
    }

    // ---- Private ----

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                if (connectionCount.get() >= config.maxConnections()) {
                    rejectConnection(clientSocket);
                    continue;
                }
                connectionCount.incrementAndGet();
                executor.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error accepting connection", e);
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        String clientAddr = clientSocket.getRemoteSocketAddress().toString();
        FtpSession session = new FtpSession(clientAddr);
        sessions.put(clientAddr, session);
        LOG.info("Client connected: {}", clientAddr);

        try (clientSocket) {
            Socket activeSocket = clientSocket;

            // Implicit FTPS: perform TLS handshake immediately on connect
            if (config.isFtpsEnabled() && config.ftpsConfig().mode() == FtpsMode.IMPLICIT) {
                try {
                    var ftpsHandler = new FtpsHandler(config.ftpsConfig());
                    activeSocket = ftpsHandler.upgradeToTlsServer(clientSocket);
                    session.setTlsEnabled(true);
                    session.setDataProtected(true);
                    LOG.info("Implicit FTPS: TLS handshake complete for {}", clientAddr);
                } catch (Exception e) {
                    LOG.error("Implicit FTPS handshake failed for {}: {}", clientAddr, e.getMessage());
                    return;
                }
            }

            activeSocket.setSoTimeout((int) config.sessionTimeout().toMillis());
            var reader = new BufferedReader(
                    new InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream writer = activeSocket.getOutputStream();

            // Send greeting
            FtpReply greeting = FtpReply.of(FtpReplyCode.SERVICE_READY, config.serverName() + " ready");
            FtpProtocolCodec.writeReply(writer, greeting);

            FtpCommandHandler handler = new FtpCommandHandler(session, fileSystem, authenticator, config);

            // Command loop
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] parsed = FtpProtocolCodec.decodeCommand(line);
                String cmdStr = parsed[0];
                String argument = parsed[1];

                FtpCommand command;
                try {
                    command = FtpCommand.parse(cmdStr);
                } catch (IllegalArgumentException e) {
                    FtpProtocolCodec.writeReply(writer,
                            FtpReply.of(FtpReplyCode.SYNTAX_ERROR, "Unknown command: " + cmdStr));
                    continue;
                }

                FtpReply reply = handler.handle(command, argument, clientSocket);
                FtpProtocolCodec.writeReply(writer, reply);

                // Handle data transfer commands
                if (reply.code() == 150 || reply.code() == 125) {
                    handleDataCommand(command, argument, handler, clientSocket, writer, session);
                }

                if (command == FtpCommand.QUIT) {
                    break;
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                LOG.debug("Client {} error: {}", clientAddr, e.getMessage());
            }
        } finally {
            sessions.remove(clientAddr);
            connectionCount.decrementAndGet();
            LOG.info("Client disconnected: {}", clientAddr);
        }
    }

    private void handleDataCommand(FtpCommand command, String argument,
                                   FtpCommandHandler handler, Socket clientSocket,
                                   OutputStream writer, FtpSession session) {
        try {
            switch (command) {
                case LIST -> {
                    String listData = handler.generateListOutput(argument);
                    handler.performDataTransfer(clientSocket, listData, writer);
                }
                case NLST -> {
                    String nlstData = handler.generateNlstOutput(argument);
                    handler.performDataTransfer(clientSocket, nlstData, writer);
                }
                case MLSD -> {
                    String mlsdData = handler.generateMlsdOutput(argument);
                    handler.performDataTransfer(clientSocket, mlsdData, writer);
                }
                case RETR -> handler.performRetrieve(clientSocket, argument, writer);
                case STOR -> handler.performStore(clientSocket, argument, false, writer);
                case STOU -> handler.performStore(clientSocket, java.util.UUID.randomUUID().toString(), false, writer);
                case APPE -> handler.performStore(clientSocket, argument, true, writer);
                default -> { /* no data transfer */ }
            }
            FtpProtocolCodec.writeReply(writer,
                    FtpReply.of(FtpReplyCode.CLOSING_DATA_CONNECTION, "Transfer complete"));
        } catch (IOException e) {
            LOG.error("Data transfer error for {}: {}", command, e.getMessage());
            try {
                FtpProtocolCodec.writeReply(writer,
                        FtpReply.of(FtpReplyCode.CONNECTION_CLOSED_TRANSFER_ABORTED, e.getMessage()));
            } catch (IOException ignored) {}
        }
    }

    private void rejectConnection(Socket socket) {
        try (socket) {
            FtpProtocolCodec.writeReply(socket.getOutputStream(),
                    FtpReply.of(FtpReplyCode.SERVICE_NOT_AVAILABLE, "Too many connections"));
        } catch (IOException ignored) {}
    }
}
