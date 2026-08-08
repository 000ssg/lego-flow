package ssg.legoflow.ssh.compression;

/**
 * Interface for SSH compression algorithms.
 *
 * @since 0.1.0
 */
public interface SshCompression {

    /**
     * Returns the SSH algorithm name.
     *
     * @return the algorithm name (e.g., "none", "zlib")
     */
    String name();

    /**
     * Returns whether this compression is delayed (only active after authentication).
     *
     * @return true if delayed compression
     */
    boolean isDelayed();

    /**
     * Sets whether compression is now active (relevant for delayed compression).
     *
     * @param active true to activate compression
     */
    void setActive(boolean active);

    /**
     * Compresses the given data.
     *
     * @param data the uncompressed data
     * @return the compressed data
     */
    byte[] compress(byte[] data);

    /**
     * Decompresses the given data.
     *
     * @param data the compressed data
     * @return the uncompressed data
     */
    byte[] decompress(byte[] data);
}
