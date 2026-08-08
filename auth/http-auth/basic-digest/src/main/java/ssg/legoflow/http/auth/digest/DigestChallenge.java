package ssg.legoflow.http.auth.digest;

import java.util.Objects;

/**
 * Builds WWW-Authenticate challenge headers for HTTP Digest authentication (RFC 7616).
 *
 * @since 0.1.0
 */
public class DigestChallenge {

    private final String realm;
    private final String nonce;
    private final String opaque;
    private final String algorithm;
    private final String qop;
    private final boolean stale;

    /**
     * Creates a digest challenge.
     *
     * @param realm     the authentication realm
     * @param nonce     the server nonce
     * @param opaque    the opaque value
     * @param algorithm the hash algorithm (MD5 or SHA-256)
     * @param qop       the quality of protection options
     * @param stale     whether the nonce is stale (client should retry with new nonce)
     * @since 0.1.0
     */
    public DigestChallenge(String realm, String nonce, String opaque,
                           String algorithm, String qop, boolean stale) {
        this.realm = Objects.requireNonNull(realm);
        this.nonce = Objects.requireNonNull(nonce);
        this.opaque = opaque;
        this.algorithm = algorithm != null ? algorithm : "MD5";
        this.qop = qop != null ? qop : "auth";
        this.stale = stale;
    }

    /**
     * Builds the WWW-Authenticate header value.
     *
     * @return the header value
     * @since 0.1.0
     */
    public String toHeaderValue() {
        var sb = new StringBuilder("Digest ");
        sb.append("realm=\"").append(realm).append("\"");
        sb.append(", nonce=\"").append(nonce).append("\"");
        if (opaque != null) {
            sb.append(", opaque=\"").append(opaque).append("\"");
        }
        sb.append(", algorithm=").append(algorithm);
        sb.append(", qop=\"").append(qop).append("\"");
        if (stale) {
            sb.append(", stale=true");
        }
        return sb.toString();
    }

    // Getters

    public String getRealm() { return realm; }
    public String getNonce() { return nonce; }
    public String getOpaque() { return opaque; }
    public String getAlgorithm() { return algorithm; }
    public String getQop() { return qop; }
    public boolean isStale() { return stale; }
}
