package ssg.legoflow.xmpp.core;

import java.util.List;
import java.util.Objects;

/**
 * XMPP presence stanza (RFC 6121).
 *
 * <p>Used for communicating availability status and managing subscriptions
 * between XMPP entities.
 *
 * @param id           the stanza identifier
 * @param from         the sender JID
 * @param to           the recipient JID
 * @param presenceType the type of presence
 * @param show         the availability sub-state
 * @param status       the human-readable status text
 * @param priority     the resource priority (-128 to 127)
 * @param extensions   the list of stanza extensions
 * @since 1.0.0
 */
public record PresenceStanza(
        String id,
        JID from,
        JID to,
        PresenceType presenceType,
        PresenceShow show,
        String status,
        int priority,
        List<XmppExtension> extensions
) implements Stanza {

    /**
     * Presence types as defined in RFC 6121.
     *
     * @since 1.0.0
     */
    public enum PresenceType {
        /** The entity is available (default, no type attribute). */
        AVAILABLE,
        /** The entity is no longer available. */
        UNAVAILABLE,
        /** Request to subscribe to another entity's presence. */
        SUBSCRIBE,
        /** Approve a subscription request. */
        SUBSCRIBED,
        /** Unsubscribe from another entity's presence. */
        UNSUBSCRIBE,
        /** Revoke a previously granted subscription. */
        UNSUBSCRIBED,
        /** Probe for the current presence of an entity. */
        PROBE,
        /** An error occurred related to a presence stanza. */
        ERROR
    }

    /**
     * Presence show values indicating availability sub-state.
     *
     * @since 1.0.0
     */
    public enum PresenceShow {
        /** Actively interested in chatting. */
        CHAT,
        /** Temporarily away. */
        AWAY,
        /** Extended away. */
        XA,
        /** Do not disturb. */
        DND
    }

    /**
     * Constructs a validated presence stanza.
     */
    public PresenceStanza {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(presenceType, "presenceType must not be null");
        if (priority < -128 || priority > 127) {
            throw new IllegalArgumentException("priority must be between -128 and 127, got: " + priority);
        }
        extensions = extensions != null ? List.copyOf(extensions) : List.of();
    }

    @Override
    public StanzaType type() {
        return StanzaType.PRESENCE;
    }

    /**
     * Creates a simple available presence.
     *
     * @param id   the stanza id
     * @param from the sender JID
     * @return a new available presence stanza
     */
    public static PresenceStanza available(String id, JID from) {
        return new PresenceStanza(id, from, null, PresenceType.AVAILABLE, null, null, 0, List.of());
    }

    /**
     * Creates an unavailable presence.
     *
     * @param id   the stanza id
     * @param from the sender JID
     * @return a new unavailable presence stanza
     */
    public static PresenceStanza unavailable(String id, JID from) {
        return new PresenceStanza(id, from, null, PresenceType.UNAVAILABLE, null, null, 0, List.of());
    }

    /**
     * Serializes this stanza to XML.
     *
     * @return the XML string representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<presence");
        sb.append(" id=\"").append(id).append("\"");
        if (from != null) {
            sb.append(" from=\"").append(from.toFullJid()).append("\"");
        }
        if (to != null) {
            sb.append(" to=\"").append(to.toFullJid()).append("\"");
        }
        if (presenceType != PresenceType.AVAILABLE) {
            sb.append(" type=\"").append(presenceType.name().toLowerCase()).append("\"");
        }
        sb.append(">");
        if (show != null) {
            sb.append("<show>").append(show.name().toLowerCase()).append("</show>");
        }
        if (status != null) {
            sb.append("<status>").append(status).append("</status>");
        }
        if (priority != 0) {
            sb.append("<priority>").append(priority).append("</priority>");
        }
        for (var ext : extensions) {
            sb.append(ext.toXml());
        }
        sb.append("</presence>");
        return sb.toString();
    }
}
