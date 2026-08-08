package ssg.legoflow.ssh.cipher;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Factory for creating SSH cipher instances by algorithm name.
 *
 * @since 0.1.0
 */
public final class CipherFactory {

    private static final Map<String, Supplier<SshCipher>> CIPHERS = Map.of(
            "aes128-ctr", Aes128Ctr::new,
            "aes192-ctr", Aes192Ctr::new,
            "aes256-ctr", Aes256Ctr::new,
            "aes128-gcm@openssh.com", Aes128Gcm::new,
            "aes256-gcm@openssh.com", Aes256Gcm::new,
            "chacha20-poly1305@openssh.com", ChaCha20Poly1305::new
    );

    private CipherFactory() {}

    /**
     * Creates a new cipher instance for the given algorithm name.
     *
     * @param name the SSH cipher algorithm name
     * @return a new cipher instance
     * @throws IllegalArgumentException if the algorithm is not supported
     */
    public static SshCipher create(String name) {
        Supplier<SshCipher> supplier = CIPHERS.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("Unsupported cipher algorithm: " + name);
        }
        return supplier.get();
    }

    /**
     * Returns whether the given cipher algorithm is supported.
     *
     * @param name the algorithm name
     * @return true if supported
     */
    public static boolean isSupported(String name) {
        return CIPHERS.containsKey(name);
    }

    /**
     * Returns all supported cipher algorithm names.
     *
     * @return unmodifiable set of algorithm names
     */
    public static java.util.Set<String> supportedAlgorithms() {
        return CIPHERS.keySet();
    }
}
