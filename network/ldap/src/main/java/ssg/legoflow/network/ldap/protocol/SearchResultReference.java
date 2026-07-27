package ssg.legoflow.network.ldap.protocol;

import java.util.List;

/**
 * LDAP Search Result Reference (APPLICATION 19) as defined in RFC 4511 Section 4.5.3.
 *
 * <pre>{@code
 * SearchResultReference ::= [APPLICATION 19] SEQUENCE OF URI
 * }</pre>
 *
 * @param uris the referral URIs
 * @since 1.0.0
 */
public record SearchResultReference(List<String> uris) implements LdapProtocolOp {

    /** APPLICATION tag number for SearchResultReference. */
    public static final int TAG = 19;

    /** Creates a search result reference with validation. */
    public SearchResultReference {
        if (uris == null || uris.isEmpty()) {
            throw new IllegalArgumentException("URIs must not be null or empty");
        }
        uris = List.copyOf(uris);
    }

    @Override
    public int tagNumber() {
        return TAG;
    }
}
