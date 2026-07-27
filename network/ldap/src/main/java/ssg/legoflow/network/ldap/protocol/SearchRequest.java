package ssg.legoflow.network.ldap.protocol;

import ssg.legoflow.network.ldap.filter.SearchFilter;

import java.util.List;

/**
 * LDAP Search Request (APPLICATION 3) as defined in RFC 4511 Section 4.5.1.
 *
 * <pre>{@code
 * SearchRequest ::= [APPLICATION 3] SEQUENCE {
 *     baseObject   LDAPDN,
 *     scope        ENUMERATED { baseObject(0), singleLevel(1), wholeSubtree(2) },
 *     derefAliases ENUMERATED { neverDerefAliases(0), ... },
 *     sizeLimit    INTEGER (0..maxInt),
 *     timeLimit    INTEGER (0..maxInt),
 *     typesOnly    BOOLEAN,
 *     filter       Filter,
 *     attributes   AttributeSelection
 * }
 * }</pre>
 *
 * @param baseObject   the base DN for the search
 * @param scope        the search scope
 * @param derefAliases the alias dereferencing policy
 * @param sizeLimit    the maximum number of entries to return (0 = no limit)
 * @param timeLimit    the maximum time in seconds (0 = no limit)
 * @param typesOnly    if true, return only attribute types (no values)
 * @param filter       the search filter
 * @param attributes   the list of attribute descriptions to return (empty = all)
 * @since 1.0.0
 */
public record SearchRequest(
        String baseObject,
        SearchScope scope,
        DerefAliases derefAliases,
        int sizeLimit,
        int timeLimit,
        boolean typesOnly,
        SearchFilter filter,
        List<String> attributes
) implements LdapProtocolOp {

    /** APPLICATION tag number for SearchRequest. */
    public static final int TAG = 3;

    /**
     * Creates a search request with validation.
     */
    public SearchRequest {
        if (baseObject == null) {
            throw new IllegalArgumentException("Base object must not be null");
        }
        if (filter == null) {
            throw new IllegalArgumentException("Filter must not be null");
        }
        if (attributes == null) {
            throw new IllegalArgumentException("Attributes must not be null");
        }
        attributes = List.copyOf(attributes);
    }

    @Override
    public int tagNumber() {
        return TAG;
    }

    /**
     * Creates a subtree search with the given base and filter.
     *
     * @param baseDn the base DN
     * @param filter the search filter
     * @return the search request
     */
    public static SearchRequest subtree(String baseDn, SearchFilter filter) {
        return new SearchRequest(baseDn, SearchScope.WHOLE_SUBTREE,
                DerefAliases.NEVER_DEREF_ALIASES, 0, 0, false, filter, List.of());
    }
}
