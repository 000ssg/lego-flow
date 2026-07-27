package ssg.legoflow.ssh.hostkey;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SshCertificateTest {

    @Test
    void testParseEncodeRoundtripEd25519() {
        Ed25519 alg = new Ed25519();
        SshKeyPair caKey = SshKeyPair.generate(alg);
        SshKeyPair subjectKey = SshKeyPair.generate(alg);
        CertificateHostKeyAlgorithm certAlg = new CertificateHostKeyAlgorithm(alg, caKey);

        Instant validAfter = Instant.now().minusSeconds(3600);
        Instant validBefore = Instant.now().plusSeconds(3600);

        SshCertificate cert = certAlg.issueCertificate(
                subjectKey.javaKeyPair(), CertType.HOST, "test-host",
                List.of("example.com"), validAfter, validBefore);

        byte[] encoded = cert.encode();
        SshCertificate parsed = SshCertificate.parse(encoded);

        assertThat(parsed.certType()).isEqualTo("ssh-ed25519-cert-v01@openssh.com");
        assertThat(parsed.serial()).isEqualTo(1L);
        assertThat(parsed.type()).isEqualTo(CertType.HOST);
        assertThat(parsed.keyId()).isEqualTo("test-host");
        assertThat(parsed.validPrincipals()).containsExactly("example.com");
        assertThat(parsed.validAfter()).isEqualTo(validAfter.getEpochSecond());
        assertThat(parsed.validBefore()).isEqualTo(validBefore.getEpochSecond());
    }

    @Test
    void testParseEncodeRoundtripEcdsa() {
        EcdsaSha2Nistp256 alg = new EcdsaSha2Nistp256();
        SshKeyPair caKey = SshKeyPair.generate(alg);
        SshKeyPair subjectKey = SshKeyPair.generate(alg);
        CertificateHostKeyAlgorithm certAlg = new CertificateHostKeyAlgorithm(alg, caKey);

        Instant validAfter = Instant.now().minusSeconds(3600);
        Instant validBefore = Instant.now().plusSeconds(3600);

        SshCertificate cert = certAlg.issueCertificate(
                subjectKey.javaKeyPair(), CertType.USER, "user1",
                List.of("alice", "bob"), validAfter, validBefore);

        byte[] encoded = cert.encode();
        SshCertificate parsed = SshCertificate.parse(encoded);

        assertThat(parsed.certType()).isEqualTo("ecdsa-sha2-nistp256-cert-v01@openssh.com");
        assertThat(parsed.type()).isEqualTo(CertType.USER);
        assertThat(parsed.validPrincipals()).containsExactly("alice", "bob");
    }

    @Test
    void testParseEncodeRoundtripRsa() {
        RsaSha256 alg = new RsaSha256();
        SshKeyPair caKey = SshKeyPair.generate(alg);
        SshKeyPair subjectKey = SshKeyPair.generate(alg);
        CertificateHostKeyAlgorithm certAlg = new CertificateHostKeyAlgorithm(alg, caKey);

        Instant validAfter = Instant.now().minusSeconds(3600);
        Instant validBefore = Instant.now().plusSeconds(3600);

        SshCertificate cert = certAlg.issueCertificate(
                subjectKey.javaKeyPair(), CertType.HOST, "rsa-host",
                List.of("server.local"), validAfter, validBefore);

        byte[] encoded = cert.encode();
        SshCertificate parsed = SshCertificate.parse(encoded);

        assertThat(parsed.certType()).isEqualTo("rsa-sha2-256-cert-v01@openssh.com");
        assertThat(parsed.keyId()).isEqualTo("rsa-host");
    }

    @Test
    void testTypeValidationUser() {
        assertThat(CertType.USER.value()).isEqualTo(1);
        assertThat(CertType.fromValue(1)).isEqualTo(CertType.USER);
    }

    @Test
    void testTypeValidationHost() {
        assertThat(CertType.HOST.value()).isEqualTo(2);
        assertThat(CertType.fromValue(2)).isEqualTo(CertType.HOST);
    }

    @Test
    void testTypeValidationUnknown() {
        assertThatThrownBy(() -> CertType.fromValue(99))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTimeValidityExpired() {
        Ed25519 alg = new Ed25519();
        SshKeyPair caKey = SshKeyPair.generate(alg);
        SshKeyPair subjectKey = SshKeyPair.generate(alg);
        CertificateHostKeyAlgorithm certAlg = new CertificateHostKeyAlgorithm(alg, caKey);

        Instant validAfter = Instant.now().minusSeconds(7200);
        Instant validBefore = Instant.now().minusSeconds(3600); // already expired

        SshCertificate cert = certAlg.issueCertificate(
                subjectKey.javaKeyPair(), CertType.HOST, "expired",
                List.of("host.com"), validAfter, validBefore);

        assertThat(cert.isValid("host.com", Instant.now())).isFalse();
    }

    @Test
    void testTimeValidityNotYetValid() {
        Ed25519 alg = new Ed25519();
        SshKeyPair caKey = SshKeyPair.generate(alg);
        SshKeyPair subjectKey = SshKeyPair.generate(alg);
        CertificateHostKeyAlgorithm certAlg = new CertificateHostKeyAlgorithm(alg, caKey);

        Instant validAfter = Instant.now().plusSeconds(3600); // not yet valid
        Instant validBefore = Instant.now().plusSeconds(7200);

        SshCertificate cert = certAlg.issueCertificate(
                subjectKey.javaKeyPair(), CertType.HOST, "future",
                List.of("host.com"), validAfter, validBefore);

        assertThat(cert.isValid("host.com", Instant.now())).isFalse();
    }

    @Test
    void testTimeValidityValid() {
        Ed25519 alg = new Ed25519();
        SshKeyPair caKey = SshKeyPair.generate(alg);
        SshKeyPair subjectKey = SshKeyPair.generate(alg);
        CertificateHostKeyAlgorithm certAlg = new CertificateHostKeyAlgorithm(alg, caKey);

        Instant validAfter = Instant.now().minusSeconds(3600);
        Instant validBefore = Instant.now().plusSeconds(3600);

        SshCertificate cert = certAlg.issueCertificate(
                subjectKey.javaKeyPair(), CertType.HOST, "valid",
                List.of("host.com"), validAfter, validBefore);

        assertThat(cert.isValid("host.com", Instant.now())).isTrue();
    }

    @Test
    void testPrincipalMatching() {
        Ed25519 alg = new Ed25519();
        SshKeyPair caKey = SshKeyPair.generate(alg);
        SshKeyPair subjectKey = SshKeyPair.generate(alg);
        CertificateHostKeyAlgorithm certAlg = new CertificateHostKeyAlgorithm(alg, caKey);

        Instant validAfter = Instant.now().minusSeconds(3600);
        Instant validBefore = Instant.now().plusSeconds(3600);

        SshCertificate cert = certAlg.issueCertificate(
                subjectKey.javaKeyPair(), CertType.HOST, "multi",
                List.of("host1.com", "host2.com"), validAfter, validBefore);

        assertThat(cert.isValid("host1.com", Instant.now())).isTrue();
        assertThat(cert.isValid("host2.com", Instant.now())).isTrue();
        assertThat(cert.isValid("host3.com", Instant.now())).isFalse();
    }

    @Test
    void testEmptyPrincipalsMatchesAny() {
        Ed25519 alg = new Ed25519();
        SshKeyPair caKey = SshKeyPair.generate(alg);
        SshKeyPair subjectKey = SshKeyPair.generate(alg);
        CertificateHostKeyAlgorithm certAlg = new CertificateHostKeyAlgorithm(alg, caKey);

        Instant validAfter = Instant.now().minusSeconds(3600);
        Instant validBefore = Instant.now().plusSeconds(3600);

        SshCertificate cert = certAlg.issueCertificate(
                subjectKey.javaKeyPair(), CertType.HOST, "wildcard",
                List.of(), validAfter, validBefore);

        assertThat(cert.isValid("anything.com", Instant.now())).isTrue();
    }

    @Test
    void testCertFieldsAccessible() {
        Ed25519 alg = new Ed25519();
        SshKeyPair caKey = SshKeyPair.generate(alg);
        SshKeyPair subjectKey = SshKeyPair.generate(alg);
        CertificateHostKeyAlgorithm certAlg = new CertificateHostKeyAlgorithm(alg, caKey);

        SshCertificate cert = certAlg.issueCertificate(
                subjectKey.javaKeyPair(), CertType.HOST, "mykey",
                List.of("host.com"),
                Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000));

        assertThat(cert.nonce()).hasSize(32);
        assertThat(cert.publicKey()).isNotEmpty();
        assertThat(cert.signatureKey()).isNotEmpty();
        assertThat(cert.signature()).isNotEmpty();
        assertThat(cert.criticalOptions()).isEmpty();
        assertThat(cert.extensions()).isEmpty();
    }

    @Test
    void testIsCertificate() {
        assertThat(SshCertificate.isCertificate("ssh-ed25519-cert-v01@openssh.com")).isTrue();
        assertThat(SshCertificate.isCertificate("ecdsa-sha2-nistp256-cert-v01@openssh.com")).isTrue();
        assertThat(SshCertificate.isCertificate("rsa-sha2-256-cert-v01@openssh.com")).isTrue();
        assertThat(SshCertificate.isCertificate("ssh-ed25519")).isFalse();
    }

    @Test
    void testBaseAlgorithm() {
        assertThat(SshCertificate.baseAlgorithm("ssh-ed25519-cert-v01@openssh.com"))
                .isEqualTo("ssh-ed25519");
        assertThat(SshCertificate.baseAlgorithm("ecdsa-sha2-nistp256-cert-v01@openssh.com"))
                .isEqualTo("ecdsa-sha2-nistp256");
        assertThat(SshCertificate.baseAlgorithm("rsa-sha2-256-cert-v01@openssh.com"))
                .isEqualTo("rsa-sha2-256");
    }

    @Test
    void testCertTypeName() {
        assertThat(SshCertificate.certTypeName("ssh-ed25519"))
                .isEqualTo("ssh-ed25519-cert-v01@openssh.com");
    }
}
