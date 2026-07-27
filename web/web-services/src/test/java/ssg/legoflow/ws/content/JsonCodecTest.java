package ssg.legoflow.ws.content;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class JsonCodecTest {

    private final JsonCodec codec = new JsonCodec();

    @Test
    void testEncodeSimple() {
        var data = new LinkedHashMap<String, String>();
        data.put("name", "Alice");
        data.put("age", "30");
        var json = codec.encode(data);
        assertThat(json).contains("\"name\":\"Alice\"");
        assertThat(json).contains("\"age\":\"30\"");
    }

    @Test
    void testDecodeSimple() {
        var json = "{\"name\":\"Alice\",\"age\":\"30\"}";
        var result = codec.decode(json);
        assertThat(result).containsEntry("name", "Alice").containsEntry("age", "30");
    }

    @Test
    void testRoundTrip() {
        var data = new LinkedHashMap<String, String>();
        data.put("key", "value");
        var json = codec.encode(data);
        var decoded = codec.decode(json);
        assertThat(decoded).isEqualTo(data);
    }

    @Test
    void testEncodeList() {
        var list = List.of(Map.of("id", "1"), Map.of("id", "2"));
        List<Map<String, String>> items = list.stream().map(m -> {
            var lhm = new LinkedHashMap<String, String>();
            lhm.putAll(m);
            return (Map<String, String>) lhm;
        }).toList();
        var json = codec.encodeList(items);
        assertThat(json).startsWith("[").endsWith("]");
        assertThat(json).contains("\"id\":\"1\"").contains("\"id\":\"2\"");
    }
}
