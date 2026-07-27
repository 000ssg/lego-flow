package ssg.legoflow.xmpp.core;

/**
 * XMPP stanza types as defined in RFC 6120.
 *
 * @since 1.0.0
 */
public enum StanzaType {

    /** A message stanza for sending content between entities. */
    MESSAGE,

    /** A presence stanza for availability and subscription management. */
    PRESENCE,

    /** An IQ (Info/Query) stanza for request-response interactions. */
    IQ
}
