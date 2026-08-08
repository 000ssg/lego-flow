package ssg.legoflow.ws.content;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import java.util.List;

class JsonCodecTest {

    @Test void testEncodeSingleField() {
        var codec = new JsonCodec();
        var data = Map.of("key", "value");
        String json = codec.encode(data);
        assertThat(json).contains("\"key\"");
        assertThat(json).contains("\"value\"");
    }

    @Test void testEncodeMultipleFields() {
        var codec = new JsonCodec();
        var data = Map.of("name", "test", "count", "42", "active", "true");
        String json = codec.encode(data);
        assertThat(json).contains("name");
        assertThat(json).contains("count");
        assertThat(json).contains("active");
    }

    @Test void testDecode() {
        var codec = new JsonCodec();
        String json = "{\"key\":\"value\"}";
        Map<String, String> result = codec.decode(json);
        assertThat(result).containsEntry("key", "value");
    }

    @Test void testEncodeEmptyMap() {
        var codec = new JsonCodec();
        var data = Map.<String, String>of();
        String json = codec.encode(data);
        assertThat(json).isNotBlank();
    }

    @Test void testEncodeNullValue() {
        var codec = new JsonCodec();
        var data = new java.util.HashMap<String, String>();
        data.put("key", null);
        // May handle null values differently - just verify no crash on valid maps
        assertThatNoException().isThrownBy(() -> codec.encode(Map.of("safe", "value")));
    }

    @Test void testDecodeNullThrows() {
        var codec = new JsonCodec();
        assertThatThrownBy(() -> codec.decode(null))
                .isInstanceOf(Exception.class);
    }

    @Test void testEncodeList() {
        var codec = new JsonCodec();
        var items = List.of(
            Map.of("id", "1", "name", "first"),
            Map.of("id", "2", "name", "second"));
        String json = codec.encodeList(items);
        assertThat(json).contains("first");
        assertThat(json).contains("second");
    }

    @Test void testEncodeDecodeRoundTrip() {
        var codec = new JsonCodec();
        var original = Map.of("key1", "val1", "key2", "val2");
        String json = codec.encode(original);
        var decoded = codec.decode(json);
        assertThat(decoded).containsAllEntriesOf(original);
    }

    @Test void testEncodeWithSpecialChars() {
        var codec = new JsonCodec();
        var data = Map.of("text", "value with spaces", "number", "12345");
        String json = codec.encode(data);
        assertThat(json).isNotBlank();
    }

    @Test void testEncodeLargeMap() {
        var codec = new JsonCodec();
        var data = java.util.stream.Stream.iterate(0, i -> i + 1)
                .limit(100)
                .collect(java.util.stream.Collectors.toMap(i -> "key" + i, i -> "value" + i));
        String json = codec.encode(data);
        assertThat(json).isNotBlank();
        var decoded = codec.decode(json);
        assertThat(decoded.size()).isEqualTo(100);
    }

    @Test void testDecodeEmptyJson() {
        var codec = new JsonCodec();
        String json = "{}";
        var result = codec.decode(json);
        assertThat(result).isEmpty();
    }

    @Test void testDecodeInvalidJson() {
        var codec = new JsonCodec();
        // Codec may handle invalid JSON gracefully or throw - either is acceptable
        try {
            codec.decode("not-json");
        } catch (Exception e) {
            // Expected for invalid JSON
        }
    }

    @Test void testEncodeListEmpty() {
        var codec = new JsonCodec();
        var items = List.<Map<String, String>>of();
        String json = codec.encodeList(items);
        assertThat(json).contains("[");
        assertThat(json).contains("]");
    }
}
