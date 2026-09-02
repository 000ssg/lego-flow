package ssg.legoflow.upnp.device;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class ScpdDocumentTest {

    private static final String SCPD_XML = """
            <?xml version="1.0"?>
            <scpd xmlns="urn:schemas-upnp-org:service-1-0">
            <specVersion><major>1</major><minor>0</minor></specVersion>
            <actionList>
            <action>
            <name>Browse</name>
            <argumentList>
            <argument>
            <name>ObjectID</name>
            <direction>in</direction>
            <relatedStateVariable>A_ARG_TYPE_ObjectID</relatedStateVariable>
            </argument>
            <argument>
            <name>BrowseFlag</name>
            <direction>in</direction>
            <relatedStateVariable>A_ARG_TYPE_BrowseFlag</relatedStateVariable>
            </argument>
            <argument>
            <name>Result</name>
            <direction>out</direction>
            <relatedStateVariable>A_ARG_TYPE_Result</relatedStateVariable>
            </argument>
            <argument>
            <name>NumberReturned</name>
            <direction>out</direction>
            <relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable>
            </argument>
            </argumentList>
            </action>
            <action>
            <name>GetSystemUpdateID</name>
            <argumentList>
            <argument>
            <name>Id</name>
            <direction>out</direction>
            <relatedStateVariable>SystemUpdateID</relatedStateVariable>
            </argument>
            </argumentList>
            </action>
            </actionList>
            <serviceStateTable>
            <stateVariable sendEvents="yes">
            <name>SystemUpdateID</name>
            <dataType>ui4</dataType>
            <defaultValue>0</defaultValue>
            </stateVariable>
            <stateVariable sendEvents="no">
            <name>A_ARG_TYPE_BrowseFlag</name>
            <dataType>string</dataType>
            <allowedValueList>
            <allowedValue>BrowseMetadata</allowedValue>
            <allowedValue>BrowseDirectChildren</allowedValue>
            </allowedValueList>
            </stateVariable>
            <stateVariable sendEvents="no">
            <name>A_ARG_TYPE_Count</name>
            <dataType>ui4</dataType>
            <allowedValueRange>
            <minimum>0</minimum>
            <maximum>999999</maximum>
            <step>1</step>
            </allowedValueRange>
            </stateVariable>
            </serviceStateTable>
            </scpd>
            """;

    @Test
    void shouldParseActions() {
        // Given/When: parsing the SCPD XML
        var scpd = ScpdDocument.parseXml(SCPD_XML);

        // Then: actions are correctly parsed
        assertThat(scpd.actions()).hasSize(2);
        assertThat(scpd.actions().get(0).name()).isEqualTo("Browse");
        assertThat(scpd.actions().get(1).name()).isEqualTo("GetSystemUpdateID");
    }

    @Test
    void shouldParseActionArguments() {
        // Given/When: parsing the SCPD XML
        var scpd = ScpdDocument.parseXml(SCPD_XML);

        // Then: Browse action has correct arguments
        var browse = scpd.findAction("Browse").orElseThrow();
        assertThat(browse.arguments()).hasSize(4);
        assertThat(browse.inputArguments()).hasSize(2);
        assertThat(browse.outputArguments()).hasSize(2);

        var objectId = browse.arguments().get(0);
        assertThat(objectId.name()).isEqualTo("ObjectID");
        assertThat(objectId.direction()).isEqualTo("in");
        assertThat(objectId.relatedStateVariable()).isEqualTo("A_ARG_TYPE_ObjectID");
        assertThat(objectId.isInput()).isTrue();
    }

    @Test
    void shouldParseStateVariables() {
        // Given/When: parsing the SCPD XML
        var scpd = ScpdDocument.parseXml(SCPD_XML);

        // Then: state variables are correctly parsed
        assertThat(scpd.stateVariables()).hasSize(3);
    }

    @Test
    void shouldParseStateVariableWithSendEvents() {
        // Given/When: parsing the SCPD XML
        var scpd = ScpdDocument.parseXml(SCPD_XML);

        // Then: SystemUpdateID sends events
        var sysId = scpd.findStateVariable("SystemUpdateID").orElseThrow();
        assertThat(sysId.sendEvents()).isTrue();
        assertThat(sysId.dataType()).isEqualTo("ui4");
        assertThat(sysId.defaultValue()).isEqualTo("0");
    }

    @Test
    void shouldParseAllowedValues() {
        // Given/When: parsing the SCPD XML
        var scpd = ScpdDocument.parseXml(SCPD_XML);

        // Then: BrowseFlag has allowed values
        var browseFlag = scpd.findStateVariable("A_ARG_TYPE_BrowseFlag").orElseThrow();
        assertThat(browseFlag.hasAllowedValues()).isTrue();
        assertThat(browseFlag.allowedValues()).containsExactly("BrowseMetadata", "BrowseDirectChildren");
        assertThat(browseFlag.sendEvents()).isFalse();
    }

    @Test
    void shouldParseAllowedValueRange() {
        // Given/When: parsing the SCPD XML
        var scpd = ScpdDocument.parseXml(SCPD_XML);

        // Then: Count has range
        var count = scpd.findStateVariable("A_ARG_TYPE_Count").orElseThrow();
        assertThat(count.hasRange()).isTrue();
        assertThat(count.minimum()).isEqualTo("0");
        assertThat(count.maximum()).isEqualTo("999999");
        assertThat(count.step()).isEqualTo("1");
    }

    @Test
    void shouldSerializeToXml() {
        // Given: an SCPD document
        var scpd = new ScpdDocument(
                List.of(new ActionDescription("Play", List.of(
                        new ArgumentDescription("Speed", "in", "TransportPlaySpeed")
                ))),
                List.of(StateVariableDescription.of("TransportPlaySpeed", "string", false))
        );

        // When: serializing
        var xml = scpd.toXml();

        // Then: XML contains expected elements
        assertThat(xml).contains("<name>Play</name>");
        assertThat(xml).contains("<name>Speed</name>");
        assertThat(xml).contains("<direction>in</direction>");
        assertThat(xml).contains("<name>TransportPlaySpeed</name>");
        assertThat(xml).contains("<dataType>string</dataType>");
    }

    @Test
    void shouldRoundtripSerializationAndParsing() {
        // Given: an SCPD document
        var original = new ScpdDocument(
                List.of(new ActionDescription("GetVolume", List.of(
                        new ArgumentDescription("InstanceID", "in", "A_ARG_TYPE_InstanceID"),
                        new ArgumentDescription("CurrentVolume", "out", "Volume")
                ))),
                List.of(
                        StateVariableDescription.of("A_ARG_TYPE_InstanceID", "ui4", false),
                        new StateVariableDescription("Volume", "ui2", true, "50",
                                List.of(), "0", "100", "1")
                )
        );

        // When: serializing then parsing
        var xml = original.toXml();
        var parsed = ScpdDocument.parseXml(xml);

        // Then: actions and variables match
        assertThat(parsed.actions()).hasSize(1);
        assertThat(parsed.actions().get(0).name()).isEqualTo("GetVolume");
        assertThat(parsed.actions().get(0).arguments()).hasSize(2);
        assertThat(parsed.stateVariables()).hasSize(2);

        var volume = parsed.findStateVariable("Volume").orElseThrow();
        assertThat(volume.sendEvents()).isTrue();
        assertThat(volume.defaultValue()).isEqualTo("50");
        assertThat(volume.minimum()).isEqualTo("0");
        assertThat(volume.maximum()).isEqualTo("100");
    }
}
