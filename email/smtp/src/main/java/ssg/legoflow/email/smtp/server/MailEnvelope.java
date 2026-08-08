package ssg.legoflow.email.smtp.server;

import java.time.Instant;
import java.util.*;

/**
 * Represents an SMTP mail envelope (MAIL FROM + RCPT TO + message data).
 *
 * <p>The envelope contains the sender, recipients, message data, and optional
 * extension parameters (SIZE, BODY, DSN). It is built during an SMTP transaction
 * and delivered to a {@link MessageStore} upon successful DATA/BDAT completion.
 *
 * @since 0.1.0
 */
public final class MailEnvelope {

    private final String sender;
    private final List<String> recipients;
    private final byte[] data;
    private final Instant receivedAt;
    private final String messageId;
    private final Map<String, String> mailParams;
    private final Map<String, Map<String, String>> rcptParams;

    /**
     * Creates a mail envelope.
     *
     * @param sender     the sender address (from MAIL FROM)
     * @param recipients the recipient addresses (from RCPT TO)
     * @param data       the raw message data
     * @param messageId  the assigned message ID
     * @param mailParams MAIL FROM extension parameters
     * @param rcptParams per-recipient extension parameters
     */
    public MailEnvelope(String sender, List<String> recipients, byte[] data,
                        String messageId, Map<String, String> mailParams,
                        Map<String, Map<String, String>> rcptParams) {
        this.sender = sender;
        this.recipients = Collections.unmodifiableList(new ArrayList<>(recipients));
        this.data = data.clone();
        this.receivedAt = Instant.now();
        this.messageId = Objects.requireNonNull(messageId, "messageId");
        this.mailParams = mailParams != null ? Map.copyOf(mailParams) : Map.of();
        this.rcptParams = rcptParams != null ? Map.copyOf(rcptParams) : Map.of();
    }

    /**
     * Creates a simple mail envelope without extension parameters.
     *
     * @param sender     the sender address
     * @param recipients the recipient addresses
     * @param data       the raw message data
     * @param messageId  the assigned message ID
     */
    public MailEnvelope(String sender, List<String> recipients, byte[] data, String messageId) {
        this(sender, recipients, data, messageId, null, null);
    }

    /**
     * Returns the sender address (MAIL FROM).
     *
     * @return the sender, or empty string for bounce messages
     */
    public String sender() {
        return sender;
    }

    /**
     * Returns the recipient addresses (RCPT TO).
     *
     * @return unmodifiable list of recipients
     */
    public List<String> recipients() {
        return recipients;
    }

    /**
     * Returns the raw message data.
     *
     * @return a copy of the message bytes
     */
    public byte[] data() {
        return data.clone();
    }

    /**
     * Returns the message data as a string (UTF-8).
     *
     * @return the message as a string
     */
    public String dataAsString() {
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Returns the time the message was received.
     *
     * @return the receive timestamp
     */
    public Instant receivedAt() {
        return receivedAt;
    }

    /**
     * Returns the assigned message ID.
     *
     * @return the message ID
     */
    public String messageId() {
        return messageId;
    }

    /**
     * Returns the MAIL FROM extension parameters.
     *
     * @return unmodifiable map of parameter names to values
     */
    public Map<String, String> mailParams() {
        return mailParams;
    }

    /**
     * Returns the extension parameters for a specific recipient.
     *
     * @param recipient the recipient address
     * @return unmodifiable map of parameter names to values, or empty map
     */
    public Map<String, String> rcptParams(String recipient) {
        return rcptParams.getOrDefault(recipient, Map.of());
    }

    /**
     * Returns the message size in bytes.
     *
     * @return the data length
     */
    public int size() {
        return data.length;
    }

    @Override
    public String toString() {
        return "MailEnvelope{from=" + sender + ", to=" + recipients
                + ", size=" + data.length + ", id=" + messageId + "}";
    }
}
