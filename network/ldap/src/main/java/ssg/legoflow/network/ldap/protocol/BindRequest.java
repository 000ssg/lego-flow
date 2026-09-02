package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Bind Request (APPLICATION 0) as defined in RFC 4511 Section 4.2.
 *
 * <p>Supports both simple authentication (password) and SASL authentication.
 *
 * <pre>{@code
 * BindRequest ::= [APPLICATION 0] SEQUENCE {
 *     version        INTEGER (1..127),
 *     name           LDAPDN,
 *     authentication AuthenticationChoice
 * }
 * }</pre>
 *
 * @param version        the LDAP protocol version (3 for LDAPv3)
 * @param name           the DN to bind as (empty string for anonymous)
 * @param authentication the authentication choice
 * @since 0.1.0
 */
public record BindRequest(
        int version,
        String name,
        AuthenticationChoice authentication
) implements LdapProtocolOp {

    /** APPLICATION tag number for BindRequest. */
    public static final int TAG = 0;

    @Override
    public int tagNumber() {
        return TAG;
    }

    /**
     * Creates a simple bind request.
     *
     * @param name     the DN to bind as
     * @param password the password
     * @return the bind request
     */
    public static BindRequest simple(String name, String password) {
        return new BindRequest(3, name, new AuthenticationChoice.Simple(password));
    }

    /**
     * Creates an anonymous bind request.
     *
     * @return the bind request
     */
    public static BindRequest anonymous() {
        return new BindRequest(3, "", AuthenticationChoice.NULL);
    }

    /**
     * Creates a SASL bind request.
     *
     * @param name      the DN to bind as
     * @param mechanism the SASL mechanism name
     * @param credentials the SASL credentials (may be null)
     * @return the bind request
     */
    public static BindRequest sasl(String name, String mechanism, byte[] credentials) {
        return new BindRequest(3, name, new AuthenticationChoice.Sasl(mechanism, credentials));
    }

    /**
     * Authentication choice for bind requests.
     *
     * @since 0.1.0
     */
    public sealed interface AuthenticationChoice
            permits AuthenticationChoice.Simple, AuthenticationChoice.Sasl, AuthenticationChoice.Anonymous {

        /**
         * Simple authentication (context tag 0) with a password.
         *
         * @param password the password (empty string for anonymous)
         */
        record Simple(String password) implements AuthenticationChoice {}

        /**
         * SASL authentication (context tag 3).
         *
         * @param mechanism   the SASL mechanism name
         * @param credentials the optional SASL credentials
         */
        record Sasl(String mechanism, byte[] credentials) implements AuthenticationChoice {}

        /**
         * NULL authentication — for true anonymous binds (RFC 4511).
         * This is distinct from Simple("") which some servers reject.
         */
        record Anonymous() implements AuthenticationChoice {}

        /** Constant for anonymous (NULL) authentication. */
        AuthenticationChoice NULL = new Anonymous();
    }
}
