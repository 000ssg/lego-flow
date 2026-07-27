package ssg.legoflow.email.smtp.client;

import ssg.legoflow.email.smtp.auth.SmtpAuthException;
import ssg.legoflow.email.smtp.protocol.SmtpReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * High-level API for submitting email messages via SMTP.
 *
 * <p>Provides a simple interface for sending emails without dealing with the
 * low-level SMTP protocol. Handles connection management, authentication,
 * and message formatting.
 *
 * <p>Usage example:
 * <pre>{@code
 *   var result = MessageSubmission.send(
 *       SmtpClientConfig.builder("smtp.example.com", 587)
 *           .tlsMode(SmtpClientConfig.TlsMode.STARTTLS)
 *           .auth("user", "password")
 *           .build(),
 *       "sender@example.com",
 *       List.of("recipient@example.com"),
 *       "Subject: Hello\r\n\r\nWorld!");
 * }</pre>
 *
 * @since 1.0.0
 */
public final class MessageSubmission {

    private static final Logger LOG = LoggerFactory.getLogger(MessageSubmission.class);

    private MessageSubmission() {
        // utility class
    }

    /**
     * Sends an email message using the specified configuration.
     *
     * <p>Creates a connection, authenticates if needed, sends the message,
     * and closes the connection.
     *
     * @param config     the SMTP client configuration
     * @param sender     the sender address
     * @param recipients the recipient addresses
     * @param message    the raw message data (headers + body)
     * @return the delivery result
     */
    public static DeliveryResult send(SmtpClientConfig config, String sender,
                                      List<String> recipients, String message) {
        try (var client = new SmtpClient(config)) {
            client.connect();
            SmtpReply reply = client.send(sender, recipients, message);
            return new DeliveryResult(true, reply.code(), reply.text(), null);
        } catch (SmtpException e) {
            LOG.error("SMTP error: {}", e.getMessage());
            return new DeliveryResult(false,
                    e.replyCode(), e.getMessage(), e);
        } catch (SmtpAuthException e) {
            LOG.error("Authentication error: {}", e.getMessage());
            return new DeliveryResult(false, 535, e.getMessage(), e);
        } catch (IOException e) {
            LOG.error("I/O error: {}", e.getMessage());
            return new DeliveryResult(false, -1, e.getMessage(), e);
        }
    }

    /**
     * Sends an email message, building the message from parts.
     *
     * @param config     the SMTP client configuration
     * @param sender     the sender address
     * @param recipients the recipient addresses
     * @param subject    the email subject
     * @param body       the email body (plain text)
     * @return the delivery result
     */
    public static DeliveryResult sendSimple(SmtpClientConfig config, String sender,
                                            List<String> recipients, String subject,
                                            String body) {
        var message = new StringBuilder();
        message.append("From: <").append(sender).append(">\r\n");
        message.append("To: ");
        for (int i = 0; i < recipients.size(); i++) {
            if (i > 0) message.append(", ");
            message.append('<').append(recipients.get(i)).append('>');
        }
        message.append("\r\n");
        message.append("Subject: ").append(subject).append("\r\n");
        message.append("Date: ").append(java.time.OffsetDateTime.now()
                .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)).append("\r\n");
        message.append("MIME-Version: 1.0\r\n");
        message.append("Content-Type: text/plain; charset=utf-8\r\n");
        message.append("Content-Transfer-Encoding: 7bit\r\n");
        message.append("\r\n");
        message.append(body);

        return send(config, sender, recipients, message.toString());
    }

    /**
     * Result of a message delivery attempt.
     *
     * @param success   true if the message was accepted
     * @param code      the SMTP reply code (-1 if no reply)
     * @param message   the result message
     * @param exception the exception, if any
     */
    public record DeliveryResult(boolean success, int code, String message, Exception exception) {

        /**
         * Throws the stored exception if the delivery failed.
         *
         * @throws Exception the failure exception
         */
        public void throwIfFailed() throws Exception {
            if (!success && exception != null) {
                throw exception;
            }
        }
    }
}
