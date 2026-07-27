package ssg.legoflow.email.common.header;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Message-ID, In-Reply-To, and References header parsing per RFC 5322.
 *
 * <p>Message-IDs have the format {@code <local-part@domain>}.
 *
 * @since 1.0.0
 */
public final class MessageId {

    private static final Pattern MSG_ID_PATTERN = Pattern.compile("<([^>]+)>");

    private final String id;

    /**
     * Creates a MessageId from the full ID string (without angle brackets).
     *
     * @param id the message ID (e.g., "unique@example.com")
     */
    public MessageId(String id) {
        this.id = Objects.requireNonNull(id, "Message ID must not be null").trim();
    }

    /**
     * Returns the message ID without angle brackets.
     *
     * @return the raw ID string
     */
    public String id() {
        return id;
    }

    /**
     * Returns the message ID in angle bracket format.
     *
     * @return the formatted ID (e.g., "{@code <unique@example.com>}")
     */
    public String toWireFormat() {
        return "<" + id + ">";
    }

    /**
     * Generates a new unique message ID for the given domain.
     *
     * @param domain the domain part of the message ID
     * @return a new unique MessageId
     */
    public static MessageId generate(String domain) {
        return new MessageId(UUID.randomUUID().toString() + "@" + domain);
    }

    /**
     * Parses a single message ID from a header value.
     *
     * @param headerValue the header value (e.g., "{@code <unique@example.com>}")
     * @return the parsed MessageId, or null if no valid ID is found
     */
    public static MessageId parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        Matcher m = MSG_ID_PATTERN.matcher(headerValue);
        if (m.find()) {
            return new MessageId(m.group(1));
        }
        // Lenient: try without angle brackets
        String trimmed = headerValue.trim();
        if (trimmed.contains("@")) {
            return new MessageId(trimmed);
        }
        return null;
    }

    /**
     * Parses a list of message IDs from References or In-Reply-To headers.
     *
     * @param headerValue the header value containing one or more message IDs
     * @return the list of parsed MessageIds
     */
    public static List<MessageId> parseList(String headerValue) {
        var result = new ArrayList<MessageId>();
        if (headerValue == null || headerValue.isBlank()) {
            return result;
        }
        Matcher m = MSG_ID_PATTERN.matcher(headerValue);
        while (m.find()) {
            result.add(new MessageId(m.group(1)));
        }
        return result;
    }

    /**
     * Serializes a list of message IDs to a header value.
     *
     * @param ids the message IDs to serialize
     * @return the serialized header value
     */
    public static String serializeList(List<MessageId> ids) {
        var sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(ids.get(i).toWireFormat());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageId other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return toWireFormat();
    }
}
