package ssg.legoflow.messaging.nats.client;

import ssg.legoflow.messaging.nats.protocol.NatsHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * NATS message model carrying subject, optional reply-to, headers, and payload.
 *
 * @param subject the message subject
 * @param replyTo the reply-to subject for request/reply, or null
 * @param headers the message headers, or null
 * @param payload the message payload
 * @since 0.1.0
 */
public record NatsMessage(
        String subject,
        String replyTo,
        NatsHeaders headers,
        byte[] payload
) {

    /**
     * Creates a message.
     *
     * @param subject the message subject
     * @param replyTo the reply-to subject, or null
     * @param headers the message headers, or null
     * @param payload the message payload
     */
    public NatsMessage {
        Objects.requireNonNull(subject, "subject must not be null");
        if (payload == null) payload = new byte[0];
    }

    /**
     * Creates a simple message with no headers and no reply-to.
     *
     * @param subject the subject
     * @param payload the payload
     * @return the message
     */
    public static NatsMessage of(String subject, byte[] payload) {
        return new NatsMessage(subject, null, null, payload);
    }

    /**
     * Creates a simple message with a string payload.
     *
     * @param subject the subject
     * @param data    the string payload
     * @return the message
     */
    public static NatsMessage of(String subject, String data) {
        return new NatsMessage(subject, null, null,
                data != null ? data.getBytes(StandardCharsets.UTF_8) : new byte[0]);
    }

    /**
     * Creates a reply message.
     *
     * @param subject the subject
     * @param replyTo the reply-to subject
     * @param payload the payload
     * @return the message
     */
    public static NatsMessage withReplyTo(String subject, String replyTo, byte[] payload) {
        return new NatsMessage(subject, replyTo, null, payload);
    }

    /**
     * Creates a message with headers.
     *
     * @param subject the subject
     * @param headers the headers
     * @param payload the payload
     * @return the message
     */
    public static NatsMessage withHeaders(String subject, NatsHeaders headers, byte[] payload) {
        return new NatsMessage(subject, null, headers, payload);
    }

    /**
     * Returns the payload as a UTF-8 string.
     *
     * @return the string payload
     */
    public String dataAsString() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    /**
     * Returns whether this message has headers.
     *
     * @return true if headers are present
     */
    public boolean hasHeaders() {
        return headers != null && !headers.isEmpty();
    }

    /**
     * Returns whether this message has a reply-to subject.
     *
     * @return true if reply-to is set
     */
    public boolean hasReplyTo() {
        return replyTo != null && !replyTo.isEmpty();
    }

    @Override
    public String toString() {
        return "NatsMessage{subject='" + subject + "', replyTo='" + replyTo
                + "', payloadSize=" + payload.length + "}";
    }
}
