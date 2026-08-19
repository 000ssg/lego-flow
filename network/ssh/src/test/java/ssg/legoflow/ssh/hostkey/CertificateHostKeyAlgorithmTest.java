package ssg.legoflow.ssh.hostkey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class CertificateHostKeyAlgorithmTest {

    private Ed25519 underlyingAlg;
    private SshKeyPair caKey;
    private CertificateHostKeyAlgorithm certAlg;

    @BeforeEach
    void setUp() {
        underlyingAlg = new Ed25519();
        caKey = SshKeyPair.generate(underlyingAlg);
        certAlg = new CertificateHostKeyAlgorithm(underlyingAlg, caKey);
    }

    @Test
    void testNameReturnsCertVariant() {
        assertThat(certAlg.name()).isEqualTo("ssh-ed25519-cert-v01@openssh.com");
    }

    @Test
    void testNameReturnsCertVariantEcdsa() {
        EcdsaSha2Nistp256 ecdsa = new EcdsaSha2Nistp256();
        SshKeyPair ecdsaCaKey = SshKeyPair.generate(ecdsa);
        CertificateHostKeyAlgorithm ecdsaCert = new CertificateHostKeyAlgorithm(ecdsa, ecdsaCaKey);
        assertThat(ecdsaCert.name()).isEqualTo("ecdsa-sha2-nistp256-cert-v01@openssh.com");
    }

    @Test
    void testNameReturnsCertVariantRsa() {
        RsaSha256 rsa = new RsaSha256();
        SshKeyPair rsaCaKey = SshKeyPair.generate(rsa);
        CertificateHostKeyAlgorithm rsaCert = new CertificateHostKeyAlgorithm(rsa, rsaCaKey);
        assertThat(rsaCert.name()).isEqualTo("rsa-sha2-256-cert-v01@openssh.com");
    }

    @Test
    void testIssueCertificate() {
        KeyPair subjectKey = underlyingAlg.generateKeyPair();
        SshCertificate cert = certAlg.issueCertificate(
                subjectKey, CertType.HOST, "test-host",
                List.of("example.com"),
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(3600));

        assertThat(cert).isNotNull();
        assertThat(cert.certType()).isEqualTo("ssh-ed25519-cert-v01@openssh.com");
        assertThat(cert.type()).isEqualTo(CertType.HOST);
        assertThat(cert.keyId()).isEqualTo("test-host");
    }

    @Test
    void testVerifySignature() {
        KeyPair subjectKey = underlyingAlg.generateKeyPair();
        SshCertificate cert = certAlg.issueCertificate(
                subjectKey, CertType.HOST, "test",
                List.of("host.com"),
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(3600));

        byte[] certBlob = cert.encode();
        byte[] data = "test data".getBytes();
        byte[] sig = underlyingAlg.sign(subjectKey, data);

        assertThat(certAlg.verify(certBlob, data, sig)).isTrue();
    }

    @Test
    void testVerifyWithWrongCaFails() {
        // Issue a certificate with the correct CA
        KeyPair subjectKey = underlyingAlg.generateKeyPair();
        SshCertificate cert = certAlg.issueCertificate(
                subjectKey, CertType.HOST, "test",
                List.of("host.com"),
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(3600));

        // Create a different CA and its cert algorithm
        SshKeyPair wrongCaKey = SshKeyPair.generate(underlyingAlg);
        CertificateHostKeyAlgorithm wrongCertAlg = new CertificateHostKeyAlgorithm(underlyingAlg, wrongCaKey);

        byte[] certBlob = cert.encode();
        byte[] data = "test data".getBytes();
        byte[] sig = underlyingAlg.sign(subjectKey, data);

        // Verification should fail because the cert was signed by a different CA
        assertThat(wrongCertAlg.verify(certBlob, data, sig)).isFalse();
    }

    @Test
    void testVerifyExpiredCertFails() {
        KeyPair subjectKey = underlyingAlg.generateKeyPair();
        SshCertificate cert = certAlg.issueCertificate(
                subjectKey, CertType.HOST, "expired",
                List.of("host.com"),
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600)); // already expired

        byte[] certBlob = cert.encode();
        byte[] data = "test data".getBytes();
        byte[] sig = underlyingAlg.sign(subjectKey, data);

        assertThat(certAlg.verify(certBlob, data, sig)).isFalse();
    }

    @Test
    void testSignDelegatesToUnderlying() {
        KeyPair kp = certAlg.generateKeyPair();
        byte[] data = "sign me".getBytes();
        byte[] sig = certAlg.sign(kp, data);

        assertThat(sig).isNotEmpty();
        // The signature should be verifiable by the underlying algorithm
        byte[] pubBlob = underlyingAlg.encodePublicKey(kp);
        assertThat(underlyingAlg.verify(pubBlob, data, sig)).isTrue();
    }

    @Test
    void testGenerateKeyPairDelegatesToUnderlying() {
        KeyPair kp = certAlg.generateKeyPair();
        assertThat(kp).isNotNull();
        assertThat(kp.getPublic().getAlgorithm()).isIn("Ed25519", "EdDSA");
    }

    @Test
    void testUnderlyingAccessor() {
        assertThat(certAlg.underlying()).isSameAs(underlyingAlg);
    }

    @Test
    void testCaKeyPairAccessor() {
        assertThat(certAlg.caKeyPair()).isSameAs(caKey);
    }

    @Test
    void testHostKeyFactoryCreatesCertAlgorithm() {
        HostKeyAlgorithm alg = HostKeyFactory.create("ssh-ed25519-cert-v01@openssh.com");
        assertThat(alg).isInstanceOf(CertificateHostKeyAlgorithm.class);
        assertThat(alg.name()).isEqualTo("ssh-ed25519-cert-v01@openssh.com");
    }

    @Test
    void testHostKeyFactoryCreatesCertAlgorithmEcdsa() {
        HostKeyAlgorithm alg = HostKeyFactory.create("ecdsa-sha2-nistp256-cert-v01@openssh.com");
        assertThat(alg).isInstanceOf(CertificateHostKeyAlgorithm.class);
    }

    @Test
    void testHostKeyFactoryCreatesCertAlgorithmRsa() {
        HostKeyAlgorithm alg = HostKeyFactory.create("rsa-sha2-256-cert-v01@openssh.com");
        assertThat(alg).isInstanceOf(CertificateHostKeyAlgorithm.class);
    }

    @Test
    void testHostKeyFactoryCreateCertificateWithCustomCa() {
        Ed25519 ed = new Ed25519();
        SshKeyPair customCa = SshKeyPair.generate(ed);
        CertificateHostKeyAlgorithm alg = HostKeyFactory.createCertificate(
                "ssh-ed25519-cert-v01@openssh.com", customCa);
        assertThat(alg.caKeyPair()).isSameAs(customCa);
        assertThat(alg.name()).isEqualTo("ssh-ed25519-cert-v01@openssh.com");
    }

    @Test
    void testHostKeyFactorySupportsCertAlgorithms() {
        assertThat(HostKeyFactory.isSupported("ssh-ed25519-cert-v01@openssh.com")).isTrue();
        assertThat(HostKeyFactory.isSupported("ecdsa-sha2-nistp256-cert-v01@openssh.com")).isTrue();
        assertThat(HostKeyFactory.isSupported("rsa-sha2-256-cert-v01@openssh.com")).isTrue();
    }
}
