package ssg.legoflow.ssh.mac;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Factory for creating SSH MAC instances by algorithm name.
 *
 * @since 0.1.0
 */
public final class MacFactory {

    private static final Map<String, Supplier<SshMac>> MACS = Map.of(
            "hmac-sha2-256", HmacSha256::new,
            "hmac-sha2-512", HmacSha512::new,
            "hmac-sha2-256-etm@openssh.com", HmacSha256Etm::new,
            "hmac-sha2-512-etm@openssh.com", HmacSha512Etm::new
    );

    private MacFactory() {}

    /**
     * Creates a new MAC instance for the given algorithm name.
     *
     * @param name the SSH MAC algorithm name
     * @return a new MAC instance
     * @throws IllegalArgumentException if the algorithm is not supported
     */
    public static SshMac create(String name) {
        Supplier<SshMac> supplier = MACS.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("Unsupported MAC algorithm: " + name);
        }
        return supplier.get();
    }

    /**
     * Returns whether the given MAC algorithm is supported.
     *
     * @param name the algorithm name
     * @return true if supported
     */
    public static boolean isSupported(String name) {
        return MACS.containsKey(name);
    }

    /**
     * Returns all supported MAC algorithm names.
     *
     * @return unmodifiable set of algorithm names
     */
    public static Set<String> supportedAlgorithms() {
        return MACS.keySet();
    }
}
