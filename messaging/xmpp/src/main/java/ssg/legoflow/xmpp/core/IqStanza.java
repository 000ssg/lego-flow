package ssg.legoflow.xmpp.core;

import java.util.Objects;

/**
 * XMPP IQ (Info/Query) stanza (RFC 6120).
 *
 * <p>IQ stanzas provide a structured request-response mechanism. Each IQ stanza
 * must have a type of get, set, result, or error.
 *
 * @param id        the stanza identifier
 * @param from      the sender JID
 * @param to        the recipient JID
 * @param iqType    the IQ type (get, set, result, error)
 * @param namespace the namespace of the payload
 * @param payload   the extension payload
 * @since 1.0.0
 */
public record IqStanza(
        String id,
        JID from,
        JID to,
        IqType iqType,
        String namespace,
        XmppExtension payload
) implements Stanza {

    /**
     * IQ stanza types as defined in RFC 6120.
     *
     * @since 1.0.0
     */
    public enum IqType {
        /** Request information or data. */
        GET,
        /** Provide data or make a change. */
        SET,
        /** Response to a successful get or set. */
        RESULT,
        /** Error response. */
        ERROR
    }

    /**
     * Constructs a validated IQ stanza.
     */
    public IqStanza {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(iqType, "iqType must not be null");
    }

    @Override
    public StanzaType type() {
        return StanzaType.IQ;
    }

    /**
     * Creates a GET IQ stanza.
     *
     * @param id      the stanza id
     * @param from    the sender JID
     * @param to      the recipient JID
     * @param payload the extension payload
     * @return a new GET IQ stanza
     */
    public static IqStanza get(String id, JID from, JID to, XmppExtension payload) {
        return new IqStanza(id, from, to, IqType.GET,
                payload != null ? payload.getNamespace() : null, payload);
    }

    /**
     * Creates a SET IQ stanza.
     *
     * @param id      the stanza id
     * @param from    the sender JID
     * @param to      the recipient JID
     * @param payload the extension payload
     * @return a new SET IQ stanza
     */
    public static IqStanza set(String id, JID from, JID to, XmppExtension payload) {
        return new IqStanza(id, from, to, IqType.SET,
                payload != null ? payload.getNamespace() : null, payload);
    }

    /**
     * Creates a RESULT IQ stanza.
     *
     * @param id      the stanza id
     * @param from    the sender JID
     * @param to      the recipient JID
     * @param payload the extension payload (may be null)
     * @return a new RESULT IQ stanza
     */
    public static IqStanza result(String id, JID from, JID to, XmppExtension payload) {
        return new IqStanza(id, from, to, IqType.RESULT,
                payload != null ? payload.getNamespace() : null, payload);
    }

    /**
     * Serializes this stanza to XML.
     *
     * @return the XML string representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<iq");
        sb.append(" id=\"").append(id).append("\"");
        if (from != null) {
            sb.append(" from=\"").append(from.toFullJid()).append("\"");
        }
        if (to != null) {
            sb.append(" to=\"").append(to.toFullJid()).append("\"");
        }
        sb.append(" type=\"").append(iqType.name().toLowerCase()).append("\"");
        sb.append(">");
        if (payload != null) {
            sb.append(payload.toXml());
        }
        sb.append("</iq>");
        return sb.toString();
    }
}
