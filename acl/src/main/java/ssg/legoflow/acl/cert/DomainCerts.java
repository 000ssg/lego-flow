package ssg.legoflow.acl.cert;

import ssg.legoflow.acl.model.CertificateEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds a CA and its signed certificates. Returned by {@link CertificateFactory#generateDomainCerts}.
 */
public record DomainCerts(CertificateEntry ca, List<CertificateEntry> signedCerts) {
    /** All certificates including the CA (for trust stores). */
    public List<CertificateEntry> all() {
        var all = new ArrayList<CertificateEntry>();
        all.add(ca);
        all.addAll(signedCerts);
        return all;
    }
}
