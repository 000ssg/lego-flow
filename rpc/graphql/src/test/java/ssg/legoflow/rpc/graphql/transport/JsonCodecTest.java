package ssg.legoflow.rpc.graphql.transport;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for {@link JsonCodec}.
 */
class JsonCodecTest {

    @Test void testEncodeNull() { assertThat(JsonCodec.encode(null)).isEqualTo("null"); }

    @Test void testEncodeString() { assertThat(JsonCodec.encode("hello")).isEqualTo("\"hello\""); }

    @Test
    void testEncodeStringWithSpecialChars() {
        String input = "hello" + "\n" + "world\t\"quoted\"";
        String json = JsonCodec.encode(input);
        assertThat(json).contains("\\n");
        assertThat(json).contains("\\t");
        assertThat(json).contains("\\\"");
    }

    @Test void testEncodeEmptyString() { assertThat(JsonCodec.encode("")).isEqualTo("\"\""); }

    @Test void testEncodeInteger() { assertThat(JsonCodec.encode(42)).isEqualTo("42"); }

    @Test void testEncodeLong() { assertThat(JsonCodec.encode(Long.MAX_VALUE)).isEqualTo(String.valueOf(Long.MAX_VALUE)); }

    @Test void testEncodeDouble() { assertThat(JsonCodec.encode(3.14)).isEqualTo("3.14"); }

    @Test void testEncodeDoubleInteger() { assertThat(JsonCodec.encode(42.0)).isEqualTo("42"); }

    @Test void testEncodeDoubleInfinite() {
        assertThat(JsonCodec.encode(Double.POSITIVE_INFINITY)).isEqualTo("null");
        assertThat(JsonCodec.encode(Double.NEGATIVE_INFINITY)).isEqualTo("null");
    }

    @Test void testEncodeDoubleNaN() { assertThat(JsonCodec.encode(Double.NaN)).isEqualTo("null"); }

    @Test void testEncodeFloatInfinite() { assertThat(JsonCodec.encode(Float.POSITIVE_INFINITY)).isEqualTo("null"); }

    @Test void testEncodeFloatNaN() { assertThat(JsonCodec.encode(Float.NaN)).isEqualTo("null"); }

    @Test void testEncodeBoolean() {
        assertThat(JsonCodec.encode(true)).isEqualTo("true");
        assertThat(JsonCodec.encode(false)).isEqualTo("false");
    }

    @Test void testEncodeEmptyMap() {
        assertThat(JsonCodec.encode(new LinkedHashMap<>())).isEqualTo("{}");
    }

    @Test
    void testEncodeMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", "Alice");
        map.put("age", 30);
        assertThat(JsonCodec.encode(map)).isEqualTo("{\"name\":\"Alice\",\"age\":30}");
    }

    @Test
    void testEncodeNestedMap() {
        var inner = Map.of("city", "Helsinki");
        var outer = Map.of("user", inner);
        String json = JsonCodec.encode(outer);
        assertThat(json).isEqualTo("{\"user\":{\"city\":\"Helsinki\"}}");
    }

    @Test void testEncodeList() {
        assertThat(JsonCodec.encode(Arrays.asList("a", "b", "c"))).isEqualTo("[\"a\",\"b\",\"c\"]");
    }

    @Test void testEncodeEmptyList() {
        assertThat(JsonCodec.encode(new ArrayList<>())).isEqualTo("[]");
    }

    @Test void testEncodeArray() {
        Object[] arr = {"hello", 42, true};
        assertThat(JsonCodec.encode(arr)).isEqualTo("[\"hello\",42,true]");
    }

    @Test void testEncodeEmptyArray() {
        assertThat(JsonCodec.encode(new Object[0])).isEqualTo("[]");
    }

    // --- decode tests ---

    @Test void testDecodeNull() { assertThat(JsonCodec.decode("null")).isNull(); }

    @Test void testDecodeNullInput() { assertThat(JsonCodec.decode(null)).isNull(); }

    @Test void testDecodeBlankInput() { assertThat(JsonCodec.decode("  \t\n")).isNull(); }

    @Test void testDecodeString() {
        assertThat(JsonCodec.decode("\"hello\"")).isEqualTo("hello");
    }

    @Test
    void testDecodeEscapedString() {
        String input = "\"hello\\nworld\"";
        assertThat(JsonCodec.decode(input)).isEqualTo("hello\nworld");
    }

    @Test
    void testDecodeUnicode() {
        // \u0048 is 'H'
        String input = "\"\\u0048ello\"";
        assertThat(JsonCodec.decode(input)).isEqualTo("Hello");
    }

    @Test void testDecodeInteger() {
        var result = JsonCodec.decode("42");
        assertThat(result).isInstanceOf(Integer.class);
        assertThat((Integer)result).isEqualTo(42);
    }

    @Test void testDecodeLong() {
        var result = JsonCodec.decode(String.valueOf(Long.MAX_VALUE));
        assertThat(result).isInstanceOf(Long.class);
    }

    @Test void testDecodeFloat() {
        var result = JsonCodec.decode("3.14");
        assertThat(result).isInstanceOf(Double.class);
    }

    @Test void testDecodeScientificNotation() {
        var result = JsonCodec.decode("1e10");
        assertThat(result).isInstanceOf(Double.class);
    }

    @Test void testDecodeBooleanTrue() { assertThat(JsonCodec.decode("true")).isEqualTo(true); }

    @Test void testDecodeBooleanFalse() { assertThat(JsonCodec.decode("false")).isEqualTo(false); }

    @Test void testDecodeEmptyObject() {
        var result = JsonCodec.decode("{}");
        assertThat(result).isInstanceOf(Map.class);
        assertThat((Map<?,?>)result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDecodeObject() {
        var result = JsonCodec.decode("{\"name\":\"Alice\",\"age\":30}");
        assertThat(result).isInstanceOf(Map.class);
        var map = (Map<String, Object>)result;
        assertThat(map.get("name")).isEqualTo("Alice");
        assertThat(map.get("age")).isEqualTo(30);
    }

    @Test void testDecodeEmptyArray() {
        var result = JsonCodec.decode("[]");
        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>)result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDecodeArray() {
        var result = JsonCodec.decode("[1, \"two\", true]");
        assertThat(result).isInstanceOf(List.class);
        var list = (List<Object>)result;
        assertThat(list.get(0)).isEqualTo(1);
        assertThat(list.get(1)).isEqualTo("two");
        assertThat(list.get(2)).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDecodeNestedObject() {
        String json = "{\"user\":{\"name\":\"Alice\",\"scores\":[90,85]}}";
        var result = JsonCodec.decode(json);
        assertThat(result).isInstanceOf(Map.class);
        var map = (Map<String, Object>)result;
        var user = (Map<String, Object>)map.get("user");
        assertThat(user.get("name")).isEqualTo("Alice");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDecodeObjectWithSpaces() {
        var result = JsonCodec.decode("{  \"key\"  :  \"value\"  }");
        assertThat(result).isInstanceOf(Map.class);
        var map = (Map<String, Object>)result;
        assertThat(map.get("key")).isEqualTo("value");
    }

    @Test void testDecodeNegativeNumber() {
        assertThat(JsonCodec.decode("-42")).isEqualTo(-42);
    }

    // --- decodeObject tests ---

    @Test void testDecodeObjectNullInput() {
        assertThat(JsonCodec.decodeObject(null)).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDecodeObjectValidJson() {
        var result = JsonCodec.decodeObject("{\"a\":1}");
        assertThat(result).isNotNull();
        assertThat(result.get("a")).isEqualTo(1);
    }

    @Test void testDecodeObjectNonObjectJson() {
        assertThat(JsonCodec.decodeObject("[1,2]")).isNull();
        assertThat(JsonCodec.decodeObject("\"string\"")).isNull();
    }

    // --- round trip tests ---

    @SuppressWarnings("unchecked")
    @Test
    void testRoundTripMap() {
        var original = new LinkedHashMap<String, Object>();
        original.put("name", "Alice");
        original.put("age", 30);
        original.put("active", true);
        String json = JsonCodec.encode(original);
        var decoded = JsonCodec.decode(json);
        assertThat(decoded).isInstanceOf(Map.class);
        var map = (Map<String, Object>)decoded;
        assertThat(map.get("name")).isEqualTo("Alice");
    }

    @Test void testEncodeFallsBackToStringForUnknown() {
        var json = JsonCodec.encode(new Object());
        assertThat(json).startsWith("\"");
        assertThat(json).endsWith("\"");
    }
}
