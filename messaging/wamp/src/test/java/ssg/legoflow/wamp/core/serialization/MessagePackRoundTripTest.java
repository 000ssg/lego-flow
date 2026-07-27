package ssg.legoflow.wamp.core.serialization;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip tests: encode then decode, verifying the result matches the original.
 */
class MessagePackRoundTripTest {

    private final MessagePackEncoder encoder = new MessagePackEncoder();
    private final MessagePackDecoder decoder = new MessagePackDecoder();

    @Test
    void testRoundTripNull() {
        assertThat(decoder.decode(encoder.encode(null))).isNull();
    }

    @Test
    void testRoundTripBooleans() {
        assertThat(decoder.decode(encoder.encode(true))).isEqualTo(true);
        assertThat(decoder.decode(encoder.encode(false))).isEqualTo(false);
    }

    @Test
    void testRoundTripSmallIntegers() {
        for (int i = -32; i <= 127; i++) {
            assertThat(((Number) decoder.decode(encoder.encode(i))).intValue()).isEqualTo(i);
        }
    }

    @Test
    void testRoundTripLargeIntegers() {
        long[] values = {128, 255, 256, 65535, 65536, 0xFFFFFFFFL, 0x1_0000_0000L,
                -33, -128, -129, -32768, -32769, Long.MIN_VALUE, Long.MAX_VALUE};
        for (long v : values) {
            assertThat(((Number) decoder.decode(encoder.encode(v))).longValue()).isEqualTo(v);
        }
    }

    @Test
    void testRoundTripFloat() {
        assertThat(decoder.decode(encoder.encode(3.14f))).isEqualTo(3.14f);
    }

    @Test
    void testRoundTripDouble() {
        assertThat(decoder.decode(encoder.encode(3.14159265358979))).isEqualTo(3.14159265358979);
    }

    @Test
    void testRoundTripString() {
        assertThat(decoder.decode(encoder.encode("hello"))).isEqualTo("hello");
        assertThat(decoder.decode(encoder.encode(""))).isEqualTo("");
        assertThat(decoder.decode(encoder.encode("a".repeat(300)))).isEqualTo("a".repeat(300));
    }

    @Test
    void testRoundTripBinary() {
        var data = new byte[]{1, 2, 3, 4, 5};
        assertThat((byte[]) decoder.decode(encoder.encode(data))).containsExactly(data);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripArray() {
        var list = java.util.Arrays.asList(1, "two", 3.0, true, null);
        var result = (List<Object>) decoder.decode(encoder.encode(list));
        assertThat(result).hasSize(5);
        assertThat(((Number) result.get(0)).intValue()).isEqualTo(1);
        assertThat(result.get(1)).isEqualTo("two");
        assertThat(result.get(4)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripMap() {
        var map = Map.of("key1", "value1", "key2", 42);
        var result = (Map<String, Object>) decoder.decode(encoder.encode(map));
        assertThat(result.get("key1")).isEqualTo("value1");
        assertThat(((Number) result.get("key2")).intValue()).isEqualTo(42);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripNestedStructure() {
        var nested = List.of(1, Map.of("inner", List.of(true, "deep")));
        var result = (List<Object>) decoder.decode(encoder.encode(nested));
        var innerMap = (Map<String, Object>) result.get(1);
        var innerList = (List<Object>) innerMap.get("inner");
        assertThat(innerList.get(0)).isEqualTo(true);
        assertThat(innerList.get(1)).isEqualTo("deep");
    }
}
