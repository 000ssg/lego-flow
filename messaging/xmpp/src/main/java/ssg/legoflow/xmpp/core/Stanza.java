package ssg.legoflow.xmpp.core;

/**
 * Base sealed interface for all XMPP stanzas (RFC 6120).
 *
 * <p>All XMPP communication occurs through three stanza types: message, presence, and IQ.
 *
 * @since 1.0.0
 */
public sealed interface Stanza permits MessageStanza, PresenceStanza, IqStanza {

    /**
     * Returns the unique identifier of this stanza.
     *
     * @return the stanza id
     */
    String id();

    /**
     * Returns the sender JID.
     *
     * @return the from JID
     */
    JID from();

    /**
     * Returns the recipient JID.
     *
     * @return the to JID
     */
    JID to();

    /**
     * Returns the stanza type.
     *
     * @return the type of this stanza
     */
    StanzaType type();
}
