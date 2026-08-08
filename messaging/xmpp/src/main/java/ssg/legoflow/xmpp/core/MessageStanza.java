package ssg.legoflow.xmpp.core;

import java.util.List;
import java.util.Objects;

/**
 * XMPP message stanza (RFC 6121).
 *
 * <p>Used for sending content between XMPP entities. Supports various message types
 * including chat, groupchat, headline, and normal messages.
 *
 * @param id          the stanza identifier
 * @param from        the sender JID
 * @param to          the recipient JID
 * @param messageType the type of message
 * @param body        the message body text
 * @param subject     the optional message subject
 * @param thread      the optional conversation thread identifier
 * @param extensions  the list of stanza extensions
 * @since 0.1.0
 */
public record MessageStanza(
        String id,
        JID from,
        JID to,
        MessageType messageType,
        String body,
        String subject,
        String thread,
        List<XmppExtension> extensions
) implements Stanza {

    /**
     * Message types as defined in RFC 6121.
     *
     * @since 0.1.0
     */
    public enum MessageType {
        /** One-to-one chat session. */
        CHAT,
        /** Multi-user chat message. */
        GROUPCHAT,
        /** Alert or notification that does not require a reply. */
        HEADLINE,
        /** A standalone message (default type). */
        NORMAL,
        /** An error message in response to a previous message. */
        ERROR
    }

    /**
     * Constructs a validated message stanza.
     */
    public MessageStanza {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(messageType, "messageType must not be null");
        extensions = extensions != null ? List.copyOf(extensions) : List.of();
    }

    @Override
    public StanzaType type() {
        return StanzaType.MESSAGE;
    }

    /**
     * Creates a simple chat message.
     *
     * @param id   the stanza id
     * @param from the sender JID
     * @param to   the recipient JID
     * @param body the message body
     * @return a new chat message stanza
     */
    public static MessageStanza chat(String id, JID from, JID to, String body) {
        return new MessageStanza(id, from, to, MessageType.CHAT, body, null, null, List.of());
    }

    /**
     * Serializes this stanza to XML.
     *
     * @return the XML string representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<message");
        sb.append(" id=\"").append(id).append("\"");
        if (from != null) {
            sb.append(" from=\"").append(from.toFullJid()).append("\"");
        }
        sb.append(" to=\"").append(to.toFullJid()).append("\"");
        sb.append(" type=\"").append(messageType.name().toLowerCase()).append("\"");
        sb.append(">");
        if (subject != null) {
            sb.append("<subject>").append(escapeXml(subject)).append("</subject>");
        }
        if (body != null) {
            sb.append("<body>").append(escapeXml(body)).append("</body>");
        }
        if (thread != null) {
            sb.append("<thread>").append(escapeXml(thread)).append("</thread>");
        }
        for (var ext : extensions) {
            sb.append(ext.toXml());
        }
        sb.append("</message>");
        return sb.toString();
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
