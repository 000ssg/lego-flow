package ssg.legoflow.upnp.device;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class DeviceDescriptionTest {

    private static final String DEVICE_XML = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
            <specVersion><major>1</major><minor>0</minor></specVersion>
            <device>
            <deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>
            <friendlyName>Test Media Server</friendlyName>
            <manufacturer>Lego Flow</manufacturer>
            <modelName>LegoServer</modelName>
            <modelNumber>1.0</modelNumber>
            <UDN>uuid:12345678-1234-1234-1234-123456789012</UDN>
            <serialNumber>SN-001</serialNumber>
            <presentationURL>http://192.168.1.100:8080/</presentationURL>
            <iconList>
            <icon>
            <mimetype>image/png</mimetype>
            <width>48</width>
            <height>48</height>
            <depth>24</depth>
            <url>/icon48.png</url>
            </icon>
            <icon>
            <mimetype>image/png</mimetype>
            <width>120</width>
            <height>120</height>
            <depth>24</depth>
            <url>/icon120.png</url>
            </icon>
            </iconList>
            <serviceList>
            <service>
            <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
            <serviceId>urn:upnp-org:serviceId:ContentDirectory</serviceId>
            <SCPDURL>/cds.xml</SCPDURL>
            <controlURL>/cds/control</controlURL>
            <eventSubURL>/cds/event</eventSubURL>
            </service>
            <service>
            <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
            <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
            <SCPDURL>/cm.xml</SCPDURL>
            <controlURL>/cm/control</controlURL>
            <eventSubURL>/cm/event</eventSubURL>
            </service>
            </serviceList>
            </device>
            </root>
            """;

    @Test
    void shouldParseDeviceDescriptionXml() {
        // Given: a UPnP device description XML

        // When: parsing it
        var device = DeviceDescription.parseXml(DEVICE_XML);

        // Then: all fields are correctly parsed
        assertThat(device.deviceType()).isEqualTo("urn:schemas-upnp-org:device:MediaServer:1");
        assertThat(device.friendlyName()).isEqualTo("Test Media Server");
        assertThat(device.manufacturer()).isEqualTo("Lego Flow");
        assertThat(device.modelName()).isEqualTo("LegoServer");
        assertThat(device.modelNumber()).isEqualTo("1.0");
        assertThat(device.udn()).isEqualTo("uuid:12345678-1234-1234-1234-123456789012");
        assertThat(device.serialNumber()).isEqualTo("SN-001");
        assertThat(device.presentationUrl()).isEqualTo("http://192.168.1.100:8080/");
    }

    @Test
    void shouldParseServicesFromXml() {
        // Given/When: parsing the device XML
        var device = DeviceDescription.parseXml(DEVICE_XML);

        // Then: services are correctly parsed
        assertThat(device.services()).hasSize(2);
        var cds = device.services().get(0);
        assertThat(cds.serviceType()).isEqualTo("urn:schemas-upnp-org:service:ContentDirectory:1");
        assertThat(cds.serviceId()).isEqualTo("urn:upnp-org:serviceId:ContentDirectory");
        assertThat(cds.scpdUrl()).isEqualTo("/cds.xml");
        assertThat(cds.controlUrl()).isEqualTo("/cds/control");
        assertThat(cds.eventSubUrl()).isEqualTo("/cds/event");
    }

    @Test
    void shouldParseIconsFromXml() {
        // Given/When: parsing the device XML
        var device = DeviceDescription.parseXml(DEVICE_XML);

        // Then: icons are correctly parsed
        assertThat(device.icons()).hasSize(2);
        var icon = device.icons().get(0);
        assertThat(icon.mimetype()).isEqualTo("image/png");
        assertThat(icon.width()).isEqualTo(48);
        assertThat(icon.height()).isEqualTo(48);
        assertThat(icon.depth()).isEqualTo(24);
        assertThat(icon.url()).isEqualTo("/icon48.png");
    }

    @Test
    void shouldSerializeToXml() {
        // Given: a device description
        var device = new DeviceDescription(
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "Test Renderer", "TestCorp", "RenderModel",
                "2.0", "uuid:renderer-uuid", null, null,
                List.of(new ServiceDescription(
                        "urn:schemas-upnp-org:service:AVTransport:1",
                        "urn:upnp-org:serviceId:AVTransport",
                        "/avt.xml", "/avt/control", "/avt/event"
                )),
                List.of(), List.of()
        );

        // When: serializing to XML
        var xml = device.toXml();

        // Then: XML contains expected elements
        assertThat(xml).contains("<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>");
        assertThat(xml).contains("<friendlyName>Test Renderer</friendlyName>");
        assertThat(xml).contains("<UDN>uuid:renderer-uuid</UDN>");
        assertThat(xml).contains("<serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>");
    }

    @Test
    void shouldRoundtripSerializationAndParsing() {
        // Given: a device description
        var original = new DeviceDescription(
                "urn:schemas-upnp-org:device:MediaServer:1",
                "My Server", "MyCorp", "ServerModel",
                "3.0", "uuid:server-uuid", "SN-999", "http://host/",
                List.of(new ServiceDescription(
                        ServiceDescription.TYPE_CONTENT_DIRECTORY,
                        "urn:upnp-org:serviceId:CDS", "/cds.xml", "/cds/ctrl", "/cds/evt"
                )),
                List.of(),
                List.of(new DeviceIcon("image/jpeg", 64, 64, 24, "/icon.jpg"))
        );

        // When: serializing then parsing
        var xml = original.toXml();
        var parsed = DeviceDescription.parseXml(xml);

        // Then: key fields match
        assertThat(parsed.deviceType()).isEqualTo(original.deviceType());
        assertThat(parsed.friendlyName()).isEqualTo(original.friendlyName());
        assertThat(parsed.udn()).isEqualTo(original.udn());
        assertThat(parsed.services()).hasSize(1);
        assertThat(parsed.icons()).hasSize(1);
    }

    @Test
    void shouldParseEmbeddedDevices() {
        // Given: XML with embedded devices
        var xml = """
                <?xml version="1.0"?>
                <root xmlns="urn:schemas-upnp-org:device-1-0">
                <device>
                <deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>
                <friendlyName>Parent Device</friendlyName>
                <manufacturer>TestCorp</manufacturer>
                <modelName>ParentModel</modelName>
                <UDN>uuid:parent-1</UDN>
                <serviceList>
                <service>
                <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:CDS</serviceId>
                <SCPDURL>/cds.xml</SCPDURL>
                <controlURL>/cds/ctrl</controlURL>
                <eventSubURL>/cds/evt</eventSubURL>
                </service>
                </serviceList>
                <deviceList>
                <device>
                <deviceType>urn:schemas-upnp-org:device:SubDevice:1</deviceType>
                <friendlyName>Child Device</friendlyName>
                <manufacturer>TestCorp</manufacturer>
                <modelName>ChildModel</modelName>
                <UDN>uuid:child-1</UDN>
                </device>
                </deviceList>
                </device>
                </root>
                """;

        // When: parsing
        var device = DeviceDescription.parseXml(xml);

        // Then: embedded device is present
        assertThat(device.embeddedDevices()).hasSize(1);
        assertThat(device.embeddedDevices().get(0).friendlyName()).isEqualTo("Child Device");
        assertThat(device.embeddedDevices().get(0).udn()).isEqualTo("uuid:child-1");
    }

    @Test
    void shouldParseDeviceWithoutOptionalFields() {
        // Given: minimal device XML
        var xml = """
                <device>
                <deviceType>urn:schemas-upnp-org:device:Basic:1</deviceType>
                <friendlyName>Basic Device</friendlyName>
                <manufacturer>TestCorp</manufacturer>
                <modelName>BasicModel</modelName>
                <UDN>uuid:basic-1</UDN>
                </device>
                """;

        // When: parsing
        var device = DeviceDescription.parseXml(xml);

        // Then: optional fields are null, lists empty
        assertThat(device.modelNumber()).isNull();
        assertThat(device.serialNumber()).isNull();
        assertThat(device.presentationUrl()).isNull();
        assertThat(device.services()).isEmpty();
        assertThat(device.icons()).isEmpty();
        assertThat(device.embeddedDevices()).isEmpty();
    }

    @Test
    void shouldRejectXmlWithMissingRequiredFields() {
        // Given: XML missing deviceType
        var xml = """
                <device>
                <friendlyName>Broken</friendlyName>
                <manufacturer>Corp</manufacturer>
                <modelName>Model</modelName>
                </device>
                """;

        // When/Then: parsing should fail
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DeviceDescription.parseXml(xml));
    }

    @Test
    void shouldSerializeDeviceWithEmbeddedDevices() {
        // Given: a device with embedded sub-devices
        var child = new DeviceDescription(
                "urn:schemas-upnp-org:device:Sub:1",
                "Sub Device", "Corp", "SubModel", null,
                "uuid:sub-1", null, null, List.of(), List.of(), List.of()
        );
        var parent = new DeviceDescription(
                "urn:schemas-upnp-org:device:Parent:1",
                "Parent Device", "Corp", "ParentModel", null,
                "uuid:parent-1", null, null, List.of(), List.of(child), List.of()
        );

        // When: serializing
        var xml = parent.toXml();

        // Then: embedded device appears in deviceList
        assertThat(xml).contains("<deviceList>");
        assertThat(xml).contains("<friendlyName>Sub Device</friendlyName>");
        assertThat(xml).contains("<UDN>uuid:sub-1</UDN>");
    }
}
