package ssg.legoflow.email.smtp.client;

import ssg.legoflow.email.smtp.auth.*;
import ssg.legoflow.email.smtp.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Manages the lifecycle of a single SMTP connection.
 *
 * <p>Handles greeting, EHLO negotiation, optional STARTTLS upgrade, and
 * optional SASL authentication. After setup, the connection is ready for
 * mail transactions.
 *
 * @since 0.1.0
 */
public final class SmtpConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpConnection.class);

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Map<SmtpExtension, String> extensions = Map.of();
    private boolean tlsActive = false;
    private boolean authenticated = false;
    private final SmtpClientConfig config;

    /**
     * Creates a connection with the given configuration.
     *
     * @param config the client configuration
     */
    public SmtpConnection(SmtpClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Establishes the connection: TCP connect, greeting, EHLO, optional STARTTLS/AUTH.
     *
     * @throws IOException       if an I/O error occurs
     * @throws SmtpException     if the server rejects any step
     * @throws SmtpAuthException if authentication fails
     */
    public void connect() throws IOException, SmtpException, SmtpAuthException {
        // TCP connect
        if (config.tlsMode() == SmtpClientConfig.TlsMode.IMPLICIT) {
            SSLContext ctx = config.sslContext() != null ? config.sslContext() : getDefaultSslContext();
            SSLSocketFactory factory = ctx.getSocketFactory();
            socket = factory.createSocket();
            socket.connect(new InetSocketAddress(config.host(), config.port()),
                    (int) config.connectTimeout().toMillis());
            tlsActive = true;
        } else {
            socket = new Socket();
            socket.connect(new InetSocketAddress(config.host(), config.port()),
                    (int) config.connectTimeout().toMillis());
        }
        socket.setSoTimeout((int) config.readTimeout().toMillis());

        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        // Read greeting
        SmtpReply greeting = SmtpCodec.readReply(reader);
        LOG.debug("S: {}", greeting);
        if (!greeting.isSuccess()) {
            throw new SmtpException("Server rejected connection", greeting);
        }

        // EHLO
        ehlo();

        // STARTTLS
        if (config.tlsMode() == SmtpClientConfig.TlsMode.STARTTLS && !tlsActive) {
            if (extensions.containsKey(SmtpExtension.STARTTLS)) {
                startTls();
                ehlo(); // Re-EHLO after TLS
            } else {
                throw new SmtpException("Server does not support STARTTLS",
                        SmtpReply.of(502, "STARTTLS not supported"));
            }
        }

        // AUTH
        if (config.hasAuth()) {
            authenticate();
        }
    }

    /**
     * Sends EHLO and parses the extension list.
     *
     * @throws IOException   if an I/O error occurs
     * @throws SmtpException if the server rejects EHLO
     */
    public void ehlo() throws IOException, SmtpException {
        sendCommand(SmtpCommand.EHLO, config.localHostname());
        SmtpReply reply = readReply();
        if (!reply.isSuccess()) {
            // Fall back to HELO
            sendCommand(SmtpCommand.HELO, config.localHostname());
            reply = readReply();
            if (!reply.isSuccess()) {
                throw new SmtpException("Server rejected HELO", reply);
            }
            extensions = Map.of();
        } else {
            extensions = SmtpExtension.parseEhlo(reply.lines());
        }
    }

    /**
     * Upgrades the connection to TLS via STARTTLS.
     *
     * @throws IOException   if an I/O error occurs
     * @throws SmtpException if the server rejects STARTTLS
     */
    public void startTls() throws IOException, SmtpException {
        sendCommand(SmtpCommand.STARTTLS);
        SmtpReply reply = readReply();
        if (reply.code() != 220) {
            throw new SmtpException("STARTTLS failed", reply);
        }

        SSLContext ctx = config.sslContext() != null ? config.sslContext() : getDefaultSslContext();
        SSLSocket sslSocket = (SSLSocket) ctx.getSocketFactory()
                .createSocket(socket, config.host(), config.port(), true);
        sslSocket.setUseClientMode(true);
        sslSocket.startHandshake();

        socket = sslSocket;
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        tlsActive = true;
    }

    /**
     * Authenticates using the configured credentials.
     *
     * @throws IOException       if an I/O error occurs
     * @throws SmtpException     if the server rejects the auth
     * @throws SmtpAuthException if authentication fails
     */
    public void authenticate() throws IOException, SmtpException, SmtpAuthException {
        SmtpAuthenticator auth = selectAuthenticator();
        String initialResponse = auth.initialResponse();

        String authLine;
        if (initialResponse != null) {
            authLine = auth.mechanism() + " " + initialResponse;
        } else {
            authLine = auth.mechanism();
        }

        sendCommand(SmtpCommand.AUTH, authLine);
        SmtpReply reply = readReply();

        while (reply.isIntermediate() && !auth.isComplete()) {
            String response = auth.respond(reply.text());
            sendLine(response);
            reply = readReply();
        }

        if (reply.code() == 235) {
            authenticated = true;
        } else {
            throw new SmtpAuthException("Authentication failed: " + reply.code() + " " + reply.text());
        }
    }

    /**
     * Sends a raw SMTP command.
     *
     * @param command    the command
     * @param parameters the parameters (may be {@code null})
     * @throws IOException if an I/O error occurs
     */
    public void sendCommand(SmtpCommand command, String parameters) throws IOException {
        String encoded = SmtpCodec.encodeCommand(command, parameters);
        LOG.debug("C: {}", encoded.stripTrailing());
        writer.write(encoded);
        writer.flush();
    }

    /**
     * Sends a raw SMTP command without parameters.
     *
     * @param command the command
     * @throws IOException if an I/O error occurs
     */
    public void sendCommand(SmtpCommand command) throws IOException {
        sendCommand(command, null);
    }

    /**
     * Sends a raw line (for auth responses, data, etc.).
     *
     * @param line the line (without CRLF)
     * @throws IOException if an I/O error occurs
     */
    public void sendLine(String line) throws IOException {
        writer.write(line);
        writer.write("\r\n");
        writer.flush();
    }

    /**
     * Sends raw bytes (for BDAT chunks).
     *
     * @param data the data
     * @throws IOException if an I/O error occurs
     */
    public void sendData(byte[] data) throws IOException {
        socket.getOutputStream().write(data);
        socket.getOutputStream().flush();
    }

    /**
     * Reads a reply from the server.
     *
     * @return the reply
     * @throws IOException if an I/O error occurs
     */
    public SmtpReply readReply() throws IOException {
        SmtpReply reply = SmtpCodec.readReply(reader);
        LOG.debug("S: {}", reply);
        return reply;
    }

    /**
     * Returns the negotiated ESMTP extensions.
     *
     * @return unmodifiable map of extensions
     */
    public Map<SmtpExtension, String> extensions() {
        return extensions;
    }

    /**
     * Returns whether a specific extension is supported.
     *
     * @param extension the extension
     * @return true if supported
     */
    public boolean hasExtension(SmtpExtension extension) {
        return extensions.containsKey(extension);
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

    @Override
    public void close() throws IOException {
        try {
            if (socket != null && !socket.isClosed()) {
                sendCommand(SmtpCommand.QUIT);
                readReply();
            }
        } catch (IOException e) {
            LOG.debug("Error during QUIT", e);
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    // ---- Private ----

    private SmtpAuthenticator selectAuthenticator() {
        String mechanism = config.authMechanism();
        if (mechanism == null) {
            // Auto-select based on server capabilities
            String authParams = extensions.get(SmtpExtension.AUTH);
            List<String> mechanisms = SmtpExtension.parseAuthMechanisms(authParams);

            // Prefer CRAM-MD5 > PLAIN > LOGIN
            if (mechanisms.contains("CRAM-MD5")) {
                mechanism = "CRAM-MD5";
            } else if (mechanisms.contains("PLAIN")) {
                mechanism = "PLAIN";
            } else if (mechanisms.contains("LOGIN")) {
                mechanism = "LOGIN";
            } else {
                mechanism = "PLAIN"; // default
            }
        }

        return switch (mechanism.toUpperCase()) {
            case "PLAIN" -> new PlainAuth(config.username(), config.password());
            case "LOGIN" -> new LoginAuth(config.username(), config.password());
            case "CRAM-MD5" -> new CramMd5Auth(config.username(), config.password());
            case "XOAUTH2" -> new XOAuth2Auth(config.username(), config.password());
            default -> new PlainAuth(config.username(), config.password());
        };
    }

    private SSLContext getDefaultSslContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, null, null);
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create default SSLContext", e);
        }
    }
}
