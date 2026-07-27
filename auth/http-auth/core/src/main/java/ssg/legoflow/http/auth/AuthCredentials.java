package ssg.legoflow.http.auth;

/**
 * Extracted credentials from an HTTP request. Each authentication scheme provides
 * its own implementation carrying scheme-specific credential data.
 *
 * @since 1.0.0
 */
public sealed interface AuthCredentials
        permits AuthCredentials.Basic, AuthCredentials.Bearer, AuthCredentials.Digest, AuthCredentials.None {

    /**
     * Basic credentials containing username and password.
     *
     * @param username the username
     * @param password the password
     * @since 1.0.0
     */
    record Basic(String username, String password) implements AuthCredentials {
    }

    /**
     * Bearer token credentials.
     *
     * @param token the bearer token
     * @since 1.0.0
     */
    record Bearer(String token) implements AuthCredentials {
    }

    /**
     * Digest authentication credentials.
     *
     * @param username  the username
     * @param realm     the authentication realm
     * @param nonce     the server-issued nonce
     * @param uri       the request URI
     * @param response  the computed response hash
     * @param algorithm the hash algorithm (MD5 or SHA-256)
     * @param cnonce    the client nonce
     * @param nc        the nonce count
     * @param qop       the quality of protection
     * @param opaque    the opaque value from the server
     * @since 1.0.0
     */
    record Digest(String username, String realm, String nonce, String uri,
                  String response, String algorithm, String cnonce,
                  String nc, String qop, String opaque) implements AuthCredentials {
    }

    /**
     * No credentials found in the request.
     *
     * @since 1.0.0
     */
    record None() implements AuthCredentials {
    }
}
