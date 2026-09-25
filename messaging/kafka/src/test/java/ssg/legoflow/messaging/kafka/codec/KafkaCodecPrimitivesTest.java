package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the shared wire primitives.
 *
 * <p>Phase 6a structural sub-step: these primitives were extracted verbatim from the
 * {@code KafkaCodec} monolith into {@link KafkaCodecPrimitives}. The tests lock their
 * wire behavior down before the per-sub-category codec classes start consuming them,
 * so no later sub-step can silently change framing semantics.
 */
class KafkaCodecPrimitivesTest {

    // ===== writeString / readString =====

    @Test
    void writeStringIsInt16LengthPrefixedUtf8() {
        ByteBuffer buf = ByteBuffer.allocate(16);
        KafkaCodecPrimitives.writeString(buf, "abc");
        buf.flip(); // position back to 0 to read the written bytes
        assertThat(buf.getShort()).isEqualTo((short) 3);
        byte[] bytes = new byte[3];
        buf.get(bytes);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("abc");
    }

    @Test
    void stringRoundTrip() {
        String s = "lego-flow-kafka-€";
        ByteBuffer buf = ByteBuffer.allocate(32);
        KafkaCodecPrimitives.writeString(buf, s);
        buf.flip();
        assertThat(KafkaCodecPrimitives.readString(buf)).isEqualTo(s);
    }

    @Test
    void emptyStringRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(4);
        KafkaCodecPrimitives.writeString(buf, "");
        buf.flip();
        assertThat(KafkaCodecPrimitives.readString(buf)).isEmpty();
    }

    // ===== writeNullableString / readNullableString =====

    @Test
    void nullableStringRoundTripNonNull() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        KafkaCodecPrimitives.writeNullableString(buf, "txn-1");
        buf.flip();
        assertThat(KafkaCodecPrimitives.readNullableString(buf)).isEqualTo("txn-1");
    }

    @Test
    void nullableStringRoundTripNull() {
        ByteBuffer buf = ByteBuffer.allocate(4);
        KafkaCodecPrimitives.writeNullableString(buf, null);
        buf.flip();
        assertThat(KafkaCodecPrimitives.readNullableString(buf)).isNull();
    }

    // ===== toBytes =====

    @Test
    void toBytesDrainsRemaining() {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.put(new byte[]{1, 2, 3, 4});
        buf.flip();
        assertThat(KafkaCodecPrimitives.toBytes(buf)).isEqualTo(new byte[]{1, 2, 3, 4});
    }
}
