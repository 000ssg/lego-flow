package ssg.legoflow.xmpp.muc;

import ssg.legoflow.xmpp.core.JID;

import java.time.Instant;
import java.util.Objects;

/**
 * A message within a Multi-User Chat room (XEP-0045).
 *
 * <p>Represents a groupchat message sent to or received from a MUC room.
 *
 * @param id        the stanza identifier
 * @param from      the sender's room JID (room@service/nick)
 * @param roomJid   the bare room JID
 * @param body      the message body
 * @param timestamp the time the message was sent
 * @since 0.1.0
 */
public record MucMessage(String id, JID from, JID roomJid, String body, Instant timestamp) {

    /**
     * Constructs a validated MUC message.
     */
    public MucMessage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(roomJid, "roomJid must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    /**
     * Serializes this message to a groupchat message stanza XML.
     *
     * @return the XML representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<message");
        sb.append(" id=\"").append(id).append("\"");
        if (from != null) {
            sb.append(" from=\"").append(from.toFullJid()).append("\"");
        }
        sb.append(" to=\"").append(roomJid.toBareJid()).append("\"");
        sb.append(" type=\"groupchat\">");
        sb.append("<body>").append(escapeXml(body)).append("</body>");
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
