package ssg.legoflow.acl.model;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * Holds a certificate and optional private key. Used for user authentication,
 * TLS peer identity, and certificate-based authentication.
 */
public final class CertificateEntry {
    private final String alias;
    private final X509Certificate certificate;
    private final PrivateKey privateKey;

    public CertificateEntry(String alias, X509Certificate certificate, PrivateKey privateKey) {
        this.alias = Objects.requireNonNull(alias);
        this.certificate = Objects.requireNonNull(certificate);
        this.privateKey = privateKey; // may be null for trust-only entries
    }

    public String alias() { return alias; }
    public X509Certificate certificate() { return certificate; }
    public PrivateKey privateKey() { return privateKey; }
    public boolean hasKey() { return privateKey != null; }

    /** Returns a certificate chain suitable for trust stores (just the certificate). */
    public X509Certificate[] trustChain() {
        return new X509Certificate[]{certificate};
    }

    /** Returns a certificate chain suitable for key stores (certificate + CA if available). */
    public X509Certificate[] keyChain() {
        return new X509Certificate[]{certificate};
    }

    @Override public String toString() { return "Cert[" + alias + ", key=" + hasKey() + "]"; }
}
