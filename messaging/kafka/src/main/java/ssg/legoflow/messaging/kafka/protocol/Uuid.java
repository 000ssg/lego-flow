package ssg.legoflow.messaging.kafka.protocol;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Raw 16-byte UUID conversions for the Kafka wire format.
 *
 * <p>Kafka's {@code uuid} field type is the 16-byte binary form of a
 * {@link java.util.UUID}: the most-significant 64 bits followed by the
 * least-significant 64 bits, both big-endian. These conversions are
 * used by the protocol models (topic IDs from v13+) and by tests; the
 * codec itself only ever handles the raw {@code byte[]} form
 * (see {@code KafkaCodecPrimitives#readUuid}/{@code #writeUuid}).
 *
 * <p>Public because codec classes in other packages validate uuid
 * field lengths via {@link #isValid(byte[])} and tests read/write the
 * wire form.
 */
public final class Uuid {

    /** The length in bytes of every Kafka {@code uuid} field. */
    public static final int SIZE = 16;

    private Uuid() {
    }

    /**
     * Encodes {@code uuid} in its 16-byte wire form.
     *
     * @param uuid the UUID to encode, not {@code null}
     * @return a fresh 16-byte array, most-significant bits first
     */
    public static byte[] bytes(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid must not be null");
        }
        byte[] out = new byte[SIZE];
        ByteBuffer buf = ByteBuffer.wrap(out);
        buf.putLong(uuid.getMostSignificantBits());
        buf.putLong(uuid.getLeastSignificantBits());
        return out;
    }

    /**
     * Decodes the 16-byte wire form of a UUID.
     *
     * @param bytes exactly {@link #SIZE} bytes in wire form, not {@code null}
     * @return the decoded UUID
     * @throws IllegalArgumentException if the length is not 16
     */
    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != SIZE) {
            throw new IllegalArgumentException(
                    "uuid bytes must be " + SIZE + " bytes, got "
                            + (bytes == null ? "null" : bytes.length));
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        return new UUID(buf.getLong(), buf.getLong());
    }

    /**
     * Returns {@code true} when {@code bytes} is a structurally valid
     * Kafka uuid field ({@code null}-checked, 16 bytes).
     *
     * @param bytes the bytes to check
     * @return true when the length is exactly 16
     */
    public static boolean isValid(byte[] bytes) {
        return bytes != null && bytes.length == SIZE;
    }
}
