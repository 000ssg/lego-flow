package ssg.legoflow.ssh.hostkey;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Certificate-based host key algorithm per OpenSSH PROTOCOL.certkeys.
 *
 * <p>Wraps an underlying {@link HostKeyAlgorithm} and provides certificate
 * issuance and verification. The algorithm name returns the certificate variant
 * (e.g., {@code "ssh-ed25519-cert-v01@openssh.com"}).
 *
 * @since 0.1.0
 */
public final class CertificateHostKeyAlgorithm implements HostKeyAlgorithm {

    private final HostKeyAlgorithm underlying;
    private final SshKeyPair caKeyPair;

    /**
     * Creates a new certificate host key algorithm.
     *
     * @param underlying the underlying host key algorithm
     * @param caKeyPair  the CA key pair used to sign/verify certificates
     */
    public CertificateHostKeyAlgorithm(HostKeyAlgorithm underlying, SshKeyPair caKeyPair) {
        this.underlying = Objects.requireNonNull(underlying, "underlying");
        this.caKeyPair = Objects.requireNonNull(caKeyPair, "caKeyPair");
    }

    @Override
    public String name() {
        return underlying.name() + SshCertificate.CERT_SUFFIX;
    }

    /**
     * Returns the underlying (non-certificate) algorithm.
     *
     * @return the underlying algorithm
     */
    public HostKeyAlgorithm underlying() {
        return underlying;
    }

    /**
     * Returns the CA key pair.
     *
     * @return the CA key pair
     */
    public SshKeyPair caKeyPair() {
        return caKeyPair;
    }

    @Override
    public KeyPair generateKeyPair() {
        return underlying.generateKeyPair();
    }

    @Override
    public byte[] sign(KeyPair keyPair, byte[] data) {
        return underlying.sign(keyPair, data);
    }

    /**
     * Verifies a certificate blob by checking the CA signature and time validity.
     *
     * @param publicKeyBlob the certificate blob
     * @param data          the data that was signed
     * @param signature     the signature to verify
     * @return true if the certificate is valid and the signature verifies
     */
    @Override
    public boolean verify(byte[] publicKeyBlob, byte[] data, byte[] signature) {
        try {
            SshCertificate cert = SshCertificate.parse(publicKeyBlob);

            // Verify the certificate was signed by our trusted CA
            byte[] certCaBlob = cert.signatureKey();
            byte[] expectedCaBlob = caKeyPair.publicKeyBlob();
            if (!java.util.Arrays.equals(certCaBlob, expectedCaBlob)) {
                return false;
            }

            // Verify CA signature on the certificate
            byte[] tbs = cert.toBeSigned();
            String caKeyType = SshPublicKey.keyTypeFromBlob(certCaBlob);
            HostKeyAlgorithm caAlg = HostKeyFactory.create(caKeyType);
            if (!caAlg.verify(certCaBlob, tbs, cert.signature())) {
                return false;
            }

            // Verify time validity
            Instant now = Instant.now();
            long nowSecs = now.getEpochSecond();
            if (nowSecs < cert.validAfter() || nowSecs >= cert.validBefore()) {
                return false;
            }

            // Verify the actual data signature using the underlying algorithm
            return underlying.verify(rebuildPublicKeyBlob(cert), data, signature);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public byte[] encodePublicKey(KeyPair keyPair) {
        return underlying.encodePublicKey(keyPair);
    }

    /**
     * Issues a new certificate for the given subject key.
     *
     * @param subjectKey      the subject's key pair
     * @param type            USER or HOST certificate
     * @param keyId           key identifier string
     * @param principals      list of valid principals
     * @param validAfter      validity start time
     * @param validBefore     validity end time
     * @return the issued certificate
     */
    public SshCertificate issueCertificate(KeyPair subjectKey, CertType type, String keyId,
                                            List<String> principals,
                                            Instant validAfter, Instant validBefore) {
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);

        // Extract public key fields from the underlying algorithm's encoding
        byte[] fullBlob = underlying.encodePublicKey(subjectKey);
        byte[] publicKeyFields = extractPublicKeyFields(fullBlob);

        byte[] caBlob = caKeyPair.publicKeyBlob();

        // Build certificate without signature first
        SshCertificate unsigned = new SshCertificate(
                name(), nonce, publicKeyFields, 1L, type, keyId,
                principals, validAfter.getEpochSecond(), validBefore.getEpochSecond(),
                new byte[0], new byte[0], new byte[0],
                caBlob, new byte[0]
        );

        // Sign the certificate
        byte[] tbs = unsigned.toBeSigned();
        byte[] sig = caKeyPair.sign(tbs);

        return new SshCertificate(
                name(), nonce, publicKeyFields, 1L, type, keyId,
                principals, validAfter.getEpochSecond(), validBefore.getEpochSecond(),
                new byte[0], new byte[0], new byte[0],
                caBlob, sig
        );
    }

    /**
     * Extracts algorithm-specific public key fields from a full SSH public key blob.
     * Strips the initial key type string, leaving only the key data fields.
     */
    private byte[] extractPublicKeyFields(byte[] blob) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(blob);
        int typeLen = buf.getInt();
        buf.position(buf.position() + typeLen); // skip key type string
        byte[] fields = new byte[buf.remaining()];
        buf.get(fields);
        return fields;
    }

    /**
     * Rebuilds a standard SSH public key blob from certificate public key fields.
     */
    private byte[] rebuildPublicKeyBlob(SshCertificate cert) {
        String baseAlg = SshCertificate.baseAlgorithm(cert.certType());
        byte[] algBytes = baseAlg.getBytes();
        byte[] fields = cert.publicKey();
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(4 + algBytes.length + fields.length);
        buf.putInt(algBytes.length);
        buf.put(algBytes);
        buf.put(fields);
        return buf.array();
    }
}
