package ssg.legoflow.ssh.compression;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Factory for creating SSH compression instances by algorithm name.
 *
 * @since 0.1.0
 */
public final class CompressionFactory {

    private static final Map<String, Supplier<SshCompression>> COMPRESSION = Map.of(
            "none", NoneCompression::new,
            "zlib", ZlibCompression::new,
            "zlib@openssh.com", ZlibOpenSshCompression::new
    );

    private CompressionFactory() {}

    /**
     * Creates a new compression instance for the given algorithm name.
     *
     * @param name the SSH compression algorithm name
     * @return a new compression instance
     * @throws IllegalArgumentException if the algorithm is not supported
     */
    public static SshCompression create(String name) {
        Supplier<SshCompression> supplier = COMPRESSION.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("Unsupported compression algorithm: " + name);
        }
        return supplier.get();
    }

    /**
     * Returns whether the given compression algorithm is supported.
     *
     * @param name the algorithm name
     * @return true if supported
     */
    public static boolean isSupported(String name) {
        return COMPRESSION.containsKey(name);
    }

    /**
     * Returns all supported compression algorithm names.
     *
     * @return unmodifiable set of algorithm names
     */
    public static java.util.Set<String> supportedAlgorithms() {
        return COMPRESSION.keySet();
    }
}
