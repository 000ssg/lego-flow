package ssg.legoflow.ssh.hostkey;

import java.security.KeyPair;

/**
 * Interface for SSH host key algorithms providing signing and verification operations.
 *
 * @since 0.1.0
 */
public interface HostKeyAlgorithm {

    /**
     * Returns the SSH algorithm name.
     *
     * @return the algorithm name (e.g., "ssh-ed25519", "rsa-sha2-256")
     */
    String name();

    /**
     * Generates a new key pair for this algorithm.
     *
     * @return a new key pair
     */
    KeyPair generateKeyPair();

    /**
     * Signs data with the given private key.
     *
     * @param keyPair the key pair (private key is used)
     * @param data    the data to sign
     * @return the signature bytes in SSH signature format
     */
    byte[] sign(KeyPair keyPair, byte[] data);

    /**
     * Verifies a signature against data using the given public key blob.
     *
     * @param publicKeyBlob the SSH-encoded public key blob
     * @param data          the data that was signed
     * @param signature     the signature bytes
     * @return true if the signature is valid
     */
    boolean verify(byte[] publicKeyBlob, byte[] data, byte[] signature);

    /**
     * Encodes a public key to SSH wire format.
     *
     * @param keyPair the key pair (public key is encoded)
     * @return the SSH-encoded public key blob
     */
    byte[] encodePublicKey(KeyPair keyPair);
}
