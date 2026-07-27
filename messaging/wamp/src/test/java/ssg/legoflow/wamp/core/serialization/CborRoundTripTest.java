package ssg.legoflow.wamp.core.serialization;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CBOR round-trip tests: encode then decode, verifying fidelity.
 */
class CborRoundTripTest {

    private final CborEncoder encoder = new CborEncoder();
    private final CborDecoder decoder = new CborDecoder();

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
    void testRoundTripPositiveIntegers() {
        int[] values = {0, 1, 23, 24, 255, 256, 65535, 65536, Integer.MAX_VALUE};
        for (int v : values) {
            assertThat(((Number) decoder.decode(encoder.encode(v))).intValue()).isEqualTo(v);
        }
    }

    @Test
    void testRoundTripNegativeIntegers() {
        int[] values = {-1, -10, -24, -25, -256, -32768};
        for (int v : values) {
            assertThat(((Number) decoder.decode(encoder.encode(v))).intValue()).isEqualTo(v);
        }
    }

    @Test
    void testRoundTripLong() {
        long[] values = {0x1_0000_0000L, Long.MAX_VALUE, -1_000_000_000L};
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
        assertThat(decoder.decode(encoder.encode(2.71828))).isEqualTo(2.71828);
    }

    @Test
    void testRoundTripString() {
        assertThat(decoder.decode(encoder.encode("hello"))).isEqualTo("hello");
        assertThat(decoder.decode(encoder.encode(""))).isEqualTo("");
        assertThat(decoder.decode(encoder.encode("a".repeat(500)))).isEqualTo("a".repeat(500));
    }

    @Test
    void testRoundTripBinary() {
        var data = new byte[]{10, 20, 30};
        assertThat((byte[]) decoder.decode(encoder.encode(data))).containsExactly(data);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripArray() {
        var list = List.of(1, "two", true);
        var result = (List<Object>) decoder.decode(encoder.encode(list));
        assertThat(result).hasSize(3);
        assertThat(((Number) result.get(0)).intValue()).isEqualTo(1);
        assertThat(result.get(1)).isEqualTo("two");
        assertThat(result.get(2)).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripMap() {
        var map = Map.of("x", 10, "y", 20);
        var result = (Map<String, Object>) decoder.decode(encoder.encode(map));
        assertThat(((Number) result.get("x")).intValue()).isEqualTo(10);
        assertThat(((Number) result.get("y")).intValue()).isEqualTo(20);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripNested() {
        var nested = List.of(Map.of("arr", List.of(1, 2, 3)), "end");
        var result = (List<Object>) decoder.decode(encoder.encode(nested));
        var innerMap = (Map<String, Object>) result.get(0);
        var innerList = (List<Object>) innerMap.get("arr");
        assertThat(innerList).hasSize(3);
        assertThat(result.get(1)).isEqualTo("end");
    }
}
