package ssg.legoflow.database.postgresql.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.database.postgresql.auth.*;
import ssg.legoflow.database.postgresql.common.PgSeverity;
import ssg.legoflow.database.postgresql.common.SqlState;
import ssg.legoflow.database.postgresql.protocol.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages a single client connection on the server side.
 *
 * <p>Handles the full lifecycle: startup, authentication, query processing,
 * and termination.
 *
 * @since 1.0.0
 */
public final class ClientSession implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ClientSession.class);

    private final Socket socket;
    private final QueryExecutor executor;
    private final NotificationManager notifications;
    private final CopyHandler copyHandler;
    private final PgAuthenticator authenticator;
    private final int processId;
    private final int secretKey;
    private final Map<String, PreparedStatement> statements = new ConcurrentHashMap<>();
    private final Map<String, Portal> portals = new ConcurrentHashMap<>();

    private TransactionStatus transactionStatus = TransactionStatus.IDLE;
    private String username;
    private String database;
    private volatile boolean running = true;

    // SCRAM state
    private ScramSha256.ServerSession scramSession;

    /**
     * Creates a new client session.
     *
     * @param socket        the client socket
     * @param executor      the query executor
     * @param notifications the notification manager
     * @param authenticator the authenticator (or null for trust auth)
     * @param processId     the backend process ID
     * @param secretKey     the secret key for cancel requests
     */
    public ClientSession(Socket socket, QueryExecutor executor, NotificationManager notifications,
                         PgAuthenticator authenticator, int processId, int secretKey) {
        this.socket = socket;
        this.executor = executor;
        this.notifications = notifications;
        this.copyHandler = new CopyHandler(executor.database());
        this.authenticator = authenticator;
        this.processId = processId;
        this.secretKey = secretKey;
    }

    @Override
    public void run() {
        try (socket) {
            var in = new BufferedInputStream(socket.getInputStream());
            var out = new BufferedOutputStream(socket.getOutputStream());

            // Startup phase
            if (!handleStartup(in, out)) return;

            // Main loop
            while (running) {
                FrontendMessage msg;
                try {
                    msg = PgCodec.decodeFrontend(in, false);
                } catch (IOException e) {
                    break;
                }

                handleMessage(msg, in, out);
            }
        } catch (IOException e) {
            LOG.debug("Client session error: {}", e.getMessage());
        } finally {
            notifications.unlistenAll(notificationListener);
        }
    }

    /**
     * Stops this session.
     */
    public void stop() {
        running = false;
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
    }

    // ======== Startup ========

    private boolean handleStartup(InputStream in, OutputStream out) throws IOException {
        FrontendMessage msg = PgCodec.decodeFrontend(in, true);

        if (msg instanceof FrontendMessage.SSLRequest) {
            // Reject SSL
            out.write('N');
            out.flush();
            // Re-read startup
            msg = PgCodec.decodeFrontend(in, true);
        }

        if (msg instanceof FrontendMessage.StartupMessage startup) {
            username = startup.parameters().getOrDefault("user", "");
            database = startup.parameters().getOrDefault("database", username);

            if (!handleAuthentication(in, out)) return false;

            // Send parameter status
            sendParameterStatuses(out);

            // Send BackendKeyData
            send(out, PgCodec.encodeBackend(
                    new BackendMessage.BackendKeyData(processId, secretKey)));

            // Send ReadyForQuery
            send(out, PgCodec.encodeBackend(
                    new BackendMessage.ReadyForQuery(transactionStatus)));

            return true;
        }

        return false;
    }

    private boolean handleAuthentication(InputStream in, OutputStream out) throws IOException {
        if (authenticator == null) {
            send(out, PgCodec.encodeBackend(new BackendMessage.AuthenticationOk()));
            return true;
        }

        return switch (authenticator) {
            case CleartextAuth auth -> handleCleartextAuth(auth, in, out);
            case Md5Auth auth -> handleMd5Auth(auth, in, out);
            case ScramSha256 auth -> handleScramAuth(auth, in, out);
            default -> {
                send(out, PgCodec.encodeBackend(new BackendMessage.AuthenticationOk()));
                yield true;
            }
        };
    }

    private boolean handleCleartextAuth(CleartextAuth auth, InputStream in, OutputStream out) throws IOException {
        send(out, PgCodec.encodeBackend(new BackendMessage.AuthenticationCleartextPassword()));

        FrontendMessage response = PgCodec.decodeFrontend(in, false);
        if (response instanceof FrontendMessage.PasswordMessage pw) {
            if (auth.authenticate(username, pw.password())) {
                send(out, PgCodec.encodeBackend(new BackendMessage.AuthenticationOk()));
                return true;
            }
        }
        sendAuthError(out);
        return false;
    }

    private boolean handleMd5Auth(Md5Auth auth, InputStream in, OutputStream out) throws IOException {
        byte[] salt = auth.generateSalt();
        send(out, PgCodec.encodeBackend(new BackendMessage.AuthenticationMD5Password(salt)));

        FrontendMessage response = PgCodec.decodeFrontend(in, false);
        if (response instanceof FrontendMessage.PasswordMessage pw) {
            if (auth.validateMd5(username, pw.password(), salt)) {
                send(out, PgCodec.encodeBackend(new BackendMessage.AuthenticationOk()));
                return true;
            }
        }
        sendAuthError(out);
        return false;
    }

    private boolean handleScramAuth(ScramSha256 auth, InputStream in, OutputStream out) throws IOException {
        // Send SASL mechanisms
        send(out, PgCodec.encodeBackend(
                new BackendMessage.AuthenticationSASL(List.of(ScramSha256.MECHANISM))));

        // Read SASLInitialResponse
        int typeByte = in.read();
        if (typeByte != 'p') { sendAuthError(out); return false; }
        byte[] rawPayload = PgCodec.readRawPayload(in);
        FrontendMessage.SASLInitialResponse saslInitial = PgCodec.decodeSASLInitialResponse(rawPayload);

        ScramSha256.StoredCredentials cred = auth.getCredentials(username);
        if (cred == null) { sendAuthError(out); return false; }

        scramSession = new ScramSha256.ServerSession(cred);
        String clientFirstMessage = new String(saslInitial.initialResponse(), StandardCharsets.UTF_8);
        String serverFirstMessage = scramSession.processClientFirst(clientFirstMessage);

        send(out, PgCodec.encodeBackend(
                new BackendMessage.AuthenticationSASLContinue(
                        serverFirstMessage.getBytes(StandardCharsets.UTF_8))));

        // Read SASLResponse
        typeByte = in.read();
        if (typeByte != 'p') { sendAuthError(out); return false; }
        rawPayload = PgCodec.readRawPayload(in);
        String clientFinalMessage = new String(rawPayload, StandardCharsets.UTF_8);

        String serverFinalMessage = scramSession.processClientFinal(clientFinalMessage);
        if (serverFinalMessage == null) {
            sendAuthError(out);
            return false;
        }

        send(out, PgCodec.encodeBackend(
                new BackendMessage.AuthenticationSASLFinal(
                        serverFinalMessage.getBytes(StandardCharsets.UTF_8))));

        send(out, PgCodec.encodeBackend(new BackendMessage.AuthenticationOk()));
        return true;
    }

    private void sendAuthError(OutputStream out) throws IOException {
        send(out, PgCodec.encodeBackend(
                QueryExecutor.makeError(PgSeverity.FATAL, SqlState.INVALID_PASSWORD,
                        "password authentication failed for user \"" + username + "\"")));
    }

    private void sendParameterStatuses(OutputStream out) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("server_version", "16.0");
        params.put("server_encoding", "UTF8");
        params.put("client_encoding", "UTF8");
        params.put("DateStyle", "ISO, MDY");
        params.put("TimeZone", "UTC");
        params.put("integer_datetimes", "on");
        params.put("standard_conforming_strings", "on");

        for (var entry : params.entrySet()) {
            send(out, PgCodec.encodeBackend(
                    new BackendMessage.ParameterStatus(entry.getKey(), entry.getValue())));
        }
    }

    // ======== Message handling ========

    private void handleMessage(FrontendMessage msg, InputStream in, OutputStream out) throws IOException {
        switch (msg) {
            case FrontendMessage.Query q -> handleQuery(q, out);
            case FrontendMessage.Parse p -> handleParse(p, out);
            case FrontendMessage.Bind b -> handleBind(b, out);
            case FrontendMessage.Describe d -> handleDescribe(d, out);
            case FrontendMessage.Execute e -> handleExecute(e, out);
            case FrontendMessage.Sync s -> handleSync(out);
            case FrontendMessage.Flush f -> out.flush();
            case FrontendMessage.Close c -> handleClose(c, out);
            case FrontendMessage.CopyData cd -> {} // handled in COPY loop
            case FrontendMessage.CopyDone cd -> {}
            case FrontendMessage.CopyFail cf -> {}
            case FrontendMessage.Terminate t -> { running = false; }
            default -> {}
        }
    }

    private void handleQuery(FrontendMessage.Query msg, OutputStream out) throws IOException {
        String sql = msg.sql().trim();

        // Handle LISTEN/NOTIFY/UNLISTEN
        if (sql.toUpperCase().startsWith("LISTEN ")) {
            String channel = sql.substring(7).trim();
            notifications.listen(channel, notificationListener);
            send(out, PgCodec.encodeBackend(new BackendMessage.CommandComplete("LISTEN")));
            send(out, PgCodec.encodeBackend(new BackendMessage.ReadyForQuery(transactionStatus)));
            return;
        }
        if (sql.toUpperCase().startsWith("UNLISTEN ")) {
            String channel = sql.substring(9).trim();
            if ("*".equals(channel)) {
                notifications.unlistenAll(notificationListener);
            } else {
                notifications.unlisten(channel, notificationListener);
            }
            send(out, PgCodec.encodeBackend(new BackendMessage.CommandComplete("UNLISTEN")));
            send(out, PgCodec.encodeBackend(new BackendMessage.ReadyForQuery(transactionStatus)));
            return;
        }
        if (sql.toUpperCase().startsWith("NOTIFY ")) {
            handleNotify(sql, out);
            return;
        }

        // Handle COPY
        if (sql.toUpperCase().startsWith("COPY ") && sql.toUpperCase().contains("FROM STDIN")) {
            handleCopyIn(sql, out);
            return;
        }
        if (sql.toUpperCase().startsWith("COPY ") && sql.toUpperCase().contains("TO STDOUT")) {
            handleCopyOut(sql, out);
            return;
        }

        // Handle transaction state changes
        String upper = sql.toUpperCase();
        if (upper.startsWith("BEGIN") || upper.startsWith("START TRANSACTION")) {
            transactionStatus = TransactionStatus.IN_TRANSACTION;
        } else if (upper.startsWith("COMMIT") || upper.startsWith("ROLLBACK")) {
            transactionStatus = TransactionStatus.IDLE;
        }

        // Execute query
        if (sql.isEmpty()) {
            send(out, PgCodec.encodeBackend(new BackendMessage.EmptyQueryResponse()));
        } else {
            try {
                var messages = executor.executeSimple(sql);
                for (BackendMessage backMsg : messages) {
                    send(out, PgCodec.encodeBackend(backMsg));
                    if (backMsg instanceof BackendMessage.ErrorResponse) {
                        if (transactionStatus == TransactionStatus.IN_TRANSACTION) {
                            transactionStatus = TransactionStatus.FAILED;
                        }
                    }
                }
            } catch (Exception e) {
                send(out, PgCodec.encodeBackend(
                        QueryExecutor.makeError(SqlState.INTERNAL_ERROR, e.getMessage())));
                if (transactionStatus == TransactionStatus.IN_TRANSACTION) {
                    transactionStatus = TransactionStatus.FAILED;
                }
            }
        }

        // Send any pending notifications
        flushNotifications(out);

        send(out, PgCodec.encodeBackend(new BackendMessage.ReadyForQuery(transactionStatus)));
    }

    private void handleParse(FrontendMessage.Parse msg, OutputStream out) throws IOException {
        try {
            statements.put(msg.statementName(),
                    new PreparedStatement(msg.statementName(), msg.sql(), msg.parameterTypes()));
            send(out, PgCodec.encodeBackend(new BackendMessage.ParseComplete()));
        } catch (Exception e) {
            send(out, PgCodec.encodeBackend(
                    QueryExecutor.makeError(SqlState.SYNTAX_ERROR, e.getMessage())));
        }
    }

    private void handleBind(FrontendMessage.Bind msg, OutputStream out) throws IOException {
        PreparedStatement stmt = statements.get(msg.statementName());
        if (stmt == null) {
            send(out, PgCodec.encodeBackend(
                    QueryExecutor.makeError(SqlState.INVALID_SQL_STATEMENT_NAME,
                            "Prepared statement not found: " + msg.statementName())));
            return;
        }
        portals.put(msg.portalName(), new Portal(msg.portalName(), stmt, msg.parameterValues()));
        send(out, PgCodec.encodeBackend(new BackendMessage.BindComplete()));
    }

    private void handleDescribe(FrontendMessage.Describe msg, OutputStream out) throws IOException {
        if (msg.target() == (byte) 'S') {
            PreparedStatement stmt = statements.get(msg.name());
            if (stmt == null) {
                send(out, PgCodec.encodeBackend(
                        QueryExecutor.makeError(SqlState.INVALID_SQL_STATEMENT_NAME,
                                "Prepared statement not found: " + msg.name())));
                return;
            }
            // Send ParameterDescription
            send(out, PgCodec.encodeBackend(
                    new BackendMessage.ParameterDescription(stmt.parameterTypes())));
            // Try to describe the result columns
            try {
                String sql = stmt.sql();
                if (sql.toUpperCase().trim().startsWith("SELECT")) {
                    String[] dummyParams = new String[stmt.parameterTypes().length];
                    Arrays.fill(dummyParams, "NULL");
                    // Use a dummy execution to get column info
                    ResultSet rs = executor.database().execute(sql, dummyParams);
                    send(out, PgCodec.encodeBackend(new BackendMessage.RowDescription(rs.columns())));
                } else {
                    send(out, PgCodec.encodeBackend(new BackendMessage.NoData()));
                }
            } catch (Exception e) {
                send(out, PgCodec.encodeBackend(new BackendMessage.NoData()));
            }
        } else {
            Portal portal = portals.get(msg.name());
            if (portal == null) {
                send(out, PgCodec.encodeBackend(
                        QueryExecutor.makeError(SqlState.INVALID_CURSOR_STATE,
                                "Portal not found: " + msg.name())));
                return;
            }
            try {
                ResultSet rs = executor.database().execute(portal.statement().sql(), portal.parameterStrings());
                if (rs.hasData()) {
                    send(out, PgCodec.encodeBackend(new BackendMessage.RowDescription(rs.columns())));
                } else {
                    send(out, PgCodec.encodeBackend(new BackendMessage.NoData()));
                }
            } catch (Exception e) {
                send(out, PgCodec.encodeBackend(new BackendMessage.NoData()));
            }
        }
    }

    private void handleExecute(FrontendMessage.Execute msg, OutputStream out) throws IOException {
        Portal portal = portals.get(msg.portalName());
        if (portal == null) {
            send(out, PgCodec.encodeBackend(
                    QueryExecutor.makeError(SqlState.INVALID_CURSOR_STATE,
                            "Portal not found: " + msg.portalName())));
            return;
        }
        try {
            var messages = executor.executeExtended(portal, msg.maxRows());
            for (BackendMessage backMsg : messages) {
                send(out, PgCodec.encodeBackend(backMsg));
                if (backMsg instanceof BackendMessage.ErrorResponse) {
                    if (transactionStatus == TransactionStatus.IN_TRANSACTION) {
                        transactionStatus = TransactionStatus.FAILED;
                    }
                }
            }
        } catch (Exception e) {
            send(out, PgCodec.encodeBackend(
                    QueryExecutor.makeError(SqlState.INTERNAL_ERROR, e.getMessage())));
        }
    }

    private void handleSync(OutputStream out) throws IOException {
        send(out, PgCodec.encodeBackend(new BackendMessage.ReadyForQuery(transactionStatus)));
    }

    private void handleClose(FrontendMessage.Close msg, OutputStream out) throws IOException {
        if (msg.target() == (byte) 'S') {
            statements.remove(msg.name());
        } else {
            portals.remove(msg.name());
        }
        send(out, PgCodec.encodeBackend(new BackendMessage.CloseComplete()));
    }

    // ======== COPY ========

    private static final Pattern COPY_TABLE_PATTERN = Pattern.compile(
            "(?i)COPY\\s+(\\w+)");

    private void handleCopyIn(String sql, OutputStream out) throws IOException {
        Matcher m = COPY_TABLE_PATTERN.matcher(sql);
        if (!m.find()) return;
        String tableName = m.group(1);

        // Send CopyInResponse
        send(out, PgCodec.encodeBackend(
                new BackendMessage.CopyInResponse((byte) 0, new short[0])));

        // Read CopyData until CopyDone or CopyFail
        var in = new BufferedInputStream(socket.getInputStream());
        List<byte[]> dataChunks = new ArrayList<>();
        boolean success = false;

        while (true) {
            FrontendMessage msg = PgCodec.decodeFrontend(in, false);
            if (msg instanceof FrontendMessage.CopyData cd) {
                dataChunks.add(cd.data());
            } else if (msg instanceof FrontendMessage.CopyDone) {
                success = true;
                break;
            } else if (msg instanceof FrontendMessage.CopyFail cf) {
                send(out, PgCodec.encodeBackend(
                        QueryExecutor.makeError(SqlState.DATA_EXCEPTION, cf.errorMessage())));
                break;
            }
        }

        if (success) {
            try {
                int count = copyHandler.processCopyIn(tableName, dataChunks);
                send(out, PgCodec.encodeBackend(
                        new BackendMessage.CommandComplete("COPY " + count)));
            } catch (Exception e) {
                send(out, PgCodec.encodeBackend(
                        QueryExecutor.makeError(SqlState.DATA_EXCEPTION, e.getMessage())));
            }
        }

        send(out, PgCodec.encodeBackend(new BackendMessage.ReadyForQuery(transactionStatus)));
    }

    private void handleCopyOut(String sql, OutputStream out) throws IOException {
        Matcher m = COPY_TABLE_PATTERN.matcher(sql);
        if (!m.find()) return;
        String tableName = m.group(1);

        try {
            // Send CopyOutResponse
            send(out, PgCodec.encodeBackend(
                    new BackendMessage.CopyOutResponse((byte) 0, new short[0])));

            // Send data
            List<byte[]> chunks = copyHandler.generateCopyOut(tableName);
            for (byte[] chunk : chunks) {
                send(out, PgCodec.encodeBackend(new BackendMessage.CopyData(chunk)));
            }

            // Send CopyDone
            send(out, PgCodec.encodeBackend(new BackendMessage.CopyDone()));
            send(out, PgCodec.encodeBackend(
                    new BackendMessage.CommandComplete("COPY " + chunks.size())));
        } catch (Exception e) {
            send(out, PgCodec.encodeBackend(
                    QueryExecutor.makeError(SqlState.DATA_EXCEPTION, e.getMessage())));
        }

        send(out, PgCodec.encodeBackend(new BackendMessage.ReadyForQuery(transactionStatus)));
    }

    // ======== NOTIFY ========

    private static final Pattern NOTIFY_PATTERN = Pattern.compile(
            "(?i)NOTIFY\\s+(\\w+)(?:\\s*,\\s*'([^']*)')?");

    private void handleNotify(String sql, OutputStream out) throws IOException {
        Matcher m = NOTIFY_PATTERN.matcher(sql);
        if (m.find()) {
            String channel = m.group(1);
            String payload = m.group(2) != null ? m.group(2) : "";
            notifications.notify(processId, channel, payload);
            send(out, PgCodec.encodeBackend(new BackendMessage.CommandComplete("NOTIFY")));
        } else {
            send(out, PgCodec.encodeBackend(
                    QueryExecutor.makeError(SqlState.SYNTAX_ERROR, "Invalid NOTIFY syntax")));
        }
        send(out, PgCodec.encodeBackend(new BackendMessage.ReadyForQuery(transactionStatus)));
    }

    // ======== Notifications ========

    private final List<BackendMessage.NotificationResponse> pendingNotifications =
            Collections.synchronizedList(new ArrayList<>());

    private final Consumer<BackendMessage.NotificationResponse> notificationListener =
            pendingNotifications::add;

    private void flushNotifications(OutputStream out) throws IOException {
        List<BackendMessage.NotificationResponse> snapshot;
        synchronized (pendingNotifications) {
            snapshot = new ArrayList<>(pendingNotifications);
            pendingNotifications.clear();
        }
        for (var notif : snapshot) {
            send(out, PgCodec.encodeBackend(notif));
        }
    }

    // ======== I/O ========

    private void send(OutputStream out, byte[] data) throws IOException {
        out.write(data);
        out.flush();
    }
}
