package ssg.legoflow.network.ldap.protocol;

import ssg.legoflow.network.ldap.control.LdapControl;

import java.util.List;

/**
 * LDAP message envelope as defined in RFC 4511 Section 4.1.1.
 *
 * <p>Every LDAP PDU consists of a message ID, a protocol operation, and an
 * optional list of controls. The message ID correlates requests with responses.
 *
 * <pre>{@code
 * LDAPMessage ::= SEQUENCE {
 *     messageID   MessageID,
 *     protocolOp  CHOICE { ... },
 *     controls    [0] Controls OPTIONAL
 * }
 * }</pre>
 *
 * @param messageId  the message identifier (0 for unsolicited notifications)
 * @param protocolOp the protocol operation
 * @param controls   optional controls (empty list if none)
 * @since 0.1.0
 */
public record LdapMessage(
        int messageId,
        LdapProtocolOp protocolOp,
        List<LdapControl> controls
) {

    /**
     * Creates an LDAP message with validation.
     *
     * @param messageId  the message ID (must be non-negative)
     * @param protocolOp the protocol operation (must not be null)
     * @param controls   the controls (must not be null; use empty list for none)
     */
    public LdapMessage {
        if (messageId < 0) {
            throw new IllegalArgumentException("Message ID must be non-negative: " + messageId);
        }
        if (protocolOp == null) {
            throw new IllegalArgumentException("Protocol operation must not be null");
        }
        if (controls == null) {
            throw new IllegalArgumentException("Controls must not be null");
        }
        controls = List.copyOf(controls);
    }

    /**
     * Creates an LDAP message without controls.
     *
     * @param messageId  the message ID
     * @param protocolOp the protocol operation
     * @return the LDAP message
     */
    public static LdapMessage of(int messageId, LdapProtocolOp protocolOp) {
        return new LdapMessage(messageId, protocolOp, List.of());
    }

    /**
     * Creates an LDAP message with controls.
     *
     * @param messageId  the message ID
     * @param protocolOp the protocol operation
     * @param controls   the controls
     * @return the LDAP message
     */
    public static LdapMessage of(int messageId, LdapProtocolOp protocolOp, List<LdapControl> controls) {
        return new LdapMessage(messageId, protocolOp, controls);
    }
}
