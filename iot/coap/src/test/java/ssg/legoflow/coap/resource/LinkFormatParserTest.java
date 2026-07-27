package ssg.legoflow.coap.resource;

import ssg.legoflow.coap.discovery.LinkFormatEntry;
import ssg.legoflow.coap.discovery.LinkFormatParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LinkFormatParser}.
 *
 * @since 1.0.0
 */
class LinkFormatParserTest {

    @Test
    void testParseSingleEntry() {
        var entries = LinkFormatParser.parse("</sensors/temp>;rt=\"temperature\";obs");

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().uri()).isEqualTo("/sensors/temp");
        assertThat(entries.getFirst().getResourceType()).isEqualTo("temperature");
        assertThat(entries.getFirst().isObservable()).isTrue();
    }

    @Test
    void testParseMultipleEntries() {
        var entries = LinkFormatParser.parse(
                "</sensors/temp>;rt=\"temperature\",</sensors/humidity>;rt=\"humidity\"");

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).uri()).isEqualTo("/sensors/temp");
        assertThat(entries.get(1).uri()).isEqualTo("/sensors/humidity");
    }

    @Test
    void testParseWithNumericAttribute() {
        var entries = LinkFormatParser.parse("</large>;ct=0;sz=5000");

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getContentFormat()).isEqualTo("0");
        assertThat(entries.getFirst().getMaxSizeEstimate()).isEqualTo("5000");
    }

    @Test
    void testSerialize() {
        var attrs = new LinkedHashMap<String, String>();
        attrs.put("rt", "temperature");
        attrs.put("obs", "");
        var entries = List.of(new LinkFormatEntry("/sensors/temp", attrs));

        var result = LinkFormatParser.serialize(entries);

        assertThat(result).contains("</sensors/temp>");
        assertThat(result).contains("rt=temperature");
        assertThat(result).contains("obs");
    }

    @Test
    void testParseEmpty() {
        var entries = LinkFormatParser.parse("");
        assertThat(entries).isEmpty();
    }

    @Test
    void testParseWithInterfaceDescription() {
        var entries = LinkFormatParser.parse("</light>;rt=\"light\";if=\"actuator\"");

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getResourceType()).isEqualTo("light");
        assertThat(entries.getFirst().getInterfaceDescription()).isEqualTo("actuator");
    }
}
