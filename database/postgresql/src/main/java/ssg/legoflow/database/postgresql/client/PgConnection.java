package ssg.legoflow.database.postgresql.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.database.postgresql.auth.Md5Auth;
import ssg.legoflow.database.postgresql.auth.ScramSha256;
import ssg.legoflow.database.postgresql.protocol.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Manages the connection lifecycle and authentication negotiation with a PostgreSQL server.
 *
 * @since 1.0.0
 */
public final class PgConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PgConnection.class);

    private final Socket socket;
    private final BufferedInputStream in;
    private final BufferedOutputStream out;
    private final Map<String, String> serverParameters = new LinkedHashMap<>();
    private int processId;
    private int secretKey;
    private TransactionStatus transactionStatus;

    /**
     * Creates a new connection (already connected socket).
     *
     * @param socket the connected socket
     * @throws IOException if an I/O error occurs
     */
    PgConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedInputStream(socket.getInputStream());
        this.out = new BufferedOutputStream(socket.getOutputStream());
    }

    /**
     * Returns the input stream from the server.
     *
     * @return the input stream
     */
    InputStream inputStream() {
        return in;
    }

    /**
     * Returns the output stream to the server.
     *
     * @return the output stream
     */
    OutputStream outputStream() {
        return out;
    }

    /**
     * Performs the startup handshake with the server.
     *
     * @param username the username
     * @param database the database name
     * @param password the password (may be null for trust auth)
     * @throws IOException if the connection or authentication fails
     */
    void startup(String username, String database, String password) throws IOException {
        // Send StartupMessage
        Map<String, String> params = new LinkedHashMap<>();
        params.put("user", username);
        params.put("database", database);
        params.put("client_encoding", "UTF8");

        PgCodec.write(out, PgCodec.encodeFrontend(
                new FrontendMessage.StartupMessage(
                        FrontendMessage.StartupMessage.PROTOCOL_VERSION_30, params)));

        // Process server responses
        while (true) {
            BackendMessage msg = PgCodec.decodeBackend(in);
            switch (msg) {
                case BackendMessage.AuthenticationOk ok -> LOG.debug("Authentication OK");

                case BackendMessage.AuthenticationCleartextPassword cp -> {
                    if (password == null) throw new IOException("Password required");
                    PgCodec.write(out, PgCodec.encodeFrontend(
                            new FrontendMessage.PasswordMessage(password)));
                }

                case BackendMessage.AuthenticationMD5Password md5 -> {
                    if (password == null) throw new IOException("Password required");
                    String md5Hash = Md5Auth.computeMd5(password, username, md5.salt());
                    PgCodec.write(out, PgCodec.encodeFrontend(
                            new FrontendMessage.PasswordMessage(md5Hash)));
                }

                case BackendMessage.AuthenticationSASL sasl -> {
                    if (password == null) throw new IOException("Password required");
                    handleSASL(sasl, username, password);
                }

                case BackendMessage.ParameterStatus ps ->
                        serverParameters.put(ps.name(), ps.value());

                case BackendMessage.BackendKeyData bkd -> {
                    processId = bkd.processId();
                    secretKey = bkd.secretKey();
                }

                case BackendMessage.ReadyForQuery rq -> {
                    transactionStatus = rq.status();
                    return;
                }

                case BackendMessage.ErrorResponse er ->
                        throw new IOException("Server error: " + er.message()
                                + " [" + er.sqlState() + "]");

                case BackendMessage.NoticeResponse nr ->
                        LOG.info("Server notice: {}", nr.message());

                default -> LOG.debug("Unexpected message during startup: {}", msg.getClass().getSimpleName());
            }
        }
    }

    private void handleSASL(BackendMessage.AuthenticationSASL sasl, String username, String password) throws IOException {
        if (!sasl.mechanisms().contains(ScramSha256.MECHANISM)) {
            throw new IOException("Server requires unsupported SASL mechanism: " + sasl.mechanisms());
        }

        var session = new ScramSha256.ClientSession(username, password);
        String clientFirstMessage = session.createClientFirstMessage();

        PgCodec.write(out, PgCodec.encodeFrontend(
                new FrontendMessage.SASLInitialResponse(
                        ScramSha256.MECHANISM,
                        clientFirstMessage.getBytes(StandardCharsets.UTF_8))));

        BackendMessage continueMsg = PgCodec.decodeBackend(in);
        if (!(continueMsg instanceof BackendMessage.AuthenticationSASLContinue saslContinue)) {
            throw new IOException("Expected SASL continue, got: " + continueMsg.getClass().getSimpleName());
        }

        String serverFirstMessage = new String(saslContinue.data(), StandardCharsets.UTF_8);
        String clientFinalMessage = session.processServerFirst(serverFirstMessage);

        PgCodec.write(out, PgCodec.encodeFrontend(
                new FrontendMessage.SASLResponse(clientFinalMessage.getBytes(StandardCharsets.UTF_8))));

        BackendMessage finalMsg = PgCodec.decodeBackend(in);
        if (finalMsg instanceof BackendMessage.AuthenticationSASLFinal saslFinal) {
            String serverFinalMessage = new String(saslFinal.data(), StandardCharsets.UTF_8);
            if (!session.verifyServerFinal(serverFinalMessage)) {
                throw new IOException("Server signature verification failed");
            }
        } else if (finalMsg instanceof BackendMessage.ErrorResponse er) {
            throw new IOException("SASL authentication failed: " + er.message());
        } else {
            throw new IOException("Expected SASL final, got: " + finalMsg.getClass().getSimpleName());
        }
    }

    /**
     * Returns the server parameters received during startup.
     *
     * @return the server parameters
     */
    public Map<String, String> serverParameters() {
        return Collections.unmodifiableMap(serverParameters);
    }

    /**
     * Returns the backend process ID.
     *
     * @return the process ID
     */
    public int processId() {
        return processId;
    }

    /**
     * Returns the secret key for cancel requests.
     *
     * @return the secret key
     */
    public int secretKey() {
        return secretKey;
    }

    /**
     * Returns the current transaction status.
     *
     * @return the transaction status
     */
    public TransactionStatus transactionStatus() {
        return transactionStatus;
    }

    /**
     * Returns whether the connection is open.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        try {
            PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.Terminate()));
        } catch (IOException e) {
            // ignore
        }
        socket.close();
    }
}
