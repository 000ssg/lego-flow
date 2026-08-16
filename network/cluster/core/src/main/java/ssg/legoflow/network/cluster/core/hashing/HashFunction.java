package ssg.legoflow.network.cluster.core.hashing;

/**
 * SPI for hash functions used in consistent hashing.
 *
 * Implementations must produce consistent 32-bit hash values
 * for the same input.
 */
@FunctionalInterface
public interface HashFunction {

    /**
     * Computes a 32-bit hash of the given byte array.
     *
     * @param data the input data
     * @return an unsigned 32-bit hash value
     */
    long hash(byte[] data);

    /**
     * Computes a 32-bit hash of the given string.
     *
     * @param key the input string
     * @return an unsigned 32-bit hash value
     */
    default long hash(String key) {
        return hash(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Returns the name of this hash function.
     */
    default String name() {
        return "HashFunction";
    }

    /**
     * Default MurmurHash3 implementation.
     */
    static HashFunction murmurHash3() {
        return MurmurHash3.INSTANCE;
    }
}
