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

    // ===== Flexible encoding (Kafka 3.0+, "flexible versions") =====
    // Field layouts unchanged; length fields become unsigned varints and nullable
    // fields add a +1 offset: null string = varint 0, null bytes = varint 1,
    // data = varint(length + 1). The request header's apiKey gets bit 15 set.

    /**
     * Number of bytes an unsigned int32 varint occupies (1–5).
     */
    static int varintSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }

    /** Writes an unsigned int32 varint (7 bits per byte, little-endian groups). */
    static void writeVarint(ByteBuffer buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        buf.put((byte) value);
    }

    /** Reads an unsigned int32 varint. */
    static int readVarint(ByteBuffer buf) {
        int result = 0;
        int shift = 0;
        byte b;
        do {
            b = buf.get();
            result |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return result;
    }

    /**
     * Writes a signed int32 varint (zigzag encoding: (n &lt;&lt; 1) ^ (n &gt;&gt; 31)).
     */
    static void writeVarintSigned(ByteBuffer buf, int value) {
        writeVarint(buf, (value << 1) ^ (value >> 31));
    }

    /**
     * Reads a signed int32 varint (zigzag encoding).
     */
    static int readVarintSigned(ByteBuffer buf) {
        int n = readVarint(buf);
        return (n >>> 1) ^ -(n & 1);
    }

    /**
     * Writes a nullable string in flexible encoding: null = varint 0,
     * otherwise varint(length + 1) + UTF-8 bytes.
     */
    static void writeCompactString(ByteBuffer buf, String s) {
        if (s == null) {
            writeVarint(buf, 0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarint(buf, bytes.length + 1);
        buf.put(bytes);
    }

    /**
     * Reads a nullable string in flexible encoding (null = varint 0).
     *
     * @return the string, or null if the length was 0
     */
    static String readCompactString(ByteBuffer buf) {
        int len = readVarint(buf);
        if (len == 0) return null;
        len -= 1;
        byte[] bytes = new byte[len];
        if (len > 0) buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Writes a nullable byte array in flexible encoding: null = varint 1,
     * otherwise varint(length + 1) + bytes.
     */
    static void writeCompactBytes(ByteBuffer buf, byte[] b) {
        if (b == null) {
            writeVarint(buf, 1);
            return;
        }
        writeVarint(buf, b.length + 1);
        buf.put(b);
    }

    /**
     * Reads a byte array in flexible encoding (null = varint 1).
     *
     * @param buf the read position
     * @return the bytes, or null if the length was 1
     */
    static byte[] readCompactBytes(ByteBuffer buf) {
        int len = readVarint(buf);
        if (len == 1) return null;
        len -= 1;
        byte[] bytes = new byte[len];
        if (len > 0) buf.get(bytes);
        return bytes;
    }

    // ===== Non-nullable compact strings + tagged-field skipping (flexible versions) =====
    // Used by ApiVersions v3, where several string fields (clientSoftwareName, feature
    // names) are non-nullable on the wire: varint(length + 1) + UTF-8 bytes, no null marker.

    /**
     * Writes a non-nullable string in flexible encoding: varint(length + 1) + UTF-8 bytes.
     * A null value is treated as the empty string (varint 1), matching the non-nullable
     * default on the wire.
     */
    static void writeCompactStringNonNullable(ByteBuffer buf, String s) {
        if (s == null) {
            s = "";
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarint(buf, bytes.length + 1);
        buf.put(bytes);
    }

    /**
     * Reads a non-nullable string in flexible encoding.
     *
     * @return the string; the empty string if the length varint was 0 (defensive — a valid
     *         non-nullable field never encodes 0)
     */
    static String readCompactStringNonNullable(ByteBuffer buf) {
        int len = readVarint(buf);
        if (len <= 0) {
            return "";
        }
        len -= 1;
        byte[] bytes = new byte[len];
        if (len > 0) {
            buf.get(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Skips a tagged-fields section: the varint count, then for each field the tag varint,
     * the size varint, and the size bytes. Struct elements and messages carry such a trailer
     * in flexible versions; unknown fields are skipped rather than parsed.
     */
    static void skipTaggedFields(ByteBuffer buf) {
        int count = readVarint(buf);
        for (int i = 0; i < count; i++) {
            readVarint(buf); // tag
            int size = readVarint(buf);
            int pos = buf.position();
            buf.position(pos + size);
        }
    }

    /**
     * Reads a tagged-fields section in flexible encoding: the varint count, then for each
     * field the tag varint, the size varint, and a positioned {@code content} view of the
     * field's bytes. Unlike {@link #skipTaggedFields}, this one hands the content to the
     * caller for typed parsing (size is not pre-consumed).
     *
     * @param buf     the read position (advanced past the whole section)
     * @param tag     the current tag number, or -1 before the first field
     * @param content remaining content bytes of the current field (empty if none)
     * @return the next tag number, or -1 when the section is exhausted
     */
    static int nextTaggedField(ByteBuffer buf, int tag, ByteBuffer content) {
        // position = count on first call (tag == -1)
        if (tag < 0) {
            return readVarint(buf);
        }
        int next = readVarint(buf);
        int size = readVarint(buf);
        int pos = buf.position();
        buf.position(pos + size);
        return next;
    }
}
