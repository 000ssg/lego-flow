package ssg.legoflow.email.smtp.client;

import ssg.legoflow.email.smtp.auth.SmtpAuthException;
import ssg.legoflow.email.smtp.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SMTP client for sending email messages.
 *
 * <p>Supports ESMTP extensions, STARTTLS, SASL authentication, pipelining,
 * and chunked transfer (BDAT). The client manages a single connection and
 * supports multiple mail transactions on the same connection.
 *
 * <p>Usage example:
 * <pre>{@code
 *   var config = SmtpClientConfig.builder("smtp.example.com", 587)
 *       .tlsMode(SmtpClientConfig.TlsMode.STARTTLS)
 *       .auth("user", "password")
 *       .build();
 *   try (var client = new SmtpClient(config)) {
 *       client.connect();
 *       client.send("sender@example.com",
 *           List.of("recipient@example.com"),
 *           "Subject: Test\r\n\r\nHello!");
 *   }
 * }</pre>
 *
 * @since 0.1.0
 */
public final class SmtpClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpClient.class);

    private final SmtpClientConfig config;
    private SmtpConnection connection;

    /**
     * Creates an SMTP client with the given configuration.
     *
     * @param config the client configuration
     */
    public SmtpClient(SmtpClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Establishes the connection (TCP, greeting, EHLO, STARTTLS, AUTH).
     *
     * @throws IOException       if an I/O error occurs
     * @throws SmtpException     if the server rejects any step
     * @throws SmtpAuthException if authentication fails
     */
    public void connect() throws IOException, SmtpException, SmtpAuthException {
        connection = new SmtpConnection(config);
        connection.connect();
    }

    /**
     * Sends an email message using DATA command.
     *
     * @param sender     the sender address
     * @param recipients the recipient addresses
     * @param message    the raw message data (headers + body)
     * @return the server reply to the DATA command
     * @throws IOException   if an I/O error occurs
     * @throws SmtpException if the server rejects any step
     */
    public SmtpReply send(String sender, List<String> recipients, String message)
            throws IOException, SmtpException {
        return send(sender, recipients, message, null);
    }

    /**
     * Sends an email message with MAIL FROM extension parameters.
     *
     * @param sender     the sender address
     * @param recipients the recipient addresses
     * @param message    the raw message data
     * @param mailParams extension parameters (e.g., "SIZE=1024 BODY=8BITMIME")
     * @return the server reply to the DATA command
     * @throws IOException   if an I/O error occurs
     * @throws SmtpException if the server rejects any step
     */
    public SmtpReply send(String sender, List<String> recipients, String message,
                          String mailParams) throws IOException, SmtpException {
        ensureConnected();

        // MAIL FROM
        String mailCmd = SmtpCodec.encodeMailFrom(sender, mailParams);
        connection.sendCommand(SmtpCommand.MAIL, "FROM:<" + sender + ">"
                + (mailParams != null ? " " + mailParams : ""));
        SmtpReply reply = connection.readReply();
        if (!reply.isSuccess()) {
            throw new SmtpException("MAIL FROM rejected", reply);
        }

        // RCPT TO
        for (String recipient : recipients) {
            connection.sendCommand(SmtpCommand.RCPT, "TO:<" + recipient + ">");
            reply = connection.readReply();
            if (!reply.isSuccess()) {
                throw new SmtpException("RCPT TO rejected for " + recipient, reply);
            }
        }

        // DATA
        connection.sendCommand(SmtpCommand.DATA);
        reply = connection.readReply();
        if (!reply.isIntermediate()) {
            throw new SmtpException("DATA rejected", reply);
        }

        // Send message body (dot-stuffed)
        String stuffed = DotStuffing.stuff(message);
        connection.sendLine(stuffed);
        connection.sendLine(".");
        reply = connection.readReply();

        if (!reply.isSuccess()) {
            throw new SmtpException("Message rejected", reply);
        }
        return reply;
    }

    /**
     * Sends an email message using BDAT chunked transfer.
     *
     * @param sender     the sender address
     * @param recipients the recipient addresses
     * @param data       the raw message data as bytes
     * @return the server reply
     * @throws IOException   if an I/O error occurs
     * @throws SmtpException if the server rejects any step
     */
    public SmtpReply sendChunked(String sender, List<String> recipients, byte[] data)
            throws IOException, SmtpException {
        return sendChunked(sender, recipients, data, data.length);
    }

    /**
     * Sends an email message using BDAT chunked transfer with configurable chunk size.
     *
     * @param sender     the sender address
     * @param recipients the recipient addresses
     * @param data       the raw message data
     * @param chunkSize  the maximum chunk size in bytes
     * @return the server reply for the final chunk
     * @throws IOException   if an I/O error occurs
     * @throws SmtpException if the server rejects any step
     */
    public SmtpReply sendChunked(String sender, List<String> recipients, byte[] data,
                                 int chunkSize) throws IOException, SmtpException {
        ensureConnected();

        if (!connection.hasExtension(SmtpExtension.CHUNKING)) {
            throw new SmtpException("Server does not support CHUNKING (BDAT)");
        }

        // MAIL FROM
        connection.sendCommand(SmtpCommand.MAIL, "FROM:<" + sender + ">");
        SmtpReply reply = connection.readReply();
        if (!reply.isSuccess()) {
            throw new SmtpException("MAIL FROM rejected", reply);
        }

        // RCPT TO
        for (String recipient : recipients) {
            connection.sendCommand(SmtpCommand.RCPT, "TO:<" + recipient + ">");
            reply = connection.readReply();
            if (!reply.isSuccess()) {
                throw new SmtpException("RCPT TO rejected for " + recipient, reply);
            }
        }

        // BDAT chunks
        int offset = 0;
        while (offset < data.length) {
            int remaining = data.length - offset;
            int thisChunk = Math.min(remaining, chunkSize);
            boolean isLast = (offset + thisChunk >= data.length);

            String bdatCmd = SmtpCodec.encodeBdat(thisChunk, isLast);
            connection.sendCommand(SmtpCommand.BDAT, thisChunk + (isLast ? " LAST" : ""));
            connection.sendData(Arrays.copyOfRange(data, offset, offset + thisChunk));

            reply = connection.readReply();
            if (!reply.isSuccess()) {
                throw new SmtpException("BDAT rejected", reply);
            }

            offset += thisChunk;
        }

        return reply;
    }

    /**
     * Sends a RSET command to reset the current transaction.
     *
     * @return the server reply
     * @throws IOException   if an I/O error occurs
     * @throws SmtpException if RSET fails
     */
    public SmtpReply reset() throws IOException, SmtpException {
        ensureConnected();
        connection.sendCommand(SmtpCommand.RSET);
        SmtpReply reply = connection.readReply();
        if (!reply.isSuccess()) {
            throw new SmtpException("RSET failed", reply);
        }
        return reply;
    }

    /**
     * Sends a NOOP command (keep-alive).
     *
     * @return the server reply
     * @throws IOException if an I/O error occurs
     */
    public SmtpReply noop() throws IOException {
        ensureConnected();
        connection.sendCommand(SmtpCommand.NOOP);
        return connection.readReply();
    }

    /**
     * Sends a VRFY command.
     *
     * @param user the user or mailbox to verify
     * @return the server reply
     * @throws IOException if an I/O error occurs
     */
    public SmtpReply verify(String user) throws IOException {
        ensureConnected();
        connection.sendCommand(SmtpCommand.VRFY, user);
        return connection.readReply();
    }

    /**
     * Returns the negotiated extensions.
     *
     * @return the extension map
     */
    public Map<SmtpExtension, String> extensions() {
        return connection != null ? connection.extensions() : Map.of();
    }

    /**
     * Returns whether the connection supports a specific extension.
     *
     * @param extension the extension to check
     * @return true if supported
     */
    public boolean hasExtension(SmtpExtension extension) {
        return connection != null && connection.hasExtension(extension);
    }

    /**
     * Returns whether the connection is authenticated.
     *
     * @return true if authenticated
     */
    public boolean isAuthenticated() {
        return connection != null && connection.isAuthenticated();
    }

    @Override
    public void close() throws IOException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    private void ensureConnected() {
        if (connection == null) {
            throw new IllegalStateException("Not connected. Call connect() first.");
        }
    }
}
