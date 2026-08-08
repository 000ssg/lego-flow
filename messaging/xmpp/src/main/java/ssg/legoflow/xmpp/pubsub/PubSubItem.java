package ssg.legoflow.xmpp.pubsub;

import java.time.Instant;
import java.util.Objects;

/**
 * A published item in a PubSub node (XEP-0060).
 *
 * @param id        the item identifier
 * @param payload   the item payload (XML content)
 * @param publisher the JID of the publisher (bare JID string)
 * @param timestamp the time the item was published
 * @since 0.1.0
 */
public record PubSubItem(String id, String payload, String publisher, Instant timestamp) {

    /**
     * Constructs a validated PubSub item.
     */
    public PubSubItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    /**
     * Serializes this item to XML.
     *
     * @return the XML representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<item id=\"").append(id).append("\"");
        if (publisher != null) {
            sb.append(" publisher=\"").append(publisher).append("\"");
        }
        if (payload != null) {
            sb.append(">").append(payload).append("</item>");
        } else {
            sb.append("/>");
        }
        return sb.toString();
    }
}
