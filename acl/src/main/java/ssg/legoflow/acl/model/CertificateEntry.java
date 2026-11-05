package ssg.legoflow.acl.model;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * Holds a certificate, private key, and optional issuer chain. Used for user authentication,
 * TLS peer identity, and certificate-based authentication.
 */
public final class CertificateEntry {
    private final String alias;
    private final X509Certificate certificate;
    private final PrivateKey privateKey;
    private final X509Certificate[] chain;

    public CertificateEntry(String alias, X509Certificate certificate, PrivateKey privateKey) {
        this(alias, certificate, privateKey, new X509Certificate[]{certificate});
    }

    public CertificateEntry(String alias, X509Certificate certificate, PrivateKey privateKey, X509Certificate[] chain) {
        this.alias = Objects.requireNonNull(alias);
        this.certificate = Objects.requireNonNull(certificate);
        this.privateKey = privateKey; // may be null for trust-only entries
        this.chain = Objects.requireNonNull(chain);
    }

    public String alias() { return alias; }
    public X509Certificate certificate() { return certificate; }
    public PrivateKey privateKey() { return privateKey; }
    public boolean hasKey() { return privateKey != null; }

    /** Returns a certificate chain suitable for trust stores (just the certificate). */
    public X509Certificate[] trustChain() {
        return new X509Certificate[]{certificate};
    }

    /** Returns the full certificate chain (leaf + issuer(s)) suitable for key stores. */
    public X509Certificate[] keyChain() {
        return chain;
    }

    @Override public String toString() { return "Cert[" + alias + ", key=" + hasKey() + "]"; }
}
