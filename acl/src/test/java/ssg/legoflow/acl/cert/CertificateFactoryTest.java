package ssg.legoflow.acl.cert;

import org.junit.jupiter.api.*;
import ssg.legoflow.acl.model.CertificateEntry;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CertificateFactoryTest {

    @Test void selfSignedCertificate() {
        var entry = CertificateFactory.selfSigned("test", "CN=test,O=Test", 2048, 10);
        assertThat(entry.alias()).isEqualTo("test");
        assertThat(entry.certificate()).isNotNull();
        assertThat(entry.privateKey()).isNotNull();
        assertThat(entry.hasKey()).isTrue();
    }

    @Test void selfSignedSubjectDn() {
        var entry = CertificateFactory.selfSigned("alice", "CN=alice,O=Test", 2048, 10);
        var cn = entry.certificate().getSubjectX500Principal().getName();
        assertThat(cn).contains("alice");
    }

    @Test void selfSignedValidity() {
        var notBefore = Instant.now();
        var notAfter = Instant.now().plus(10 * 365L * 24L * 3600L, ChronoUnit.SECONDS);
        var entry = CertificateFactory.selfSigned("test", "CN=test", 2048, notBefore, notAfter);
        assertThat(entry.certificate().getNotBefore()).isCloseTo(notBefore, 1000);
        assertThat(entry.certificate().getNotAfter()).isCloseTo(notAfter, 1000);
    }

    @Test void caSignedCertificate() throws Exception {
        var ca = CertificateFactory.selfSigned("CA", "CN=TestCA,O=Test", 2048, 10);
        var cert = CertificateFactory.caSigned("alice", "CN=alice,O=Test", 2048, ca, 10);
        assertThat(cert.alias()).isEqualTo("alice");
        assertThat(cert.hasKey()).isTrue();
        // Verify signature was done by CA
        cert.certificate().verify(ca.certificate().getPublicKey());
        // Does NOT throw
    }

    @Test void caSignedSubjectVsIssuer() {
        var ca = CertificateFactory.selfSigned("CA", "CN=TestCA,O=Test", 2048, 10);
        var alice = CertificateFactory.caSigned("alice", "CN=alice,O=Test", 2048, ca, 10);
        assertThat(alice.certificate().getSubjectX500Principal().getName()).contains("alice");
        assertThat(alice.certificate().getIssuerX500Principal().getName()).contains("TestCA");
    }

    @Test void generateDomainCerts() {
        var certs = CertificateFactory.generateDomainCerts("TestDomain", 2048, 10,
                "CN=admin,O=Test", "CN=user,O=Test", "CN=guest,O=Test");
        assertThat(certs.ca()).isNotNull();
        assertThat(certs.signedCerts()).hasSize(3);
        assertThat(certs.all()).hasSize(4); // CA + 3 signed
    }

    @Test void domainCertsVerification() throws Exception {
        var certs = CertificateFactory.generateDomainCerts("TestDomain", 2048, 10,
                "CN=admin,O=Test", "CN=user,O=Test");
        for (var cert : certs.signedCerts()) {
            cert.certificate().verify(certs.ca().certificate().getPublicKey());
        }
    }

    @Test void toPKCS12AndBack() throws Exception {
        var entry = CertificateFactory.selfSigned("test", "CN=test", 2048, 1);
        var bytes = CertificateFactory.toPKCS12(entry, "secret".toCharArray());
        assertThat(bytes).isNotEmpty();
        var ks = CertificateFactory.loadPKCS12(bytes, "secret".toCharArray());
        assertThat(ks.containsAlias("test")).isTrue();
        var key = ks.getKey("test", "secret".toCharArray());
        assertThat(key).isNotNull();
    }

    @Test void toTrustStorePKCS12() throws KeyStoreException {
        var certs = CertificateFactory.generateDomainCerts("Test", 2048, 10,
                "CN=a,O=T", "CN=b,O=T");
        var bytes = CertificateFactory.toTrustStorePKCS12(certs.all(), "secret".toCharArray());
        var ks = CertificateFactory.loadPKCS12(bytes, "secret".toCharArray());
        assertThat(ks.size()).isEqualTo(3); // CA + 2 signed
    }

    @Test void differentKeySizes() {
        for (int size : List.of(1024, 2048, 4096)) {
            var entry = CertificateFactory.selfSigned("k" + size, "CN=key", size, 1);
            var key = entry.privateKey();
            assertThat(key.getAlgorithm()).isEqualTo("RSA");
        }
    }

    @Test void multipleCertsSameSubject() throws Exception {
        var ca = CertificateFactory.selfSigned("CA", "CN=MyCA", 2048, 10);
        var cert1 = CertificateFactory.caSigned("c1", "CN=same", 2048, ca, 10);
        var cert2 = CertificateFactory.caSigned("c2", "CN=same", 2048, ca, 10);
        // Both verify against CA
        cert1.certificate().verify(ca.certificate().getPublicKey());
        cert2.certificate().verify(ca.certificate().getPublicKey());
    }
}
