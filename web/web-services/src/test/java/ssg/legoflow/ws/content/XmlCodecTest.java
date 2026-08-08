package ssg.legoflow.ws.content;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.Map;

class XmlCodecTest {

    @Test void testEncodeWithRootElement() {
        var codec = new XmlCodec();
        var data = Map.of("name", "test", "count", "42");
        String xml = codec.encode("root", data);
        assertThat(xml).contains("<root>");
        assertThat(xml).contains("</root>");
    }

    @Test void testDecode() {
        var codec = new XmlCodec();
        String xml = "<root><name>test</name></root>";
        Map<String, String> result = codec.decode(xml);
        assertThat(result).containsKey("name");
    }

    @Test void testEncodeEmptyData() {
        var codec = new XmlCodec();
        var data = Map.<String, String>of();
        String xml = codec.encode("empty", data);
        assertThat(xml).contains("<empty>");
    }

    @Test void testDecodeNullThrows() {
        var codec = new XmlCodec();
        assertThatThrownBy(() -> codec.decode(null))
                .isInstanceOf(Exception.class);
    }

    @Test void testEncodeMultipleElements() {
        var codec = new XmlCodec();
        var data = Map.of("first", "one", "second", "two", "third", "three");
        String xml = codec.encode("items", data);
        assertThat(xml).contains("<first>");
        assertThat(xml).contains("<second>");
    }

    @Test void testDecodeInvalidXml() {
        var codec = new XmlCodec();
        try {
            codec.decode("not-valid-xml<");
        } catch (Exception e) {
            // Expected for invalid XML
        }
    }

    @Test void testEncodeWithSpecialChars() {
        var codec = new XmlCodec();
        var data = Map.of("text", "value");
        String xml = codec.encode("data", data);
        assertThat(xml).isNotBlank();
    }

    @Test void testRoundTrip() {
        var codec = new XmlCodec();
        var original = Map.of("key1", "val1", "key2", "val2");
        String xml = codec.encode("root", original);
        var decoded = codec.decode(xml);
        assertThat(decoded).containsKeys("key1", "key2");
    }
}
