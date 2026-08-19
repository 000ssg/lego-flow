package ssg.legoflow.network.ldap.server;

import ssg.legoflow.network.ldap.protocol.*;
import java.util.List;
/**
 * Backend interface for LDAP server operations.
 *
 * <p>Implementations provide the actual directory data and logic for
 * handling LDAP protocol operations.
 *
 * @since 0.1.0
 */
public interface DirectoryBackend {

    /**
     * Handles a bind request.
     *
     * @param request the bind request
     * @return the result
     */
    LdapResult bind(BindRequest request);

    /**
     * Handles a search request.
     *
     * @param request the search request
     * @return the matching entries
     */
    List<SearchResultEntry> search(SearchRequest request);

    /**
     * Handles a compare request.
     *
     * @param request the compare request
     * @return true if the assertion matches
     */
    boolean compare(CompareRequest request);

    /**
     * Handles an add request.
     *
     * @param request the add request
     * @return the result
     */
    LdapResult add(AddRequest request);

    /**
     * Handles a delete request.
     *
     * @param request the delete request
     * @return the result
     */
    LdapResult delete(DeleteRequest request);

    /**
     * Handles a modify request.
     *
     * @param request the modify request
     * @return the result
     */
    LdapResult modify(ModifyRequest request);

    /**
     * Handles a modify DN request.
     *
     * @param request the modify DN request
     * @return the result
     */
    LdapResult modifyDn(ModifyDnRequest request);

    /**
     * Handles an extended request.
     *
     * @param request the extended request
     * @return the extended response
     */
    ExtendedResponse extended(ExtendedRequest request);
}
