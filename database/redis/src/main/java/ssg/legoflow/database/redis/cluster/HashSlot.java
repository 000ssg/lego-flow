package ssg.legoflow.database.redis.cluster;

import java.nio.charset.StandardCharsets;

/**
 * Calculates Redis Cluster hash slots using CRC16.
 *
 * <p>Redis Cluster uses 16384 hash slots. The slot for a key is computed
 * as {@code CRC16(key) % 16384}. If the key contains a hash tag
 * (e.g., {@code {tag}key}), only the content between the first {@code \{}
 * and the first subsequent {@code \}} is hashed.
 *
 * @since 1.0.0
 */
public final class HashSlot {

    /** Total number of hash slots in a Redis Cluster. */
    public static final int TOTAL_SLOTS = 16384;

    // CRC16-CCITT lookup table
    private static final int[] CRC16_TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc <<= 1;
                }
            }
            CRC16_TABLE[i] = crc & 0xFFFF;
        }
    }

    private HashSlot() {}

    /**
     * Computes the CRC16-CCITT checksum of the given bytes.
     *
     * @param data the input bytes
     * @return CRC16 value
     */
    public static int crc16(byte[] data) {
        int crc = 0;
        for (byte b : data) {
            crc = ((crc << 8) ^ CRC16_TABLE[((crc >>> 8) ^ (b & 0xFF)) & 0xFF]) & 0xFFFF;
        }
        return crc;
    }

    /**
     * Computes the hash slot for the given key.
     *
     * <p>Supports hash tags: if the key contains {@code {tag}}, only
     * "tag" is used for hashing.
     *
     * @param key the Redis key
     * @return hash slot (0-16383)
     */
    public static int slot(String key) {
        byte[] keyBytes = extractHashTag(key).getBytes(StandardCharsets.UTF_8);
        return crc16(keyBytes) % TOTAL_SLOTS;
    }

    /**
     * Extracts the hash tag from a key, if present.
     *
     * @param key the Redis key
     * @return the hash tag content, or the full key if no tag
     */
    static String extractHashTag(String key) {
        int start = key.indexOf('{');
        if (start >= 0) {
            int end = key.indexOf('}', start + 1);
            if (end > start + 1) {
                return key.substring(start + 1, end);
            }
        }
        return key;
    }
}
