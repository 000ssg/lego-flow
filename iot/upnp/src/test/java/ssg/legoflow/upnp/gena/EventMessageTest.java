package ssg.legoflow.upnp.gena;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class EventMessageTest {

    @Test
    void shouldParseEventXml() {
        // Given: a GENA event notification XML
        var xml = """
                <?xml version="1.0"?>
                <e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">
                <e:property><SystemUpdateID>42</SystemUpdateID></e:property>
                <e:property><ContainerUpdateIDs>1,5</ContainerUpdateIDs></e:property>
                </e:propertyset>
                """;

        // When: parsing
        var event = EventMessage.parseXml("uuid:sub-123", 5, xml);

        // Then: fields are correct
        assertThat(event.sid()).isEqualTo("uuid:sub-123");
        assertThat(event.seq()).isEqualTo(5);
        assertThat(event.changedVariables()).containsEntry("SystemUpdateID", "42");
        assertThat(event.changedVariables()).containsEntry("ContainerUpdateIDs", "1,5");
    }

    @Test
    void shouldSerializeToXml() {
        // Given: an event message
        var vars = new LinkedHashMap<String, String>();
        vars.put("Volume", "75");
        vars.put("Mute", "0");
        var event = new EventMessage("uuid:sub-456", 10, vars);

        // When: serializing
        var xml = event.toXml();

        // Then: XML contains property elements
        assertThat(xml).contains("<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">");
        assertThat(xml).contains("<e:property><Volume>75</Volume></e:property>");
        assertThat(xml).contains("<e:property><Mute>0</Mute></e:property>");
        assertThat(xml).contains("</e:propertyset>");
    }

    @Test
    void shouldIdentifyInitialEvent() {
        // Given: an event with seq=0
        var event = new EventMessage("uuid:sub-1", 0, Map.of("Status", "OK"));

        // When/Then: it is an initial event
        assertThat(event.isInitialEvent()).isTrue();
    }

    @Test
    void shouldIdentifyNonInitialEvent() {
        // Given: an event with seq > 0
        var event = new EventMessage("uuid:sub-1", 7, Map.of("Status", "Changed"));

        // When/Then: it is not an initial event
        assertThat(event.isInitialEvent()).isFalse();
    }

    @Test
    void shouldRejectNegativeSequenceNumber() {
        // Given/When/Then: creating an event with negative seq throws
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EventMessage("uuid:sub-1", -1, Map.of()))
                .withMessageContaining("seq must not be negative");
    }
}
