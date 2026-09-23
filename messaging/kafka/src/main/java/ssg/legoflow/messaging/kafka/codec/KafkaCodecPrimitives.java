package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Shared low-level wire primitives for the Kafka codec classes.
 *
 * <p>Every per-sub-category codec class ({@code ProduceCodec}, {@code MetadataCodec}, …)
 * builds on these primitives so that string/bytes framing is implemented exactly once.
 * Method semantics are unchanged from the original monolith
 * ({@code KafkaCodec}, pre-split); they were extracted as the first structural sub-step
 * of Phase 6a with zero behavior change (full suite re-run, see
 * {@code doc/plans/messaging/PHASE6A_KAFKA_CODEC_VERSIONS.md} §2 step 1).
 *
 * <p>New primitives required by specific API versions (varint, zigzag, compact strings,
 * byte-buffer fields) are added here as part of the version sub-task that first needs them —
 * never from wire findings, always from the spec layout in {@code doc/spec/message/}.
 *
 * @since 0.1.0
 */
final class KafkaCodecPrimitives {

    private KafkaCodecPrimitives() {
    }

    /** Writes a non-null string as int16 length + UTF-8 bytes (Kafka "string" / "kstring"). */
    static void writeString(ByteBuffer buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.putShort((short) bytes.length);
        buf.put(bytes);
    }

    /**
     * Writes a nullable string: -1 length if null, otherwise as {@link #writeString}.
     * Kafka "nullable string".
     */
    static void writeNullableString(ByteBuffer buf, String s) {
        if (s == null) {
            buf.putShort((short) -1);
        } else {
            writeString(buf, s);
        }
    }

    /**
     * Reads a non-null string (int16 length + UTF-8 bytes).
     *
     * @return the string, or the empty string if the length was negative (defensive; the
     *         Kafka wire format does not produce this for non-nullable strings)
     */
    static String readString(ByteBuffer buf) {
        short len = buf.getShort();
        if (len < 0) return "";
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads a nullable string: null if the length is -1.
     *
     * @return the string, or null if the length was -1
     */
    static String readNullableString(ByteBuffer buf) {
        short len = buf.getShort();
        if (len < 0) return null;
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Drains the remaining buffer contents into a new byte array.
     *
     * @param buf the positioned buffer (after {@code flip()} or at the end of a body)
     * @return a fresh array holding the remaining bytes
     */
    static byte[] toBytes(ByteBuffer buf) {
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Writes a nullable byte array as int32 length + bytes (Kafka "bytes").
     * Null is written as length -1 per the spec definition of "bytes".
     *
     * @param buf the write position
     * @param b   the bytes, or null
     */
    static void writeBytesField(ByteBuffer buf, byte[] b) {
        if (b == null) {
            buf.putInt(-1);
        } else {
            buf.putInt(b.length);
            buf.put(b);
        }
    }

    /**
     * Reads a byte array field (int32 length + bytes).
     *
     * @param buf the read position
     * @return the bytes, or null if the length was -1
     */
    static byte[] readBytesField(ByteBuffer buf) {
        int len = buf.getInt();
        if (len < 0) return null;
        byte[] bytes = new byte[len];
        if (len > 0) buf.get(bytes);
        return bytes;
    }
}
