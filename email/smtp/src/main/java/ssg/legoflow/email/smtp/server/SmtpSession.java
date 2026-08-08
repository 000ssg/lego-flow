package ssg.legoflow.email.smtp.server;

import ssg.legoflow.email.smtp.auth.CramMd5Auth;
import ssg.legoflow.email.smtp.auth.LoginAuth;
import ssg.legoflow.email.smtp.auth.PlainAuth;
import ssg.legoflow.email.smtp.auth.SmtpAuthException;
import ssg.legoflow.email.smtp.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-client SMTP session state machine.
 *
 * <p>Manages the lifecycle of a single SMTP connection: greeting, EHLO/HELO
 * negotiation, optional STARTTLS and AUTH, mail transactions (MAIL FROM,
 * RCPT TO, DATA/BDAT), and session cleanup.
 *
 * <p>Session states:
 * <ul>
 *   <li>GREETING -- initial state, awaiting client EHLO/HELO</li>
 *   <li>READY -- after EHLO, ready for MAIL FROM or AUTH</li>
 *   <li>MAIL -- after MAIL FROM, expecting RCPT TO</li>
 *   <li>RCPT -- after at least one RCPT TO, expecting DATA/BDAT or more RCPT TO</li>
 *   <li>DATA -- receiving message data</li>
 *   <li>AUTH -- in authentication exchange</li>
 *   <li>QUIT -- session ending</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class SmtpSession implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpSession.class);
    private static final AtomicLong ID_COUNTER = new AtomicLong(0);

    /** Session states. */
    public enum State {
        GREETING, READY, MAIL, RCPT, DATA, AUTH, QUIT
    }

    private final String sessionId;
    private final String hostname;
    private final SmtpHandler handler;
    private final MessageStore store;
    private final RelayConfig relayConfig;
    private final SSLContext sslContext;
    private final Set<SmtpExtension> extensions;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private State state = State.GREETING;
    private boolean tlsActive = false;
    private boolean authenticated = false;
    private String authenticatedUser = null;
    private String clientHostname = null;

    // Current transaction
    private String mailFrom = null;
    private final List<String> rcptTo = new ArrayList<>();
    private final Map<String, String> mailParams = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> rcptParams = new LinkedHashMap<>();
    private final ByteArrayOutputStream bdatBuffer = new ByteArrayOutputStream();

    // Auth exchange state
    private String authMechanism = null;
    private String cramChallenge = null;
    private int loginStep = 0;
    private String loginUsername = null;

    /**
     * Creates a new SMTP session.
     *
     * @param socket      the client socket
     * @param hostname    the server hostname
     * @param handler     the message handler
     * @param store       the message store
     * @param relayConfig the relay configuration
     * @param sslContext  the SSL context for STARTTLS (may be {@code null})
     */
    public SmtpSession(Socket socket, String hostname, SmtpHandler handler,
                       MessageStore store, RelayConfig relayConfig, SSLContext sslContext) {
        this.sessionId = "S" + ID_COUNTER.incrementAndGet();
        this.socket = Objects.requireNonNull(socket, "socket");
        this.hostname = Objects.requireNonNull(hostname, "hostname");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.store = Objects.requireNonNull(store, "store");
        this.relayConfig = relayConfig != null ? relayConfig : RelayConfig.openRelay();
        this.sslContext = sslContext;
        this.extensions = EnumSet.of(
                SmtpExtension.SIZE,
                SmtpExtension.EIGHT_BIT_MIME,
                SmtpExtension.PIPELINING,
                SmtpExtension.ENHANCED_STATUS_CODES,
                SmtpExtension.DSN,
                SmtpExtension.CHUNKING
        );
        if (sslContext != null) {
            extensions.add(SmtpExtension.STARTTLS);
        }
        extensions.add(SmtpExtension.AUTH);
    }

    /**
     * Runs the session to completion, processing commands from the client.
     */
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            // Send greeting
            sendReply(SmtpReply.greeting(hostname));

            // Process commands
            while (state != State.QUIT) {
                String line = reader.readLine();
                if (line == null) {
                    LOG.debug("[{}] Client disconnected", sessionId);
                    break;
                }
                LOG.debug("[{}] C: {}", sessionId, line);

                if (state == State.AUTH) {
                    handleAuthResponse(line);
                    continue;
                }

                processCommand(line);
            }
        } catch (IOException e) {
            LOG.debug("[{}] Session error: {}", sessionId, e.getMessage());
        } finally {
            close();
        }
    }

    /**
     * Processes a single SMTP command line.
     *
     * @param line the command line (without CRLF)
     * @throws IOException if an I/O error occurs
     */
    private void processCommand(String line) throws IOException {
        String[] parts = SmtpCodec.decodeCommand(line);
        String verb = parts[0];
        String params = parts[1];

        SmtpCommand command;
        try {
            command = SmtpCommand.parse(verb);
        } catch (IllegalArgumentException e) {
            sendReply(SmtpReply.commandUnrecognized());
            return;
        }

        switch (command) {
            case EHLO -> handleEhlo(params);
            case HELO -> handleHelo(params);
            case MAIL -> handleMail(params);
            case RCPT -> handleRcpt(params);
            case DATA -> handleData();
            case BDAT -> handleBdat(params);
            case RSET -> handleRset();
            case QUIT -> handleQuit();
            case NOOP -> handleNoop();
            case VRFY -> handleVrfy(params);
            case EXPN -> handleExpn(params);
            case HELP -> handleHelp();
            case STARTTLS -> handleStartTls();
            case AUTH -> handleAuth(params);
        }
    }

    private void handleEhlo(String params) throws IOException {
        clientHostname = params;
        var lines = new ArrayList<String>();
        lines.add(hostname + " Hello " + (params != null ? params : "unknown"));
        for (SmtpExtension ext : extensions) {
            switch (ext) {
                case SIZE -> lines.add("SIZE " + relayConfig.maxMessageSize());
                case AUTH -> lines.add("AUTH PLAIN LOGIN CRAM-MD5 XOAUTH2");
                default -> lines.add(ext.keyword());
            }
        }
        sendReply(SmtpReply.ofLines(250, lines));
        state = State.READY;
    }

    private void handleHelo(String params) throws IOException {
        clientHostname = params;
        sendReply(SmtpReply.of(250, hostname + " Hello " + (params != null ? params : "unknown")));
        state = State.READY;
    }

    private void handleMail(String params) throws IOException {
        if (state != State.READY) {
            sendReply(SmtpReply.badSequence());
            return;
        }
        if (relayConfig.requireAuth() && !authenticated) {
            sendReply(SmtpReply.authRequired());
            return;
        }

        String address = SmtpCodec.parseMailFromAddress(params);
        String extParams = SmtpCodec.parseExtensionParams(params);

        if (!relayConfig.isSenderAllowed(address)) {
            sendReply(SmtpReply.of(550, EnhancedStatusCode.PERM_REFUSED, "Sender not allowed"));
            return;
        }

        if (!handler.acceptSender(address)) {
            sendReply(SmtpReply.of(550, EnhancedStatusCode.PERM_REFUSED, "Sender rejected"));
            return;
        }

        // Check SIZE parameter
        mailParams.clear();
        if (!extParams.isEmpty()) {
            parseExtensionParams(extParams, mailParams);
            String sizeStr = mailParams.get("SIZE");
            if (sizeStr != null && relayConfig.maxMessageSize() > 0) {
                try {
                    long size = Long.parseLong(sizeStr);
                    if (size > relayConfig.maxMessageSize()) {
                        sendReply(SmtpReply.messageTooLarge());
                        return;
                    }
                } catch (NumberFormatException e) {
                    sendReply(SmtpReply.syntaxError());
                    return;
                }
            }
        }

        mailFrom = address;
        rcptTo.clear();
        rcptParams.clear();
        state = State.MAIL;
        sendReply(SmtpReply.senderOk());
    }

    private void handleRcpt(String params) throws IOException {
        if (state != State.MAIL && state != State.RCPT) {
            sendReply(SmtpReply.badSequence());
            return;
        }

        String address = SmtpCodec.parseRcptToAddress(params);
        String extParams = SmtpCodec.parseExtensionParams(params);

        if (!relayConfig.isRecipientAllowed(address)) {
            sendReply(SmtpReply.of(550, EnhancedStatusCode.PERM_REFUSED,
                    "Relay not permitted for " + address));
            return;
        }

        if (!handler.acceptRecipient(address, mailFrom)) {
            sendReply(SmtpReply.mailboxNotFound());
            return;
        }

        rcptTo.add(address);
        if (!extParams.isEmpty()) {
            var rParams = new LinkedHashMap<String, String>();
            parseExtensionParams(extParams, rParams);
            rcptParams.put(address, rParams);
        }
        state = State.RCPT;
        sendReply(SmtpReply.recipientOk());
    }

    private void handleData() throws IOException {
        if (state != State.RCPT) {
            sendReply(SmtpReply.badSequence());
            return;
        }

        sendReply(SmtpReply.startInput());
        state = State.DATA;

        // Read dot-stuffed data
        var data = new ByteArrayOutputStream();
        String line;
        boolean firstLine = true;
        while ((line = reader.readLine()) != null) {
            if (DotStuffing.isEndOfData(line)) {
                break;
            }
            String unstuffed = DotStuffing.unstuffLine(line);
            if (unstuffed == null) break; // should not happen since we checked isEndOfData

            if (!firstLine) {
                data.write('\r');
                data.write('\n');
            }
            data.write(unstuffed.getBytes(StandardCharsets.UTF_8));
            firstLine = false;
        }

        deliverMessage(data.toByteArray());
    }

    private void handleBdat(String params) throws IOException {
        if (state != State.RCPT && state != State.DATA) {
            sendReply(SmtpReply.badSequence());
            return;
        }

        String[] bdatParams = SmtpCodec.parseBdatParams(params);
        int chunkSize;
        try {
            chunkSize = Integer.parseInt(bdatParams[0]);
        } catch (NumberFormatException e) {
            sendReply(SmtpReply.syntaxError());
            return;
        }
        boolean isLast = bdatParams[1] != null;

        // Read exact number of bytes
        byte[] chunk = new byte[chunkSize];
        int offset = 0;
        var rawIn = socket.getInputStream();
        while (offset < chunkSize) {
            int read = rawIn.read(chunk, offset, chunkSize - offset);
            if (read < 0) break;
            offset += read;
        }
        bdatBuffer.write(chunk, 0, offset);

        if (isLast) {
            deliverMessage(bdatBuffer.toByteArray());
            bdatBuffer.reset();
        } else {
            state = State.DATA;
            sendReply(SmtpReply.of(250, EnhancedStatusCode.SUCCESS_OTHER,
                    chunkSize + " bytes received"));
        }
    }

    private void deliverMessage(byte[] data) throws IOException {
        String messageId = generateMessageId();
        var envelope = new MailEnvelope(mailFrom, rcptTo, data, messageId, mailParams, rcptParams);

        if (!handler.acceptMessage(envelope)) {
            sendReply(SmtpReply.transactionFailed());
            resetTransaction();
            return;
        }

        try {
            var result = store.store(envelope);
            if (result.accepted()) {
                sendReply(SmtpReply.ok(result.messageId()));
            } else {
                sendReply(SmtpReply.of(554, EnhancedStatusCode.PERM_REFUSED, result.message()));
            }
        } catch (MessageStoreException e) {
            LOG.error("[{}] Message store error", sessionId, e);
            sendReply(SmtpReply.of(451, EnhancedStatusCode.TRANS_SERVICE_UNAVAIL,
                    "Requested action aborted: local error in processing"));
        }

        resetTransaction();
    }

    private void handleRset() throws IOException {
        resetTransaction();
        sendReply(SmtpReply.ok());
    }

    private void handleQuit() throws IOException {
        sendReply(SmtpReply.closing(hostname));
        state = State.QUIT;
    }

    private void handleNoop() throws IOException {
        sendReply(SmtpReply.ok());
    }

    private void handleVrfy(String params) throws IOException {
        // Return 252 (cannot verify, but will attempt delivery)
        sendReply(SmtpReply.of(252, EnhancedStatusCode.SUCCESS_OTHER,
                "Cannot VRFY user, but will accept message and attempt delivery"));
    }

    private void handleExpn(String params) throws IOException {
        sendReply(SmtpReply.notImplemented());
    }

    private void handleHelp() throws IOException {
        var lines = List.of(
                "Supported commands:",
                "EHLO HELO MAIL RCPT DATA BDAT RSET QUIT NOOP VRFY HELP AUTH STARTTLS"
        );
        sendReply(SmtpReply.ofLines(214, lines));
    }

    private void handleStartTls() throws IOException {
        if (sslContext == null) {
            sendReply(SmtpReply.notImplemented());
            return;
        }
        if (tlsActive) {
            sendReply(SmtpReply.of(503, EnhancedStatusCode.PERM_BAD_SEQUENCE, "TLS already active"));
            return;
        }

        sendReply(SmtpReply.of(220, "Ready to start TLS"));

        try {
            var sslSocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, socket.getInetAddress().getHostAddress(),
                            socket.getPort(), true);
            sslSocket.setUseClientMode(false);
            sslSocket.startHandshake();

            socket = sslSocket;
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            tlsActive = true;
            state = State.GREETING; // Client must re-EHLO after STARTTLS

            LOG.debug("[{}] TLS handshake complete", sessionId);
        } catch (IOException e) {
            LOG.error("[{}] TLS handshake failed", sessionId, e);
            state = State.QUIT;
        }
    }

    private void handleAuth(String params) throws IOException {
        if (state != State.READY) {
            sendReply(SmtpReply.badSequence());
            return;
        }
        if (authenticated) {
            sendReply(SmtpReply.of(503, EnhancedStatusCode.PERM_BAD_SEQUENCE, "Already authenticated"));
            return;
        }

        if (params == null || params.isBlank()) {
            sendReply(SmtpReply.syntaxError());
            return;
        }

        String[] authParts = params.split("\\s+", 2);
        authMechanism = authParts[0].toUpperCase();
        String initialResponse = authParts.length > 1 ? authParts[1] : null;

        switch (authMechanism) {
            case "PLAIN" -> handleAuthPlain(initialResponse);
            case "LOGIN" -> handleAuthLogin(initialResponse);
            case "CRAM-MD5" -> handleAuthCramMd5();
            case "XOAUTH2" -> handleAuthXOAuth2(initialResponse);
            default -> sendReply(SmtpReply.of(504, EnhancedStatusCode.PERM_PARAM_NOT_IMPL,
                    "Unrecognized authentication mechanism"));
        }
    }

    private void handleAuthPlain(String initialResponse) throws IOException {
        if (initialResponse != null && !initialResponse.isEmpty()) {
            verifyPlainCredentials(initialResponse);
        } else {
            // Send empty challenge
            sendReply(SmtpReply.authChallenge(""));
            state = State.AUTH;
        }
    }

    private void handleAuthLogin(String initialResponse) throws IOException {
        loginStep = 0;
        loginUsername = null;
        if (initialResponse != null && !initialResponse.isEmpty()) {
            loginUsername = LoginAuth.decodeResponse(initialResponse);
            loginStep = 1;
            sendReply(SmtpReply.authChallenge(LoginAuth.passwordChallenge()));
            state = State.AUTH;
        } else {
            sendReply(SmtpReply.authChallenge(LoginAuth.usernameChallenge()));
            state = State.AUTH;
        }
    }

    private void handleAuthCramMd5() throws IOException {
        cramChallenge = CramMd5Auth.generateChallenge(hostname);
        sendReply(SmtpReply.authChallenge(cramChallenge));
        state = State.AUTH;
    }

    private void handleAuthXOAuth2(String initialResponse) throws IOException {
        if (initialResponse != null && !initialResponse.isEmpty()) {
            verifyXOAuth2Credentials(initialResponse);
        } else {
            sendReply(SmtpReply.authChallenge(""));
            state = State.AUTH;
        }
    }

    private void handleAuthResponse(String line) throws IOException {
        if ("*".equals(line.trim())) {
            // Client cancels auth
            sendReply(SmtpReply.of(501, EnhancedStatusCode.PERM_SYNTAX_ERROR, "Authentication cancelled"));
            state = State.READY;
            return;
        }

        switch (authMechanism) {
            case "PLAIN" -> verifyPlainCredentials(line);
            case "LOGIN" -> handleLoginResponse(line);
            case "CRAM-MD5" -> verifyCramMd5Response(line);
            case "XOAUTH2" -> verifyXOAuth2Credentials(line);
            default -> {
                sendReply(SmtpReply.authFailed());
                state = State.READY;
            }
        }
    }

    private void verifyPlainCredentials(String base64Creds) throws IOException {
        try {
            String[] creds = PlainAuth.decodeCredentials(base64Creds);
            String username = creds[1];
            String password = creds[2];
            if (handler.authenticate(username, password)) {
                authenticated = true;
                authenticatedUser = username;
                sendReply(SmtpReply.authSuccess());
            } else {
                sendReply(SmtpReply.authFailed());
            }
        } catch (SmtpAuthException e) {
            sendReply(SmtpReply.authFailed());
        }
        state = State.READY;
    }

    private void handleLoginResponse(String line) throws IOException {
        if (loginStep == 0) {
            loginUsername = LoginAuth.decodeResponse(line);
            loginStep = 1;
            sendReply(SmtpReply.authChallenge(LoginAuth.passwordChallenge()));
        } else {
            String password = LoginAuth.decodeResponse(line);
            if (handler.authenticate(loginUsername, password)) {
                authenticated = true;
                authenticatedUser = loginUsername;
                sendReply(SmtpReply.authSuccess());
            } else {
                sendReply(SmtpReply.authFailed());
            }
            state = State.READY;
        }
    }

    private void verifyCramMd5Response(String base64Response) throws IOException {
        // Decode the challenge back to get the raw challenge
        String rawChallenge = new String(
                java.util.Base64.getDecoder().decode(cramChallenge), StandardCharsets.UTF_8);
        // Decode the response to get "username digest"
        String decoded = new String(
                java.util.Base64.getDecoder().decode(base64Response), StandardCharsets.UTF_8);
        int spaceIdx = decoded.indexOf(' ');
        if (spaceIdx < 0) {
            sendReply(SmtpReply.authFailed());
            state = State.READY;
            return;
        }

        String username = decoded.substring(0, spaceIdx);
        // For CRAM-MD5, we can't verify without the password, so delegate to handler
        // The handler.authenticate is called with username and the raw challenge+digest
        // For testing purposes, we pass the digest as password
        if (handler.authenticate(username, decoded.substring(spaceIdx + 1))) {
            authenticated = true;
            authenticatedUser = username;
            sendReply(SmtpReply.authSuccess());
        } else {
            sendReply(SmtpReply.authFailed());
        }
        state = State.READY;
    }

    private void verifyXOAuth2Credentials(String base64Creds) throws IOException {
        try {
            var creds = ssg.legoflow.email.smtp.auth.XOAuth2Auth.decodeCredentials(base64Creds);
            // For XOAUTH2, the "password" is the access token
            if (handler.authenticate(creds[0], creds[1])) {
                authenticated = true;
                authenticatedUser = creds[0];
                sendReply(SmtpReply.authSuccess());
            } else {
                sendReply(SmtpReply.authFailed());
            }
        } catch (SmtpAuthException e) {
            sendReply(SmtpReply.authFailed());
        }
        state = State.READY;
    }

    private void resetTransaction() {
        mailFrom = null;
        rcptTo.clear();
        mailParams.clear();
        rcptParams.clear();
        bdatBuffer.reset();
        if (state != State.QUIT) {
            state = State.READY;
        }
    }

    private void sendReply(SmtpReply reply) throws IOException {
        String encoded = SmtpCodec.encodeReply(reply);
        LOG.debug("[{}] S: {}", sessionId, encoded.stripTrailing());
        writer.write(encoded);
        writer.flush();
    }

    private String generateMessageId() {
        return UUID.randomUUID().toString() + "@" + hostname;
    }

    private void parseExtensionParams(String paramString, Map<String, String> target) {
        String[] tokens = paramString.split("\\s+");
        for (String token : tokens) {
            int eqIdx = token.indexOf('=');
            if (eqIdx >= 0) {
                target.put(token.substring(0, eqIdx).toUpperCase(), token.substring(eqIdx + 1));
            } else {
                target.put(token.toUpperCase(), "");
            }
        }
    }

    /**
     * Returns the session ID.
     *
     * @return the session ID
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * Returns the current session state.
     *
     * @return the state
     */
    public State state() {
        return state;
    }

    /**
     * Returns whether TLS is active.
     *
     * @return true if TLS is active
     */
    public boolean isTlsActive() {
        return tlsActive;
    }

    /**
     * Returns whether the client is authenticated.
     *
     * @return true if authenticated
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Returns the authenticated user, if any.
     *
     * @return the username, or {@code null}
     */
    public String authenticatedUser() {
        return authenticatedUser;
    }

    /**
     * Returns the client hostname from EHLO/HELO.
     *
     * @return the client hostname, or {@code null}
     */
    public String clientHostname() {
        return clientHostname;
    }

    @Override
    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            LOG.debug("[{}] Error closing socket", sessionId, e);
        }
    }
}
