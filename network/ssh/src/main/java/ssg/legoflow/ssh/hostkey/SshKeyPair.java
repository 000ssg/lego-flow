package ssg.legoflow.ssh.hostkey;

import java.security.KeyPair;
import java.util.Objects;

/**
 * SSH key pair holder with SSH-specific encoding support.
 *
 * @since 0.1.0
 */
public final class SshKeyPair {

    private final String algorithm;
    private final KeyPair javaKeyPair;
    private final HostKeyAlgorithm hostKeyAlgorithm;

    /**
     * Creates a new SSH key pair.
     *
     * @param algorithm        the SSH algorithm name
     * @param javaKeyPair      the Java key pair
     * @param hostKeyAlgorithm the host key algorithm for encoding/signing
     */
    public SshKeyPair(String algorithm, KeyPair javaKeyPair, HostKeyAlgorithm hostKeyAlgorithm) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.javaKeyPair = Objects.requireNonNull(javaKeyPair, "javaKeyPair");
        this.hostKeyAlgorithm = Objects.requireNonNull(hostKeyAlgorithm, "hostKeyAlgorithm");
    }

    /**
     * Generates a new key pair for the given algorithm.
     *
     * @param algorithm the host key algorithm
     * @return a new SSH key pair
     */
    public static SshKeyPair generate(HostKeyAlgorithm algorithm) {
        KeyPair kp = algorithm.generateKeyPair();
        return new SshKeyPair(algorithm.name(), kp, algorithm);
    }

    /**
     * Returns the SSH algorithm name.
     *
     * @return the algorithm name
     */
    public String algorithm() { return algorithm; }

    /**
     * Returns the Java key pair.
     *
     * @return the key pair
     */
    public KeyPair javaKeyPair() { return javaKeyPair; }

    /**
     * Returns the host key algorithm.
     *
     * @return the algorithm implementation
     */
    public HostKeyAlgorithm hostKeyAlgorithm() { return hostKeyAlgorithm; }

    /**
     * Encodes the public key to SSH wire format.
     *
     * @return the encoded public key blob
     */
    public byte[] publicKeyBlob() {
        return hostKeyAlgorithm.encodePublicKey(javaKeyPair);
    }

    /**
     * Signs data with the private key.
     *
     * @param data the data to sign
     * @return the signature
     */
    public byte[] sign(byte[] data) {
        return hostKeyAlgorithm.sign(javaKeyPair, data);
    }

    /**
     * Returns the public key in OpenSSH format.
     *
     * @return the SSH public key
     */
    public SshPublicKey publicKey() {
        return new SshPublicKey(algorithm, publicKeyBlob(), null);
    }
}
