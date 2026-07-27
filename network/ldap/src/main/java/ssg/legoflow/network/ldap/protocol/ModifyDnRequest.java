package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Modify DN Request (APPLICATION 12) as defined in RFC 4511 Section 4.9.
 *
 * <pre>{@code
 * ModifyDNRequest ::= [APPLICATION 12] SEQUENCE {
 *     entry        LDAPDN,
 *     newrdn       RelativeLDAPDN,
 *     deleteoldrdn BOOLEAN,
 *     newSuperior  [0] LDAPDN OPTIONAL
 * }
 * }</pre>
 *
 * @param entry        the DN of the entry to rename
 * @param newRdn       the new RDN
 * @param deleteOldRdn whether to delete the old RDN values
 * @param newSuperior  the optional new parent DN (null if not moving)
 * @since 1.0.0
 */
public record ModifyDnRequest(
        String entry,
        String newRdn,
        boolean deleteOldRdn,
        String newSuperior
) implements LdapProtocolOp {

    /** APPLICATION tag number for ModifyDNRequest. */
    public static final int TAG = 12;

    /** Creates a modify DN request with validation. */
    public ModifyDnRequest {
        if (entry == null) throw new IllegalArgumentException("Entry must not be null");
        if (newRdn == null) throw new IllegalArgumentException("New RDN must not be null");
    }

    @Override
    public int tagNumber() { return TAG; }

    /**
     * Creates a rename request (no move).
     *
     * @param entry        the DN to rename
     * @param newRdn       the new RDN
     * @param deleteOldRdn whether to delete old RDN values
     * @return the request
     */
    public static ModifyDnRequest rename(String entry, String newRdn, boolean deleteOldRdn) {
        return new ModifyDnRequest(entry, newRdn, deleteOldRdn, null);
    }
}
