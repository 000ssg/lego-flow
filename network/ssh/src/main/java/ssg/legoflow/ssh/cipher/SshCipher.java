package ssg.legoflow.ssh.cipher;

/**
 * Interface for SSH encryption and decryption ciphers.
 *
 * <p>Implementations wrap standard JCE cipher algorithms with SSH-specific
 * block and key size requirements.
 *
 * @since 1.0.0
 */
public interface SshCipher {

    /**
     * Returns the SSH algorithm name.
     *
     * @return the algorithm name (e.g., "aes256-ctr")
     */
    String name();

    /**
     * Returns the cipher block size in bytes.
     *
     * @return the block size
     */
    int blockSize();

    /**
     * Returns the key size in bytes.
     *
     * @return the key size
     */
    int keySize();

    /**
     * Returns the IV (initialization vector) size in bytes.
     *
     * @return the IV size
     */
    int ivSize();

    /**
     * Returns whether this cipher provides authenticated encryption (AEAD).
     *
     * @return true if this is an AEAD cipher (e.g., GCM, ChaCha20-Poly1305)
     */
    boolean isAead();

    /**
     * Returns the authentication tag length for AEAD ciphers.
     *
     * @return the tag length in bytes, or 0 for non-AEAD ciphers
     */
    int authTagLength();

    /**
     * Initializes this cipher with key and IV for the given direction.
     *
     * @param key     the encryption key
     * @param iv      the initialization vector
     * @param encrypt true for encryption, false for decryption
     */
    void init(byte[] key, byte[] iv, boolean encrypt);

    /**
     * Encrypts the given plaintext.
     *
     * @param data the plaintext bytes
     * @return the ciphertext bytes
     */
    byte[] encrypt(byte[] data);

    /**
     * Decrypts the given ciphertext.
     *
     * @param data the ciphertext bytes
     * @return the plaintext bytes
     */
    byte[] decrypt(byte[] data);
}
