package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.client.FtpFileEntry;
import ssg.legoflow.ftp.client.MlsdParser;
import ssg.legoflow.ftp.data.*;
import ssg.legoflow.ftp.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Processes FTP commands for a connected client session.
 *
 * <p>Handles all RFC 959 commands plus extensions (FEAT, SIZE, MDTM, MLSD, EPRT, EPSV, etc.).
 * Each command returns an {@link FtpReply} to be sent back to the client.
 *
 * @since 0.1.0
 */
public final class FtpCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(FtpCommandHandler.class);

    private final FtpSession session;
    private final FtpFileSystem fileSystem;
    private final FtpAuthenticator authenticator;
    private final FtpServerConfig config;
    private volatile PassiveDataConnection passiveDataConn;
    private final Map<String, Function<String, FtpReply>> optionHandlers = new ConcurrentHashMap<>();

    /**
     * Creates a command handler for a session.
     *
     * @param session       the client session
     * @param fileSystem    the virtual filesystem
     * @param authenticator the authenticator
     * @param config        the server configuration
     */
    public FtpCommandHandler(FtpSession session, FtpFileSystem fileSystem,
                             FtpAuthenticator authenticator, FtpServerConfig config) {
        this.session = Objects.requireNonNull(session);
        this.fileSystem = Objects.requireNonNull(fileSystem);
        this.authenticator = Objects.requireNonNull(authenticator);
        this.config = Objects.requireNonNull(config);

        // Register default OPTS handlers
        registerOption("UTF8", arg -> FtpReply.of(FtpReplyCode.COMMAND_OK, "UTF8 set to ON"));
        registerOption("MLST", this::handleOptsMlst);
    }

    /**
     * Registers a handler for an OPTS option name.
     *
     * <p>This allows extending OPTS support with new option names.
     * The handler receives the argument portion after the option name
     * (may be {@code null}) and returns an {@link FtpReply}.
     *
     * @param optionName the option name (case-insensitive, stored uppercase)
     * @param handler    the handler function
     */
    public void registerOption(String optionName, Function<String, FtpReply> handler) {
        optionHandlers.put(optionName.toUpperCase(), handler);
    }

    /**
     * Unregisters a handler for an OPTS option name.
     *
     * @param optionName the option name to remove
     */
    public void unregisterOption(String optionName) {
        optionHandlers.remove(optionName.toUpperCase());
    }

    /**
     * Returns an unmodifiable view of registered option names.
     *
     * @return the set of registered option names
     */
    public Set<String> registeredOptions() {
        return Collections.unmodifiableSet(optionHandlers.keySet());
    }

    /**
     * Handles an FTP command and returns the reply.
     *
     * @param command  the FTP command
     * @param argument the command argument (may be {@code null})
     * @param socket   the client control socket (for data connections)
     * @return the reply to send
     */
    public FtpReply handle(FtpCommand command, String argument, Socket socket) {
        LOG.debug("Handling {} {}", command, argument);

        // Commands allowed before authentication
        return switch (command) {
            case USER -> handleUser(argument);
            case PASS -> handlePass(argument);
            case QUIT -> handleQuit();
            case AUTH -> handleAuth(argument);
            case FEAT -> handleFeat();
            case SYST -> handleSyst();
            case HELP -> handleHelp();
            case NOOP -> FtpReply.of(FtpReplyCode.COMMAND_OK, "NOOP ok");
            default -> {
                if (!session.isAuthenticated()) {
                    yield FtpReply.of(FtpReplyCode.NOT_LOGGED_IN);
                }
                yield handleAuthenticated(command, argument, socket);
            }
        };
    }

    /**
     * Opens a data connection and performs a transfer.
     *
     * @param socket    the control socket
     * @param sendData  the data to send (for LIST, NLST, MLSD)
     * @param outWriter the control channel writer
     * @throws IOException if the transfer fails
     */
    public void performDataTransfer(Socket socket, String sendData, OutputStream outWriter) throws IOException {
        DataConnection dataConn = getDataConnection(socket);
        try {
            dataConn.open();
            OutputStream out = dataConn.getOutputStream();
            out.write(sendData.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } finally {
            dataConn.close();
            closePassiveConnection();
        }
    }

    /**
     * Opens a data connection for file retrieval.
     *
     * @param socket the control socket
     * @param path   the file path
     * @param outWriter control channel writer
     * @throws IOException if the transfer fails
     */
    public void performRetrieve(Socket socket, String path, OutputStream outWriter) throws IOException {
        String resolved = session.resolvePath(path);
        long offset = session.consumeRestartOffset();
        DataConnection dataConn = getDataConnection(socket);
        try {
            dataConn.open();
            DataTransfer transfer = new DataTransfer(session.transferType());
            try (InputStream in = fileSystem.readFile(resolved)) {
                if (offset > 0) {
                    long skipped = in.skip(offset);
                    LOG.debug("REST: skipped {} bytes (requested {})", skipped, offset);
                }
                transfer.send(in, dataConn.getOutputStream());
            }
        } finally {
            dataConn.close();
            closePassiveConnection();
        }
    }

    /**
     * Opens a data connection for file storage.
     *
     * @param socket the control socket
     * @param path   the file path
     * @param append whether to append
     * @param outWriter control channel writer
     * @throws IOException if the transfer fails
     */
    public void performStore(Socket socket, String path, boolean append, OutputStream outWriter) throws IOException {
        String resolved = session.resolvePath(path);
        long offset = session.consumeRestartOffset();
        DataConnection dataConn = getDataConnection(socket);
        try {
            dataConn.open();
            DataTransfer transfer = new DataTransfer(session.transferType());
            if (offset > 0 && !append) {
                // REST + STOR: write to file, seeking to the offset position
                // For in-memory fs, we append to the file and rely on the FS to handle offset
                try (OutputStream out = fileSystem.appendFile(resolved)) {
                    transfer.receive(dataConn.getInputStream(), out);
                }
            } else {
                try (OutputStream out = append ? fileSystem.appendFile(resolved) : fileSystem.writeFile(resolved)) {
                    transfer.receive(dataConn.getInputStream(), out);
                }
            }
        } finally {
            dataConn.close();
            closePassiveConnection();
        }
    }

    // ---- Command handlers ----

    private FtpReply handleUser(String username) {
        session.setUsername(username);
        session.setState(FtpSession.State.USER_PROVIDED);
        return FtpReply.of(FtpReplyCode.USER_OK_NEED_PASSWORD);
    }

    private FtpReply handlePass(String password) {
        if (session.state() != FtpSession.State.USER_PROVIDED) {
            return FtpReply.of(FtpReplyCode.BAD_COMMAND_SEQUENCE, "Send USER first");
        }
        if (authenticator.authenticate(session.username(), password)) {
            session.setState(FtpSession.State.AUTHENTICATED);
            return FtpReply.of(FtpReplyCode.USER_LOGGED_IN, "Welcome " + session.username());
        }
        session.setState(FtpSession.State.NOT_AUTHENTICATED);
        return FtpReply.of(FtpReplyCode.NOT_LOGGED_IN, "Authentication failed");
    }

    private FtpReply handleQuit() {
        return FtpReply.of(FtpReplyCode.SERVICE_CLOSING, "Goodbye");
    }

    private FtpReply handleAuth(String argument) {
        if (argument != null && (argument.equalsIgnoreCase("TLS") || argument.equalsIgnoreCase("SSL"))) {
            if (config.isFtpsEnabled()) {
                return FtpReply.of(FtpReplyCode.SECURITY_DATA_EXCHANGE_COMPLETE,
                        "AUTH " + argument.toUpperCase() + " successful");
            }
            return FtpReply.of(FtpReplyCode.SECURITY_MECHANISM_NOT_ACCEPTED, "TLS not available");
        }
        return FtpReply.of(FtpReplyCode.COMMAND_NOT_IMPLEMENTED_FOR_PARAMETER, "Unknown AUTH mechanism");
    }

    private FtpReply handleFeat() {
        List<String> features = List.of(
                "Features:",
                " SIZE",
                " MDTM",
                " MLST type*;size*;modify*;perm*;",
                " MLSD",
                " UTF8",
                " EPRT",
                " EPSV",
                " PASV",
                " REST STREAM",
                "End"
        );
        return new FtpReply(211, features);
    }

    private FtpReply handleSyst() {
        return FtpReply.of(FtpReplyCode.NAME_SYSTEM_TYPE, "UNIX Type: L8");
    }

    private FtpReply handleHelp() {
        return FtpReply.of(FtpReplyCode.HELP_MESSAGE,
                "The following commands are recognized: USER PASS ACCT QUIT CWD CDUP SMNT PWD MKD RMD " +
                        "DELE RNFR RNTO LIST NLST MLSD MLST RETR STOR APPE TYPE PASV EPSV PORT EPRT " +
                        "SIZE MDTM SYST FEAT OPTS NOOP STAT HELP SITE ABOR AUTH PBSZ PROT REIN");
    }

    private FtpReply handleAuthenticated(FtpCommand command, String argument, Socket socket) {
        try {
            return switch (command) {
                case CWD -> handleCwd(argument);
                case CDUP -> handleCdup();
                case PWD -> handlePwd();
                case MKD -> handleMkd(argument);
                case RMD -> handleRmd(argument);
                case DELE -> handleDele(argument);
                case RNFR -> handleRnfr(argument);
                case RNTO -> handleRnto(argument);
                case TYPE -> handleType(argument);
                case PASV -> handlePasv(socket);
                case EPSV -> handleEpsv(socket);
                case PORT -> handlePort(argument);
                case EPRT -> handleEprt(argument);
                case LIST -> handleList(argument);
                case NLST -> handleNlst(argument);
                case MLSD -> handleMlsd(argument);
                case RETR -> handleRetr(argument);
                case STOR -> handleStor(argument);
                case STOU -> handleStou();
                case APPE -> handleAppe(argument);
                case SIZE -> handleSize(argument);
                case MDTM -> handleMdtm(argument);
                case REST -> handleRest(argument);
                case STAT -> handleStat(argument);
                case SITE -> FtpReply.of(FtpReplyCode.COMMAND_OK, "SITE command ok");
                case OPTS -> handleOpts(argument);
                case PBSZ -> handlePbsz(argument);
                case PROT -> handleProt(argument);
                case STRU -> handleStru(argument);
                case MODE -> handleMode(argument);
                case ABOR -> FtpReply.of(FtpReplyCode.CLOSING_DATA_CONNECTION, "ABOR ok");
                case ALLO -> FtpReply.of(FtpReplyCode.COMMAND_OK, "ALLO command ok (no-op)");
                case ACCT -> handleAcct(argument);
                case SMNT -> handleSmnt(argument);
                case REIN -> handleRein();
                case MLST -> handleMlst(argument);
                case CCC -> handleCcc();
                case UTF8 -> FtpReply.of(FtpReplyCode.COMMAND_OK, "UTF8 mode enabled");
                default -> FtpReply.of(FtpReplyCode.COMMAND_NOT_IMPLEMENTED);
            };
        } catch (IOException e) {
            LOG.error("Error handling command {} {}: {}", command, argument, e.getMessage());
            return FtpReply.of(FtpReplyCode.FILE_ACTION_NOT_TAKEN, e.getMessage());
        }
    }

    private FtpReply handleCwd(String path) throws IOException {
        String resolved = session.resolvePath(path);
        if (!fileSystem.exists(resolved)) {
            return FtpReply.of(FtpReplyCode.FILE_UNAVAILABLE, "Directory not found: " + path);
        }
        session.setCurrentDirectory(resolved);
        return FtpReply.of(FtpReplyCode.FILE_ACTION_OK, "CWD successful. \"" + resolved + "\"");
    }

    private FtpReply handleCdup() {
        String parent = FtpSession.normalizePath(session.currentDirectory() + "/..");
        session.setCurrentDirectory(parent);
        return FtpReply.of(FtpReplyCode.FILE_ACTION_OK, "CDUP successful. \"" + parent + "\"");
    }

    private FtpReply handlePwd() {
        return FtpReply.of(FtpReplyCode.PATHNAME_CREATED,
                "\"" + session.currentDirectory() + "\" is current directory");
    }

    private FtpReply handleMkd(String path) throws IOException {
        String resolved = session.resolvePath(path);
        fileSystem.createDirectory(resolved);
        return FtpReply.of(FtpReplyCode.PATHNAME_CREATED, "\"" + resolved + "\" directory created");
    }

    private FtpReply handleRmd(String path) throws IOException {
        String resolved = session.resolvePath(path);
        fileSystem.deleteDirectory(resolved);
        return FtpReply.of(FtpReplyCode.FILE_ACTION_OK, "Directory removed");
    }

    private FtpReply handleDele(String path) throws IOException {
        String resolved = session.resolvePath(path);
        fileSystem.deleteFile(resolved);
        return FtpReply.of(FtpReplyCode.FILE_ACTION_OK, "File deleted");
    }

    private FtpReply handleRnfr(String path) throws IOException {
        String resolved = session.resolvePath(path);
        if (!fileSystem.exists(resolved)) {
            return FtpReply.of(FtpReplyCode.FILE_UNAVAILABLE, "File not found");
        }
        session.setRenameFrom(resolved);
        return FtpReply.of(FtpReplyCode.FILE_ACTION_PENDING, "Ready for RNTO");
    }

    private FtpReply handleRnto(String path) throws IOException {
        if (session.renameFrom() == null) {
            return FtpReply.of(FtpReplyCode.BAD_COMMAND_SEQUENCE, "RNFR required first");
        }
        String resolved = session.resolvePath(path);
        fileSystem.rename(session.renameFrom(), resolved);
        session.setRenameFrom(null);
        return FtpReply.of(FtpReplyCode.FILE_ACTION_OK, "Rename successful");
    }

    private FtpReply handleType(String argument) {
        if (argument == null) {
            return FtpReply.of(FtpReplyCode.SYNTAX_ERROR_PARAMETERS, "TYPE requires an argument");
        }
        try {
            FtpTransferType type = FtpTransferType.fromCode(argument.substring(0, 1));
            session.setTransferType(type);
            return FtpReply.of(FtpReplyCode.COMMAND_OK, "Type set to " + type.name());
        } catch (IllegalArgumentException e) {
            return FtpReply.of(FtpReplyCode.COMMAND_NOT_IMPLEMENTED_FOR_PARAMETER,
                    "Unknown type: " + argument);
        }
    }

    private FtpReply handlePasv(Socket socket) throws IOException {
        closePassiveConnection();
        InetAddress addr = socket.getLocalAddress();
        int portToUse = config.passivePortMin() > 0 ? config.passivePortMin() : 0;
        passiveDataConn = DataConnectionFactory.createPassiveServer(addr, portToUse);
        int actualPort = passiveDataConn.listen();
        session.setDataMode(FtpSession.DataMode.PASSIVE);
        String pasvReply = PassiveDataConnection.formatPasvReply(addr, actualPort);
        return FtpReply.of(FtpReplyCode.ENTERING_PASSIVE_MODE,
                "Entering Passive Mode " + pasvReply);
    }

    private FtpReply handleEpsv(Socket socket) throws IOException {
        closePassiveConnection();
        InetAddress addr = socket.getLocalAddress();
        int portToUse = config.passivePortMin() > 0 ? config.passivePortMin() : 0;
        passiveDataConn = DataConnectionFactory.createPassiveServer(addr, portToUse);
        int actualPort = passiveDataConn.listen();
        session.setDataMode(FtpSession.DataMode.PASSIVE);
        return FtpReply.of(FtpReplyCode.ENTERING_EXTENDED_PASSIVE_MODE,
                "Entering Extended Passive Mode (|||" + actualPort + "|)");
    }

    private FtpReply handlePort(String argument) throws IOException {
        Object[] parsed = ActiveDataConnection.parsePortArgument(argument);
        session.setDataAddress((InetAddress) parsed[0]);
        session.setDataPort((int) parsed[1]);
        session.setDataMode(FtpSession.DataMode.ACTIVE);
        return FtpReply.of(FtpReplyCode.COMMAND_OK, "PORT command successful");
    }

    private FtpReply handleEprt(String argument) throws IOException {
        Object[] parsed = ActiveDataConnection.parseEprtArgument(argument);
        session.setDataAddress((InetAddress) parsed[0]);
        session.setDataPort((int) parsed[1]);
        session.setDataMode(FtpSession.DataMode.ACTIVE);
        return FtpReply.of(FtpReplyCode.COMMAND_OK, "EPRT command successful");
    }

    private FtpReply handleList(String argument) throws IOException {
        String resolved = session.resolvePath(argument);
        return FtpReply.of(FtpReplyCode.FILE_STATUS_OK, "Opening data connection for LIST");
    }

    private FtpReply handleNlst(String argument) throws IOException {
        return FtpReply.of(FtpReplyCode.FILE_STATUS_OK, "Opening data connection for NLST");
    }

    private FtpReply handleMlsd(String argument) throws IOException {
        return FtpReply.of(FtpReplyCode.FILE_STATUS_OK, "Opening data connection for MLSD");
    }

    private FtpReply handleMlst(String argument) throws IOException {
        String resolved = session.resolvePath(argument);
        FtpFileEntry entry = fileSystem.getFile(resolved);
        if (entry == null) {
            return FtpReply.of(FtpReplyCode.FILE_UNAVAILABLE, "File not found");
        }
        String typeStr = entry.isDirectory() ? "dir" : "file";
        String modifyStr = entry.modified() != null ? MlsdParser.formatTimestamp(entry.modified()) : "";
        String line = String.format(" type=%s;size=%d;modify=%s; %s",
                typeStr, entry.size(), modifyStr, entry.name());
        return new FtpReply(250, List.of("Start", line, "End"));
    }

    private FtpReply handleRetr(String argument) {
        return FtpReply.of(FtpReplyCode.FILE_STATUS_OK, "Opening data connection for RETR");
    }

    private FtpReply handleStor(String argument) {
        return FtpReply.of(FtpReplyCode.FILE_STATUS_OK, "Opening data connection for STOR");
    }

    private FtpReply handleStou() {
        return FtpReply.of(FtpReplyCode.FILE_STATUS_OK,
                "Opening data connection for STOU (" + UUID.randomUUID() + ")");
    }

    private FtpReply handleAppe(String argument) {
        return FtpReply.of(FtpReplyCode.FILE_STATUS_OK, "Opening data connection for APPE");
    }

    private FtpReply handleSize(String argument) throws IOException {
        String resolved = session.resolvePath(argument);
        long size = fileSystem.getSize(resolved);
        return FtpReply.of(FtpReplyCode.FILE_STATUS, String.valueOf(size));
    }

    private FtpReply handleMdtm(String argument) throws IOException {
        String resolved = session.resolvePath(argument);
        var modified = fileSystem.getModificationTime(resolved);
        return FtpReply.of(FtpReplyCode.FILE_STATUS, MlsdParser.formatTimestamp(modified));
    }

    private FtpReply handleRest(String argument) {
        if (argument == null || argument.isEmpty()) {
            return FtpReply.of(FtpReplyCode.SYNTAX_ERROR_PARAMETERS, "REST requires an offset");
        }
        try {
            long offset = Long.parseLong(argument.trim());
            if (offset < 0) {
                return FtpReply.of(FtpReplyCode.SYNTAX_ERROR_PARAMETERS, "Invalid restart offset");
            }
            session.setRestartOffset(offset);
            return FtpReply.of(FtpReplyCode.FILE_ACTION_PENDING,
                    "Restart position accepted (" + offset + ")");
        } catch (NumberFormatException e) {
            return FtpReply.of(FtpReplyCode.SYNTAX_ERROR_PARAMETERS, "Invalid restart offset: " + argument);
        }
    }

    private FtpReply handleStat(String argument) {
        return FtpReply.of(FtpReplyCode.SYSTEM_STATUS,
                config.serverName() + " — session: " + session);
    }

    private FtpReply handleAcct(String argument) {
        // RFC 959 §4.1.1: ACCT stores account information on the session.
        // Most FTP servers accept and ignore ACCT; we store it for completeness.
        session.setAccount(argument);
        return FtpReply.of(FtpReplyCode.USER_LOGGED_IN, "ACCT accepted");
    }

    private FtpReply handleSmnt(String argument) {
        // RFC 959 §4.1.1: SMNT allows mounting a different filesystem.
        // We accept the root "/" mount point (or null/empty argument) and reject others.
        if (argument == null || argument.isEmpty() || "/".equals(argument.trim())) {
            return FtpReply.of(FtpReplyCode.FILE_ACTION_OK, "SMNT accepted");
        }
        return FtpReply.of(FtpReplyCode.FILE_UNAVAILABLE,
                "Mount point not available: " + argument);
    }

    private FtpReply handleOpts(String argument) {
        if (argument == null || argument.isBlank()) {
            return FtpReply.of(FtpReplyCode.SYNTAX_ERROR_PARAMETERS, "OPTS requires an option name");
        }
        // Parse option name and optional argument: "OPTS <name> [<arg>]"
        String upper = argument.trim();
        int spaceIdx = upper.indexOf(' ');
        String optionName;
        String optionArg;
        if (spaceIdx >= 0) {
            optionName = upper.substring(0, spaceIdx).toUpperCase();
            optionArg = upper.substring(spaceIdx + 1).trim();
        } else {
            optionName = upper.toUpperCase();
            optionArg = null;
        }
        Function<String, FtpReply> handler = optionHandlers.get(optionName);
        if (handler != null) {
            return handler.apply(optionArg);
        }
        return FtpReply.of(FtpReplyCode.COMMAND_NOT_IMPLEMENTED_FOR_PARAMETER,
                "Unknown option: " + argument);
    }

    private FtpReply handleOptsMlst(String argument) {
        // RFC 3659 §7.1: OPTS MLST allows client to select which facts appear in MLST/MLSD output.
        // Supported facts: type, size, modify, perm
        Set<String> supportedFacts = Set.of("type", "size", "modify", "perm");
        if (argument == null || argument.isBlank()) {
            // No argument — return all supported facts
            return FtpReply.of(FtpReplyCode.COMMAND_OK, "MLST OPTS type;size;modify;perm;");
        }
        // Parse requested facts (semicolon-separated)
        String[] requested = argument.toLowerCase().replace(";", ";").split(";");
        var accepted = new StringBuilder("MLST OPTS ");
        for (String fact : requested) {
            String trimmed = fact.trim();
            if (!trimmed.isEmpty() && supportedFacts.contains(trimmed)) {
                accepted.append(trimmed).append(';');
            }
        }
        return FtpReply.of(FtpReplyCode.COMMAND_OK, accepted.toString());
    }

    private FtpReply handlePbsz(String argument) {
        return FtpReply.of(FtpReplyCode.COMMAND_OK, "PBSZ set to 0");
    }

    private FtpReply handleProt(String argument) {
        if ("P".equalsIgnoreCase(argument)) {
            session.setDataProtected(true);
            return FtpReply.of(FtpReplyCode.COMMAND_OK, "PROT set to Private");
        }
        if ("C".equalsIgnoreCase(argument)) {
            session.setDataProtected(false);
            return FtpReply.of(FtpReplyCode.COMMAND_OK, "PROT set to Clear");
        }
        return FtpReply.of(FtpReplyCode.COMMAND_NOT_IMPLEMENTED_FOR_PARAMETER,
                "Unknown PROT level: " + argument);
    }

    private FtpReply handleStru(String argument) {
        if ("F".equalsIgnoreCase(argument)) {
            return FtpReply.of(FtpReplyCode.COMMAND_OK, "Structure set to File");
        }
        return FtpReply.of(FtpReplyCode.COMMAND_NOT_IMPLEMENTED_FOR_PARAMETER,
                "Only File structure supported");
    }

    private FtpReply handleMode(String argument) {
        if ("S".equalsIgnoreCase(argument)) {
            return FtpReply.of(FtpReplyCode.COMMAND_OK, "Mode set to Stream");
        }
        return FtpReply.of(FtpReplyCode.COMMAND_NOT_IMPLEMENTED_FOR_PARAMETER,
                "Only Stream mode supported");
    }

    private FtpReply handleCcc() {
        if (!session.isTlsEnabled()) {
            return FtpReply.of(FtpReplyCode.COMMAND_NOT_IMPLEMENTED,
                    "CCC not applicable — TLS not active");
        }
        // Per RFC 4217: CCC clears the control channel encryption
        // The data channel may still be protected via PROT P
        session.setTlsEnabled(false);
        session.setCccIssued(true);
        return FtpReply.of(FtpReplyCode.COMMAND_OK,
                "CCC ok — control channel will revert to plaintext");
    }

    private FtpReply handleRein() {
        session.setState(FtpSession.State.NOT_AUTHENTICATED);
        session.setUsername(null);
        session.setAccount(null);
        session.setCurrentDirectory("/");
        session.setTransferType(FtpTransferType.BINARY);
        return FtpReply.of(FtpReplyCode.SERVICE_READY, "Session reinitialized");
    }

    /**
     * Gets the current data connection based on session mode.
     *
     * @param controlSocket the control socket
     * @return the data connection
     * @throws IOException if the connection cannot be opened
     */
    public DataConnection getDataConnection(Socket controlSocket) throws IOException {
        if (session.dataMode() == FtpSession.DataMode.PASSIVE) {
            if (passiveDataConn == null) {
                throw new IOException("No passive data connection prepared");
            }
            return passiveDataConn;
        } else {
            return DataConnectionFactory.createActiveServer(
                    session.dataAddress(), session.dataPort());
        }
    }

    /**
     * Generates MLSD output for the given directory.
     *
     * @param path the directory path
     * @return the MLSD formatted listing
     * @throws IOException if the listing fails
     */
    public String generateMlsdOutput(String path) throws IOException {
        String resolved = session.resolvePath(path);
        List<FtpFileEntry> entries = fileSystem.listFiles(resolved);
        var sb = new StringBuilder();
        for (FtpFileEntry entry : entries) {
            String typeStr = entry.isDirectory() ? "dir" : "file";
            String modifyStr = entry.modified() != null ? MlsdParser.formatTimestamp(entry.modified()) : "";
            sb.append(String.format("type=%s;size=%d;modify=%s; %s\r\n",
                    typeStr, entry.size(), modifyStr, entry.name()));
        }
        return sb.toString();
    }

    /**
     * Generates LIST output for the given directory.
     *
     * @param path the directory path
     * @return the formatted listing
     * @throws IOException if the listing fails
     */
    public String generateListOutput(String path) throws IOException {
        String resolved = session.resolvePath(path);
        List<FtpFileEntry> entries = fileSystem.listFiles(resolved);
        var sb = new StringBuilder();
        for (FtpFileEntry entry : entries) {
            char typeChar = entry.isDirectory() ? 'd' : '-';
            String perms = entry.permissions() != null ? entry.permissions() : "rw-r--r--";
            String dateStr = entry.modified() != null ?
                    entry.modified().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd HH:mm")) :
                    "Jan 01 00:00";
            sb.append(String.format("%c%s %3d %-8s %-8s %10d %s %s\r\n",
                    typeChar, perms, 1, "owner", "group",
                    entry.size(), dateStr, entry.name()));
        }
        return sb.toString();
    }

    /**
     * Generates NLST output for the given directory.
     *
     * @param path the directory path
     * @return the name list
     * @throws IOException if the listing fails
     */
    public String generateNlstOutput(String path) throws IOException {
        String resolved = session.resolvePath(path);
        List<FtpFileEntry> entries = fileSystem.listFiles(resolved);
        var sb = new StringBuilder();
        for (FtpFileEntry entry : entries) {
            sb.append(entry.name()).append("\r\n");
        }
        return sb.toString();
    }

    /**
     * Returns the passive data connection, or {@code null}.
     *
     * @return the passive data connection
     */
    public PassiveDataConnection getPassiveDataConnection() {
        return passiveDataConn;
    }

    private void closePassiveConnection() {
        if (passiveDataConn != null) {
            try {
                passiveDataConn.close();
            } catch (IOException ignored) {}
            passiveDataConn = null;
        }
    }
}
