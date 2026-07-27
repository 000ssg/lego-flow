package ssg.legoflow.http.auth.digest;

/**
 * Digest authentication credentials extracted from the Authorization header.
 *
 * @param username  the username
 * @param realm     the authentication realm
 * @param nonce     the server nonce
 * @param uri       the request URI
 * @param response  the computed digest response
 * @param algorithm the hash algorithm (MD5 or SHA-256)
 * @param cnonce    the client nonce
 * @param nc        the nonce count (hex string)
 * @param qop       the quality of protection (auth or auth-int)
 * @param opaque    the opaque value
 * @since 1.0.0
 */
public record DigestCredentials(
        String username,
        String realm,
        String nonce,
        String uri,
        String response,
        String algorithm,
        String cnonce,
        String nc,
        String qop,
        String opaque) {

    /**
     * Returns whether this uses the auth quality of protection.
     *
     * @return true if qop is "auth"
     * @since 1.0.0
     */
    public boolean isQopAuth() {
        return "auth".equals(qop);
    }

    /**
     * Returns whether this uses the auth-int quality of protection.
     *
     * @return true if qop is "auth-int"
     * @since 1.0.0
     */
    public boolean isQopAuthInt() {
        return "auth-int".equals(qop);
    }
}
