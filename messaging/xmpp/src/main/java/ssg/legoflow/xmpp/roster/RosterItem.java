package ssg.legoflow.xmpp.roster;

import ssg.legoflow.xmpp.core.JID;

import java.util.List;
import java.util.Objects;

/**
 * An item in the XMPP roster (RFC 6121).
 *
 * @param jid          the JID of the contact
 * @param name         the display name
 * @param subscription the subscription state
 * @param groups       the groups this contact belongs to
 * @since 1.0.0
 */
public record RosterItem(JID jid, String name, SubscriptionType subscription, List<String> groups) {

    /**
     * Subscription types for roster items.
     *
     * @since 1.0.0
     */
    public enum SubscriptionType {
        /** No subscription. */
        NONE,
        /** Subscription to the contact's presence. */
        TO,
        /** The contact is subscribed to our presence. */
        FROM,
        /** Mutual subscription. */
        BOTH,
        /** Item is pending removal. */
        REMOVE
    }

    /**
     * Constructs a validated roster item.
     */
    public RosterItem {
        Objects.requireNonNull(jid, "jid must not be null");
        Objects.requireNonNull(subscription, "subscription must not be null");
        groups = groups != null ? List.copyOf(groups) : List.of();
    }

    /**
     * Serializes this roster item to XML.
     *
     * @return the XML representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<item jid=\"").append(jid.toBareJid()).append("\"");
        if (name != null) {
            sb.append(" name=\"").append(name).append("\"");
        }
        sb.append(" subscription=\"").append(subscription.name().toLowerCase()).append("\"");
        if (groups.isEmpty()) {
            sb.append("/>");
        } else {
            sb.append(">");
            for (var group : groups) {
                sb.append("<group>").append(group).append("</group>");
            }
            sb.append("</item>");
        }
        return sb.toString();
    }
}
