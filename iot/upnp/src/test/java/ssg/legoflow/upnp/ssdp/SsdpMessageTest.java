package ssg.legoflow.upnp.ssdp;

import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import static org.assertj.core.api.Assertions.*;
class SsdpMessageTest {

    @Test
    void shouldParseAliveNotification() {
        // Given: a raw SSDP alive notification
        var raw = "NOTIFY * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "LOCATION: http://192.168.1.100:8080/description.xml\r\n" +
                "NT: upnp:rootdevice\r\n" +
                "NTS: ssdp:alive\r\n" +
                "SERVER: Linux/3.0 UPnP/1.0 MediaServer/1.0\r\n" +
                "USN: uuid:12345678-1234-1234-1234-123456789012::upnp:rootdevice\r\n" +
                "\r\n";
        var source = new InetSocketAddress("192.168.1.100", 1900);

        // When: parsing the message
        var message = SsdpMessage.parse(raw, source);

        // Then: the type and headers are correct
        assertThat(message.type()).isEqualTo(SsdpMessageType.NOTIFY_ALIVE);
        assertThat(message.location()).hasValue("http://192.168.1.100:8080/description.xml");
        assertThat(message.notificationType()).hasValue("upnp:rootdevice");
        assertThat(message.usn()).hasValue("uuid:12345678-1234-1234-1234-123456789012::upnp:rootdevice");
        assertThat(message.server()).hasValue("Linux/3.0 UPnP/1.0 MediaServer/1.0");
        assertThat(message.maxAge()).isEqualTo(1800);
        assertThat(message.source()).isEqualTo(source);
    }

    @Test
    void shouldParseByebyeNotification() {
        // Given: a raw SSDP byebye notification
        var raw = "NOTIFY * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "NT: upnp:rootdevice\r\n" +
                "NTS: ssdp:byebye\r\n" +
                "USN: uuid:device-1::upnp:rootdevice\r\n" +
                "\r\n";

        // When: parsing the message
        var message = SsdpMessage.parse(raw, null);

        // Then: the type is NOTIFY_BYEBYE
        assertThat(message.type()).isEqualTo(SsdpMessageType.NOTIFY_BYEBYE);
        assertThat(message.usn()).hasValue("uuid:device-1::upnp:rootdevice");
    }

    @Test
    void shouldParseMSearchRequest() {
        // Given: a raw M-SEARCH request
        var raw = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: ssdp:all\r\n" +
                "\r\n";

        // When: parsing the message
        var message = SsdpMessage.parse(raw, null);

        // Then: the type is M_SEARCH
        assertThat(message.type()).isEqualTo(SsdpMessageType.M_SEARCH);
        assertThat(message.searchTarget()).hasValue("ssdp:all");
        assertThat(message.header("MAN")).hasValue("\"ssdp:discover\"");
        assertThat(message.header("MX")).hasValue("3");
    }

    @Test
    void shouldParseMSearchResponse() {
        // Given: a raw M-SEARCH response
        var raw = "HTTP/1.1 200 OK\r\n" +
                "CACHE-CONTROL: max-age=900\r\n" +
                "EXT:\r\n" +
                "LOCATION: http://192.168.1.50:9000/desc.xml\r\n" +
                "SERVER: Test/1.0 UPnP/1.0 Renderer/1.0\r\n" +
                "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
                "USN: uuid:renderer-1::urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
                "\r\n";

        // When: parsing the message
        var message = SsdpMessage.parse(raw, null);

        // Then: the type is M_SEARCH_RESPONSE
        assertThat(message.type()).isEqualTo(SsdpMessageType.M_SEARCH_RESPONSE);
        assertThat(message.location()).hasValue("http://192.168.1.50:9000/desc.xml");
        assertThat(message.searchTarget()).hasValue("urn:schemas-upnp-org:device:MediaRenderer:1");
        assertThat(message.maxAge()).isEqualTo(900);
    }

    @Test
    void shouldSerializeAndParseAliveRoundtrip() {
        // Given: an alive message created via factory method
        var original = SsdpMessage.alive(
                "http://192.168.1.10:8080/desc.xml",
                "upnp:rootdevice",
                "uuid:abc-123::upnp:rootdevice",
                "Lego/1.0 UPnP/1.0",
                3600
        );

        // When: serializing and re-parsing
        var serialized = original.serialize();
        var parsed = SsdpMessage.parse(serialized, null);

        // Then: the parsed message matches the original
        assertThat(parsed.type()).isEqualTo(SsdpMessageType.NOTIFY_ALIVE);
        assertThat(parsed.location()).isEqualTo(original.location());
        assertThat(parsed.usn()).isEqualTo(original.usn());
        assertThat(parsed.notificationType()).isEqualTo(original.notificationType());
        assertThat(parsed.maxAge()).isEqualTo(3600);
    }

    @Test
    void shouldSerializeAndParseSearchRoundtrip() {
        // Given: a search message
        var original = SsdpMessage.search("upnp:rootdevice", 5);

        // When: serializing and re-parsing
        var serialized = original.serialize();
        var parsed = SsdpMessage.parse(serialized, null);

        // Then: the parsed message matches
        assertThat(parsed.type()).isEqualTo(SsdpMessageType.M_SEARCH);
        assertThat(parsed.searchTarget()).hasValue("upnp:rootdevice");
        assertThat(parsed.header("MX")).hasValue("5");
    }

    @Test
    void shouldCreateAliveMessageWithCorrectHeaders() {
        // Given/When: creating an alive message
        var message = SsdpMessage.alive(
                "http://host/desc.xml", "urn:schemas-upnp-org:device:MediaServer:1",
                "uuid:ms-1::urn:schemas-upnp-org:device:MediaServer:1",
                "Server/1.0", 1800
        );

        // Then: all required headers are present
        assertThat(message.type()).isEqualTo(SsdpMessageType.NOTIFY_ALIVE);
        assertThat(message.header("HOST")).hasValue(SsdpConstants.MULTICAST_HOST);
        assertThat(message.header("NTS")).hasValue(SsdpConstants.NTS_ALIVE);
        assertThat(message.header("CACHE-CONTROL")).hasValue("max-age=1800");
    }

    @Test
    void shouldCreateByebyeMessage() {
        // Given/When: creating a byebye message
        var message = SsdpMessage.byebye("upnp:rootdevice", "uuid:dev-1::upnp:rootdevice");

        // Then: correct type and headers
        assertThat(message.type()).isEqualTo(SsdpMessageType.NOTIFY_BYEBYE);
        assertThat(message.header("NTS")).hasValue(SsdpConstants.NTS_BYEBYE);
        assertThat(message.usn()).hasValue("uuid:dev-1::upnp:rootdevice");
    }

    @Test
    void shouldCreateSearchResponseMessage() {
        // Given/When: creating a search response
        var message = SsdpMessage.searchResponse(
                "http://host/desc.xml", "upnp:rootdevice",
                "uuid:dev-1::upnp:rootdevice", "Server/1.0", 900
        );

        // Then: correct type and headers
        assertThat(message.type()).isEqualTo(SsdpMessageType.M_SEARCH_RESPONSE);
        assertThat(message.header("EXT")).hasValue("");
        assertThat(message.location()).hasValue("http://host/desc.xml");
    }

    @Test
    void shouldAccessHeadersCaseInsensitively() {
        // Given: a message with mixed-case headers
        var message = SsdpMessage.alive(
                "http://host/desc.xml", "upnp:rootdevice",
                "uuid:dev-1::upnp:rootdevice", "Server/1.0", 1800
        );

        // When/Then: headers can be accessed with any case
        assertThat(message.header("location")).hasValue("http://host/desc.xml");
        assertThat(message.header("LOCATION")).hasValue("http://host/desc.xml");
        assertThat(message.header("Location")).hasValue("http://host/desc.xml");
    }

    @Test
    void shouldReturnDefaultMaxAgeWhenCacheControlMissing() {
        // Given: a byebye message without CACHE-CONTROL
        var message = SsdpMessage.byebye("upnp:rootdevice", "uuid:dev-1::upnp:rootdevice");

        // When/Then: max age returns the default
        assertThat(message.maxAge()).isEqualTo(SsdpConstants.DEFAULT_MAX_AGE);
    }

    @Test
    void shouldRejectSearchWithNonPositiveMx() {
        // Given/When/Then: creating a search with MX=0 throws
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SsdpMessage.search("ssdp:all", 0))
                .withMessageContaining("MX must be positive");
    }
}
