package ssg.legoflow.ssh.mac;

/**
 * Interface for SSH MAC (Message Authentication Code) computation.
 *
 * @since 0.1.0
 */
public interface SshMac {

    /**
     * Returns the SSH algorithm name.
     *
     * @return the algorithm name (e.g., "hmac-sha2-256")
     */
    String name();

    /**
     * Returns the MAC output length in bytes.
     *
     * @return the MAC length
     */
    int macLength();

    /**
     * Returns the key length in bytes.
     *
     * @return the key length
     */
    int keyLength();

    /**
     * Returns whether this MAC uses encrypt-then-MAC mode.
     *
     * @return true if encrypt-then-MAC
     */
    boolean isEncryptThenMac();

    /**
     * Initializes this MAC with the given key.
     *
     * @param key the MAC key
     */
    void init(byte[] key);

    /**
     * Computes the MAC for a packet.
     *
     * @param sequenceNumber the packet sequence number
     * @param data           the data to authenticate
     * @return the computed MAC bytes
     */
    byte[] compute(long sequenceNumber, byte[] data);

    /**
     * Verifies the MAC for a packet.
     *
     * @param sequenceNumber the packet sequence number
     * @param data           the data that was authenticated
     * @param mac            the MAC to verify
     * @return true if the MAC is valid
     */
    default boolean verify(long sequenceNumber, byte[] data, byte[] mac) {
        byte[] expected = compute(sequenceNumber, data);
        return constantTimeEquals(expected, mac);
    }

    /**
     * Constant-time byte array comparison.
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
