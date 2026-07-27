package ssg.legoflow.network.ldap.protocol;

/**
 * Sealed interface representing all LDAP v3 protocol operations (RFC 4511).
 *
 * <p>Each permitted type corresponds to a specific LDAP protocol operation
 * identified by an APPLICATION tag in the BER encoding. The sealed hierarchy
 * enables exhaustive pattern matching in {@code switch} expressions.
 *
 * @since 1.0.0
 */
public sealed interface LdapProtocolOp
        permits BindRequest, BindResponse,
                UnbindRequest,
                SearchRequest, SearchResultEntry, SearchResultDone, SearchResultReference,
                ModifyRequest, ModifyResponse,
                AddRequest, AddResponse,
                DeleteRequest, DeleteResponse,
                ModifyDnRequest, ModifyDnResponse,
                CompareRequest, CompareResponse,
                AbandonRequest,
                ExtendedRequest, ExtendedResponse,
                IntermediateResponse {

    /**
     * Returns the APPLICATION tag number for this protocol operation
     * as defined in RFC 4511 Section 4.2.
     *
     * @return the APPLICATION tag number
     */
    int tagNumber();
}
