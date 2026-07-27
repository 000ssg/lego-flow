package ssg.legoflow.ftp.client;

import ssg.legoflow.ftp.data.*;
import ssg.legoflow.ftp.protocol.*;
import ssg.legoflow.ftp.security.FtpsConfig;
import ssg.legoflow.ftp.security.FtpsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Full FTP client implementation supporting RFC 959 and RFC 4217 (FTPS).
 *
 * <p>Provides high-level file transfer operations, directory listing,
 * and session management. Supports both active and passive data modes,
 * ASCII and binary transfers, and TLS encryption.
 *
 * <p>Usage example:
 * <pre>{@code
 *   try (var client = new FtpClient()) {
 *       client.connect("ftp.example.com", 21);
 *       client.login("user", "pass");
 *       client.setPassiveMode(true);
 *       client.setTransferType(FtpTransferType.BINARY);
 *       client.get("/remote/file.zip", Path.of("local/file.zip"));
 *   }
 * }</pre>
 *
 * @since 1.0.0
 */
public final class FtpClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(FtpClient.class);

    private final FtpClientConfig config;
    private volatile Socket controlSocket;
    private volatile BufferedReader reader;
    private volatile OutputStream writer;
    private volatile boolean connected = false;
    private volatile boolean loggedIn = false;
    private volatile boolean passiveMode;
    private volatile FtpTransferType transferType = FtpTransferType.BINARY;
    private volatile FtpsHandler ftpsHandler;
    private volatile String host;
    private volatile int port;

    /**
     * Creates an FTP client with default configuration.
     */
    public FtpClient() {
        this(FtpClientConfig.defaults());
    }

    /**
     * Creates an FTP client with the given configuration.
     *
     * @param config the client configuration
     */
    public FtpClient(FtpClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.passiveMode = config.passiveMode();
    }

    /**
     * Connects to an FTP server.
     *
     * @param host the server hostname
     * @param port the server port (usually 21)
     * @return the server greeting reply
     * @throws IOException if the connection fails
     */
    public FtpReply connect(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        controlSocket = new Socket();
        controlSocket.setSoTimeout((int) config.soTimeout().toMillis());
        controlSocket.connect(new InetSocketAddress(host, port), (int) config.connectTimeout().toMillis());
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
        writer = controlSocket.getOutputStream();
        connected = true;
        FtpReply greeting = FtpProtocolCodec.readReply(reader);
        LOG.info("Connected to {}:{} — {}", host, port, greeting.text());
        return greeting;
    }

    /**
     * Disconnects from the server, sending QUIT first if connected.
     *
     * @throws IOException if an I/O error occurs
     */
    public void disconnect() throws IOException {
        try {
            if (connected && loggedIn) {
                sendCommand(FtpCommand.QUIT, null);
            }
        } finally {
            loggedIn = false;
            connected = false;
            if (controlSocket != null) {
                controlSocket.close();
            }
        }
    }

    /**
     * Logs in with a username and password.
     *
     * @param username the username
     * @param password the password
     * @return the server reply to the PASS command
     * @throws IOException if login fails
     */
    public FtpReply login(String username, String password) throws IOException {
        FtpReply userReply = sendCommand(FtpCommand.USER, username);
        if (userReply.code() == 230) {
            loggedIn = true;
            return userReply;
        }
        if (userReply.code() != 331) {
            throw new IOException("USER command failed: " + userReply);
        }
        FtpReply passReply = sendCommand(FtpCommand.PASS, password);
        if (passReply.code() != 230) {
            throw new IOException("Login failed: " + passReply);
        }
        loggedIn = true;
        LOG.info("Logged in as {}", username);
        return passReply;
    }

    /**
     * Logs in anonymously.
     *
     * @return the server reply
     * @throws IOException if login fails
     */
    public FtpReply loginAnonymous() throws IOException {
        return login("anonymous", "anonymous@example.com");
    }

    /**
     * Changes the current working directory.
     *
     * @param path the target directory
     * @return the server reply
     * @throws IOException if the command fails
     */
    public FtpReply cd(String path) throws IOException {
        return sendCommand(FtpCommand.CWD, path);
    }

    /**
     * Changes to the parent directory.
     *
     * @return the server reply
     * @throws IOException if the command fails
     */
    public FtpReply cdup() throws IOException {
        return sendCommand(FtpCommand.CDUP, null);
    }

    /**
     * Returns the current working directory.
     *
     * @return the current directory path
     * @throws IOException if the command fails
     */
    public String pwd() throws IOException {
        FtpReply reply = sendCommand(FtpCommand.PWD, null);
        if (reply.code() != 257) {
            throw new IOException("PWD failed: " + reply);
        }
        // Extract path from "path" or response
        String text = reply.text();
        int start = text.indexOf('"');
        int end = text.indexOf('"', start + 1);
        if (start >= 0 && end > start) {
            return text.substring(start + 1, end);
        }
        return text;
    }

    /**
     * Creates a directory.
     *
     * @param path the directory path
     * @return the server reply
     * @throws IOException if the command fails
     */
    public FtpReply mkdir(String path) throws IOException {
        return sendCommand(FtpCommand.MKD, path);
    }

    /**
     * Removes a directory.
     *
     * @param path the directory path
     * @return the server reply
     * @throws IOException if the command fails
     */
    public FtpReply rmdir(String path) throws IOException {
        return sendCommand(FtpCommand.RMD, path);
    }

    /**
     * Lists files in the given path using LIST.
     *
     * @param path the directory path (null for current directory)
     * @return the parsed file entries
     * @throws IOException if the command fails
     */
    public List<FtpFileEntry> list(String path) throws IOException {
        String data = transferData(FtpCommand.LIST, path, true);
        return FtpListParser.parse(data);
    }

    /**
     * Lists file names in the given path using NLST.
     *
     * @param path the directory path (null for current directory)
     * @return the file names
     * @throws IOException if the command fails
     */
    public List<String> nlst(String path) throws IOException {
        String data = transferData(FtpCommand.NLST, path, true);
        if (data == null || data.isBlank()) return List.of();
        List<String> names = new ArrayList<>();
        for (String line : data.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return names;
    }

    /**
     * Lists files using MLSD (machine-readable format, RFC 3659).
     *
     * @param path the directory path (null for current directory)
     * @return the parsed file entries
     * @throws IOException if the command fails
     */
    public List<FtpFileEntry> mlsd(String path) throws IOException {
        String data = transferData(FtpCommand.MLSD, path, true);
        return MlsdParser.parse(data);
    }

    /**
     * Downloads a remote file to a local path.
     *
     * @param remotePath the remote file path
     * @param localPath  the local destination path
     * @return the number of bytes transferred
     * @throws IOException if the transfer fails
     */
    public long get(String remotePath, Path localPath) throws IOException {
        try (OutputStream fos = Files.newOutputStream(localPath)) {
            return getToStream(remotePath, fos);
        }
    }

    /**
     * Downloads a remote file to an output stream.
     *
     * @param remotePath the remote file path
     * @param dest       the destination output stream
     * @return the number of bytes transferred
     * @throws IOException if the transfer fails
     */
    public long getToStream(String remotePath, OutputStream dest) throws IOException {
        try (DataConnection dataConn = openDataConnection()) {
            FtpReply reply = sendCommand(FtpCommand.RETR, remotePath);
            if (reply.code() != 150 && reply.code() != 125) {
                throw new IOException("RETR failed: " + reply);
            }
            dataConn.open();
            DataTransfer transfer = new DataTransfer(transferType);
            long bytes = transfer.receive(dataConn.getInputStream(), dest);
            dataConn.close();
            FtpReply endReply = FtpProtocolCodec.readReply(reader);
            if (endReply != null && endReply.code() != 226 && endReply.code() != 250) {
                LOG.warn("Transfer end reply: {}", endReply);
            }
            return bytes;
        }
    }

    /**
     * Downloads a remote file and returns an input stream.
     *
     * @param remotePath the remote file path
     * @return the file data as an input stream (caller must close)
     * @throws IOException if the transfer fails
     */
    public InputStream get(String remotePath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        getToStream(remotePath, baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Uploads a local file to the server.
     *
     * @param localPath  the local file path
     * @param remotePath the remote destination path
     * @return the number of bytes transferred
     * @throws IOException if the transfer fails
     */
    public long put(Path localPath, String remotePath) throws IOException {
        try (InputStream fis = Files.newInputStream(localPath)) {
            return putFromStream(fis, remotePath);
        }
    }

    /**
     * Uploads data from an input stream to the server.
     *
     * @param source     the data source
     * @param remotePath the remote destination path
     * @return the number of bytes transferred
     * @throws IOException if the transfer fails
     */
    public long put(InputStream source, String remotePath) throws IOException {
        return putFromStream(source, remotePath);
    }

    /**
     * Appends data to an existing remote file.
     *
     * @param localPath  the local file path
     * @param remotePath the remote destination path
     * @return the number of bytes transferred
     * @throws IOException if the transfer fails
     */
    public long append(Path localPath, String remotePath) throws IOException {
        try (InputStream fis = Files.newInputStream(localPath)) {
            return appendFromStream(fis, remotePath);
        }
    }

    /**
     * Deletes a remote file.
     *
     * @param remotePath the remote file path
     * @return the server reply
     * @throws IOException if the command fails
     */
    public FtpReply delete(String remotePath) throws IOException {
        return sendCommand(FtpCommand.DELE, remotePath);
    }

    /**
     * Renames a remote file or directory.
     *
     * @param from the current name
     * @param to   the new name
     * @return the server reply for RNTO
     * @throws IOException if the command fails
     */
    public FtpReply rename(String from, String to) throws IOException {
        FtpReply rnfrReply = sendCommand(FtpCommand.RNFR, from);
        if (rnfrReply.code() != 350) {
            throw new IOException("RNFR failed: " + rnfrReply);
        }
        return sendCommand(FtpCommand.RNTO, to);
    }

    /**
     * Sets the transfer type (ASCII or BINARY).
     *
     * @param type the transfer type
     * @return the server reply
     * @throws IOException if the command fails
     */
    public FtpReply setTransferType(FtpTransferType type) throws IOException {
        FtpReply reply = sendCommand(FtpCommand.TYPE, type.typeCode());
        if (reply.isSuccess()) {
            this.transferType = type;
        }
        return reply;
    }

    /**
     * Sets whether to use passive mode for data connections.
     *
     * @param passive true for passive mode, false for active mode
     */
    public void setPassiveMode(boolean passive) {
        this.passiveMode = passive;
    }

    /**
     * Returns the size of a remote file.
     *
     * @param remotePath the remote file path
     * @return the file size in bytes
     * @throws IOException if the command fails
     */
    public long size(String remotePath) throws IOException {
        FtpReply reply = sendCommand(FtpCommand.SIZE, remotePath);
        if (reply.code() != 213) {
            throw new IOException("SIZE failed: " + reply);
        }
        return Long.parseLong(reply.text().trim());
    }

    /**
     * Returns the modification time of a remote file.
     *
     * @param remotePath the remote file path
     * @return the modification time
     * @throws IOException if the command fails
     */
    public LocalDateTime modificationTime(String remotePath) throws IOException {
        FtpReply reply = sendCommand(FtpCommand.MDTM, remotePath);
        if (reply.code() != 213) {
            throw new IOException("MDTM failed: " + reply);
        }
        return MlsdParser.parseTimestamp(reply.text().trim());
    }

    /**
     * Aborts the current data transfer.
     *
     * @return the server reply
     * @throws IOException if the command fails
     */
    public FtpReply abort() throws IOException {
        return sendCommand(FtpCommand.ABOR, null);
    }

    /**
     * Sends a SITE command.
     *
     * @param command the site-specific command
     * @return the server reply
     * @throws IOException if the command fails
     */
    public FtpReply site(String command) throws IOException {
        return sendCommand(FtpCommand.SITE, command);
    }

    /**
     * Queries the server system type.
     *
     * @return the SYST reply text
     * @throws IOException if the command fails
     */
    public String systemType() throws IOException {
        FtpReply reply = sendCommand(FtpCommand.SYST, null);
        return reply.text();
    }

    /**
     * Queries server features.
     *
     * @return the list of feature strings
     * @throws IOException if the command fails
     */
    public List<String> features() throws IOException {
        FtpReply reply = sendCommand(FtpCommand.FEAT, null);
        if (reply.code() != 211) {
            return List.of();
        }
        List<String> features = new ArrayList<>();
        for (String line : reply.lines()) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("Features") && !trimmed.equals("End")) {
                features.add(trimmed);
            }
        }
        return features;
    }

    /**
     * Sends a NOOP (keep-alive) command.
     *
     * @return the server reply
     * @throws IOException if the command fails
     */
    public FtpReply noop() throws IOException {
        return sendCommand(FtpCommand.NOOP, null);
    }

    /**
     * Upgrades the control connection to TLS (explicit FTPS).
     *
     * @param ftpsConfig the TLS configuration
     * @return the server reply to AUTH TLS
     * @throws Exception if the TLS upgrade fails
     */
    public FtpReply enableTls(FtpsConfig ftpsConfig) throws Exception {
        FtpReply reply = sendCommand(FtpCommand.AUTH, "TLS");
        if (reply.code() != 234) {
            throw new IOException("AUTH TLS failed: " + reply);
        }
        ftpsHandler = new FtpsHandler(ftpsConfig);
        var sslSocket = ftpsHandler.upgradeToTls(controlSocket, host, port);
        controlSocket = sslSocket;
        reader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream(), StandardCharsets.UTF_8));
        writer = sslSocket.getOutputStream();

        // Set PBSZ 0 and PROT P
        sendCommand(FtpCommand.PBSZ, "0");
        ftpsHandler.handlePbsz(0);
        sendCommand(FtpCommand.PROT, "P");
        ftpsHandler.handleProt("P");

        LOG.info("TLS enabled on control connection");
        return reply;
    }

    /**
     * Returns whether the client is connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected && controlSocket != null && !controlSocket.isClosed();
    }

    /**
     * Returns whether the client is logged in.
     *
     * @return true if logged in
     */
    public boolean isLoggedIn() {
        return loggedIn;
    }

    /**
     * Returns the current transfer type.
     *
     * @return the transfer type
     */
    public FtpTransferType getTransferType() {
        return transferType;
    }

    /**
     * Returns whether passive mode is enabled.
     *
     * @return true if passive mode
     */
    public boolean isPassiveMode() {
        return passiveMode;
    }

    /**
     * Sends a raw FTP command and reads the reply.
     *
     * @param command  the FTP command
     * @param argument the command argument (may be {@code null})
     * @return the server reply
     * @throws IOException if an I/O error occurs
     */
    public FtpReply sendCommand(FtpCommand command, String argument) throws IOException {
        ensureConnected();
        String encoded = FtpProtocolCodec.encodeCommand(command, argument);
        LOG.debug(">>> {}", encoded.trim());
        writer.write(encoded.getBytes(StandardCharsets.UTF_8));
        writer.flush();
        FtpReply reply = FtpProtocolCodec.readReply(reader);
        if (reply != null) {
            LOG.debug("<<< {}", reply);
        }
        return reply;
    }

    @Override
    public void close() throws IOException {
        disconnect();
    }

    // ---- Private helpers ----

    private long putFromStream(InputStream source, String remotePath) throws IOException {
        try (DataConnection dataConn = openDataConnection()) {
            FtpReply reply = sendCommand(FtpCommand.STOR, remotePath);
            if (reply.code() != 150 && reply.code() != 125) {
                throw new IOException("STOR failed: " + reply);
            }
            dataConn.open();
            DataTransfer transfer = new DataTransfer(transferType);
            long bytes = transfer.send(source, dataConn.getOutputStream());
            dataConn.close();
            FtpReply endReply = FtpProtocolCodec.readReply(reader);
            if (endReply != null && endReply.code() != 226 && endReply.code() != 250) {
                LOG.warn("Transfer end reply: {}", endReply);
            }
            return bytes;
        }
    }

    private long appendFromStream(InputStream source, String remotePath) throws IOException {
        try (DataConnection dataConn = openDataConnection()) {
            FtpReply reply = sendCommand(FtpCommand.APPE, remotePath);
            if (reply.code() != 150 && reply.code() != 125) {
                throw new IOException("APPE failed: " + reply);
            }
            dataConn.open();
            DataTransfer transfer = new DataTransfer(transferType);
            long bytes = transfer.send(source, dataConn.getOutputStream());
            dataConn.close();
            FtpReply endReply = FtpProtocolCodec.readReply(reader);
            if (endReply != null && endReply.code() != 226 && endReply.code() != 250) {
                LOG.warn("Transfer end reply: {}", endReply);
            }
            return bytes;
        }
    }

    private String transferData(FtpCommand command, String argument, boolean read) throws IOException {
        try (DataConnection dataConn = openDataConnection()) {
            FtpReply reply = sendCommand(command, argument);
            if (reply.code() != 150 && reply.code() != 125) {
                throw new IOException(command + " failed: " + reply);
            }
            dataConn.open();
            if (read) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[config.bufferSize()];
                int n;
                InputStream in = dataConn.getInputStream();
                while ((n = in.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                dataConn.close();
                FtpReply endReply = FtpProtocolCodec.readReply(reader);
                if (endReply != null && endReply.code() != 226 && endReply.code() != 250) {
                    LOG.warn("Transfer end reply: {}", endReply);
                }
                return baos.toString(StandardCharsets.UTF_8);
            }
            dataConn.close();
            return null;
        }
    }

    private DataConnection openDataConnection() throws IOException {
        if (passiveMode) {
            return openPassiveDataConnection();
        } else {
            return openActiveDataConnection();
        }
    }

    private PassiveDataConnection openPassiveDataConnection() throws IOException {
        FtpReply reply = sendCommand(FtpCommand.PASV, null);
        if (reply.code() != 227) {
            // Try EPSV
            reply = sendCommand(FtpCommand.EPSV, null);
            if (reply.code() != 229) {
                throw new IOException("Cannot enter passive mode: " + reply);
            }
            int dataPort = PassiveDataConnection.parseEpsvReply(reply.text());
            return DataConnectionFactory.createPassiveClient(
                    controlSocket.getInetAddress(), dataPort);
        }
        Object[] addr = PassiveDataConnection.parsePasvReply(reply.text());
        return DataConnectionFactory.createPassiveClient(
                (InetAddress) addr[0], (int) addr[1]);
    }

    private ActiveDataConnection openActiveDataConnection() throws IOException {
        InetAddress localAddr = controlSocket.getLocalAddress();
        ActiveDataConnection conn = DataConnectionFactory.createActiveClient(localAddr, 0);
        // Need to create the server socket to get the port
        // Then send PORT command
        // For simplicity, use ephemeral port
        var ss = new java.net.ServerSocket(0, 1, localAddr);
        int localPort = ss.getLocalPort();
        ss.close();

        String portArg = ActiveDataConnection.formatPortArgument(localAddr, localPort);
        FtpReply reply = sendCommand(FtpCommand.PORT, portArg);
        if (reply.code() != 200) {
            throw new IOException("PORT command failed: " + reply);
        }
        return new ActiveDataConnection(localAddr, localPort, false);
    }

    private void ensureConnected() throws IOException {
        if (!connected || controlSocket == null || controlSocket.isClosed()) {
            throw new IOException("Not connected to FTP server");
        }
    }
}
