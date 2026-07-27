package ssg.legoflow.network.ldap.protocol;

import java.util.List;

/**
 * Common components of LDAP result messages (RFC 4511 Section 4.1.9).
 *
 * <pre>{@code
 * LDAPResult ::= SEQUENCE {
 *     resultCode    ENUMERATED { ... },
 *     matchedDN     LDAPDN,
 *     diagnosticMessage LDAPString,
 *     referral      [3] Referral OPTIONAL
 * }
 * }</pre>
 *
 * @param resultCode        the result code
 * @param matchedDn         the matched DN (empty string if not applicable)
 * @param diagnosticMessage the diagnostic message (empty string if none)
 * @param referrals         optional referral URIs
 * @since 1.0.0
 */
public record LdapResult(
        LdapResultCode resultCode,
        String matchedDn,
        String diagnosticMessage,
        List<String> referrals
) {

    /**
     * Creates an LDAP result with validation.
     */
    public LdapResult {
        if (resultCode == null) {
            throw new IllegalArgumentException("Result code must not be null");
        }
        if (matchedDn == null) {
            throw new IllegalArgumentException("Matched DN must not be null");
        }
        if (diagnosticMessage == null) {
            throw new IllegalArgumentException("Diagnostic message must not be null");
        }
        if (referrals == null) {
            throw new IllegalArgumentException("Referrals must not be null");
        }
        referrals = List.copyOf(referrals);
    }

    /**
     * Creates a success result with no diagnostic message.
     *
     * @return a success result
     */
    public static LdapResult success() {
        return new LdapResult(LdapResultCode.SUCCESS, "", "", List.of());
    }

    /**
     * Creates a result with the given code and diagnostic message.
     *
     * @param code    the result code
     * @param message the diagnostic message
     * @return the result
     */
    public static LdapResult of(LdapResultCode code, String message) {
        return new LdapResult(code, "", message, List.of());
    }
}
