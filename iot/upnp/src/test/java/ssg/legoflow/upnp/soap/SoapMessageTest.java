package ssg.legoflow.upnp.soap;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class SoapMessageTest {

    private static final String SERVICE_TYPE = "urn:schemas-upnp-org:service:ContentDirectory:1";

    @Test
    void shouldSerializeRequestEnvelope() {
        // Given: a SOAP request message
        var args = new LinkedHashMap<String, String>();
        args.put("ObjectID", "0");
        args.put("BrowseFlag", "BrowseDirectChildren");
        var message = SoapMessage.request(SERVICE_TYPE, "Browse", args);

        // When: serializing
        var xml = message.serializeRequest();

        // Then: XML contains SOAP envelope structure
        assertThat(xml).contains("s:Envelope");
        assertThat(xml).contains(SoapConstants.SOAP_ENVELOPE_NS);
        assertThat(xml).contains("<u:Browse xmlns:u=\"" + SERVICE_TYPE + "\">");
        assertThat(xml).contains("<ObjectID>0</ObjectID>");
        assertThat(xml).contains("<BrowseFlag>BrowseDirectChildren</BrowseFlag>");
        assertThat(xml).contains("</u:Browse>");
    }

    @Test
    void shouldParseResponseEnvelope() {
        // Given: a SOAP response XML
        var xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                 s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
                <Result>some-didl-xml</Result>
                <NumberReturned>10</NumberReturned>
                <TotalMatches>42</TotalMatches>
                </u:BrowseResponse>
                </s:Body>
                </s:Envelope>
                """;

        // When: parsing the response
        var response = SoapMessage.parseResponse(xml);

        // Then: response is successful with output arguments
        assertThat(response.success()).isTrue();
        assertThat(response.fault()).isNull();
        assertThat(response.outputArguments()).containsEntry("Result", "some-didl-xml");
        assertThat(response.outputArguments()).containsEntry("NumberReturned", "10");
        assertThat(response.outputArguments()).containsEntry("TotalMatches", "42");
    }

    @Test
    void shouldParseFaultResponse() {
        // Given: a SOAP fault XML
        var xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                 s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                <s:Fault>
                <faultcode>s:Client</faultcode>
                <faultstring>UPnPError</faultstring>
                <detail>
                <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
                <errorCode>401</errorCode>
                <errorDescription>Invalid Action</errorDescription>
                </UPnPError>
                </detail>
                </s:Fault>
                </s:Body>
                </s:Envelope>
                """;

        // When: parsing the fault
        var response = SoapMessage.parseResponse(xml);

        // Then: response is a failure with fault details
        assertThat(response.success()).isFalse();
        assertThat(response.fault()).isNotNull();
        assertThat(response.fault().errorCode()).isEqualTo(401);
        assertThat(response.fault().errorDescription()).isEqualTo("Invalid Action");
    }

    @Test
    void shouldRoundtripRequestSerialization() {
        // Given: a SOAP request
        var args = Map.of("InstanceID", "0", "Channel", "Master");
        var original = SoapMessage.request(
                "urn:schemas-upnp-org:service:RenderingControl:1", "GetVolume", args
        );

        // When: serializing then parsing
        var xml = original.serializeRequest();
        var parsed = SoapMessage.parseRequest(xml);

        // Then: fields match
        assertThat(parsed.serviceType()).isEqualTo(original.serviceType());
        assertThat(parsed.actionName()).isEqualTo(original.actionName());
        assertThat(parsed.arguments()).containsAllEntriesOf(original.arguments());
    }

    @Test
    void shouldSerializeAndParseResponseRoundtrip() {
        // Given: a SOAP response
        var outputArgs = new LinkedHashMap<String, String>();
        outputArgs.put("CurrentVolume", "75");
        var original = SoapMessage.response(
                "urn:schemas-upnp-org:service:RenderingControl:1", "GetVolume", outputArgs
        );

        // When: serializing then parsing
        var xml = original.serializeResponse();
        var response = SoapMessage.parseResponse(xml);

        // Then: output arguments match
        assertThat(response.success()).isTrue();
        assertThat(response.outputArguments()).containsEntry("CurrentVolume", "75");
    }

    @Test
    void shouldSerializeFaultEnvelope() {
        // Given: a SOAP fault
        var fault = new SoapFault(SoapConstants.ERROR_INVALID_ARGS, "Invalid Args");

        // When: serializing
        var xml = SoapMessage.serializeFault(fault);

        // Then: XML contains fault structure
        assertThat(xml).contains("<s:Fault>");
        assertThat(xml).contains("<errorCode>402</errorCode>");
        assertThat(xml).contains("<errorDescription>Invalid Args</errorDescription>");
        assertThat(xml).contains("UPnPError");
    }

    @Test
    void shouldParseRequestWithCorrectServiceType() {
        // Given: a SOAP request XML
        var xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                 s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                <InstanceID>0</InstanceID>
                <Speed>1</Speed>
                </u:Play>
                </s:Body>
                </s:Envelope>
                """;

        // When: parsing
        var parsed = SoapMessage.parseRequest(xml);

        // Then: service type and action name are correct
        assertThat(parsed.serviceType()).isEqualTo("urn:schemas-upnp-org:service:AVTransport:1");
        assertThat(parsed.actionName()).isEqualTo("Play");
        assertThat(parsed.arguments()).containsEntry("InstanceID", "0");
        assertThat(parsed.arguments()).containsEntry("Speed", "1");
    }

    @Test
    void shouldHandleEmptyArguments() {
        // Given: a request with no arguments
        var message = SoapMessage.request(SERVICE_TYPE, "GetSystemUpdateID", Map.of());

        // When: serializing
        var xml = message.serializeRequest();

        // Then: action element is present but empty
        assertThat(xml).contains("<u:GetSystemUpdateID xmlns:u=\"" + SERVICE_TYPE + "\">");
        assertThat(xml).contains("</u:GetSystemUpdateID>");
    }

    @Test
    void shouldParseResponseWithXmlEscapedDidlLiteInResult() {
        // Given: a real-world SOAP response with XML-escaped DIDL-Lite in Result argument
        // This is the format used by MiniDLNA, Plex, Jellyfin, and most real servers
        var xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                 s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
                <Result>&lt;DIDL-Lite xmlns:dc=&quot;http://purl.org/dc/elements/1.1/&quot; xmlns:upnp=&quot;urn:schemas-upnp-org:metadata-1-0/upnp/&quot; xmlns=&quot;urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/&quot;&gt;&lt;container id=&quot;1&quot; parentID=&quot;0&quot; restricted=&quot;1&quot; childCount=&quot;3&quot;&gt;&lt;dc:title&gt;Music&lt;/dc:title&gt;&lt;upnp:class&gt;object.container.storageFolder&lt;/upnp:class&gt;&lt;/container&gt;&lt;/DIDL-Lite&gt;</Result>
                <NumberReturned>1</NumberReturned>
                <TotalMatches>1</TotalMatches>
                <UpdateID>1</UpdateID>
                </u:BrowseResponse>
                </s:Body>
                </s:Envelope>
                """;

        // When: parsing the response
        var response = SoapMessage.parseResponse(xml);

        // Then: Result contains unescaped DIDL-Lite XML
        assertThat(response.success()).isTrue();
        String result = response.outputArguments().get("Result");
        assertThat(result).isNotNull();
        assertThat(result).contains("<DIDL-Lite");
        assertThat(result).contains("<container id=\"1\"");
        assertThat(result).contains("<dc:title>Music</dc:title>");
        assertThat(result).contains("object.container.storageFolder");
        assertThat(response.outputArguments()).containsEntry("NumberReturned", "1");
        assertThat(response.outputArguments()).containsEntry("TotalMatches", "1");
    }

    @Test
    void shouldParseResponseWithCdataWrappedResult() {
        // Given: a SOAP response with CDATA-wrapped DIDL-Lite (used by some servers)
        var xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                 s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
                <Result><![CDATA[<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item id="42" parentID="1"><dc:title>Song.mp3</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class><res protocolInfo="http-get:*:audio/mpeg:*">http://192.168.1.100:8200/MediaItems/42.mp3</res></item></DIDL-Lite>]]></Result>
                <NumberReturned>1</NumberReturned>
                <TotalMatches>1</TotalMatches>
                <UpdateID>5</UpdateID>
                </u:BrowseResponse>
                </s:Body>
                </s:Envelope>
                """;

        // When
        var response = SoapMessage.parseResponse(xml);

        // Then: Result contains the DIDL-Lite XML from CDATA
        assertThat(response.success()).isTrue();
        String result = response.outputArguments().get("Result");
        assertThat(result).contains("<DIDL-Lite");
        assertThat(result).contains("<item id=\"42\"");
        assertThat(result).contains("Song.mp3");
    }

    @Test
    void shouldParseResponseWithDifferentNamespacePrefix() {
        // Given: a SOAP response using SOAP-ENV prefix instead of s: (common in older devices)
        var xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
                 SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <SOAP-ENV:Body>
                <m:BrowseResponse xmlns:m="urn:schemas-upnp-org:service:ContentDirectory:1">
                <Result>test-content</Result>
                <NumberReturned>0</NumberReturned>
                <TotalMatches>0</TotalMatches>
                <UpdateID>1</UpdateID>
                </m:BrowseResponse>
                </SOAP-ENV:Body>
                </SOAP-ENV:Envelope>
                """;

        // When
        var response = SoapMessage.parseResponse(xml);

        // Then: parsed correctly despite different namespace prefix
        assertThat(response.success()).isTrue();
        assertThat(response.outputArguments()).containsEntry("Result", "test-content");
        assertThat(response.outputArguments()).containsEntry("NumberReturned", "0");
    }

    @Test
    void shouldParseFaultWithDifferentNamespacePrefix() {
        // Given: a SOAP fault with SOAP-ENV prefix
        var xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                <SOAP-ENV:Body>
                <SOAP-ENV:Fault>
                <faultcode>SOAP-ENV:Client</faultcode>
                <faultstring>UPnPError</faultstring>
                <detail>
                <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
                <errorCode>701</errorCode>
                <errorDescription>No such object</errorDescription>
                </UPnPError>
                </detail>
                </SOAP-ENV:Fault>
                </SOAP-ENV:Body>
                </SOAP-ENV:Envelope>
                """;

        // When
        var response = SoapMessage.parseResponse(xml);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.fault()).isNotNull();
        assertThat(response.fault().errorCode()).isEqualTo(701);
        assertThat(response.fault().errorDescription()).isEqualTo("No such object");
    }

    @Test
    void shouldParseRequestWithDifferentNamespacePrefix() {
        // Given: a SOAP request using non-standard prefix
        var xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
                 SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <SOAP-ENV:Body>
                <ns0:Browse xmlns:ns0="urn:schemas-upnp-org:service:ContentDirectory:1">
                <ObjectID>0</ObjectID>
                <BrowseFlag>BrowseDirectChildren</BrowseFlag>
                </ns0:Browse>
                </SOAP-ENV:Body>
                </SOAP-ENV:Envelope>
                """;

        // When
        var parsed = SoapMessage.parseRequest(xml);

        // Then
        assertThat(parsed.actionName()).isEqualTo("Browse");
        assertThat(parsed.serviceType()).isEqualTo("urn:schemas-upnp-org:service:ContentDirectory:1");
        assertThat(parsed.arguments()).containsEntry("ObjectID", "0");
    }
}
