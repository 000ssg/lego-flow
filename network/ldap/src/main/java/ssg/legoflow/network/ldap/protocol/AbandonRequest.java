package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Abandon Request (APPLICATION 16) as defined in RFC 4511 Section 4.11.
 *
 * <pre>{@code
 * AbandonRequest ::= [APPLICATION 16] MessageID
 * }</pre>
 *
 * @param abandonedMessageId the message ID of the operation to abandon
 * @since 0.1.0
 */
public record AbandonRequest(int abandonedMessageId) implements LdapProtocolOp {

    /** APPLICATION tag number for AbandonRequest. */
    public static final int TAG = 16;

    @Override
    public int tagNumber() { return TAG; }
}
