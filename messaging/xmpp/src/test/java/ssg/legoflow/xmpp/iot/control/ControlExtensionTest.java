package ssg.legoflow.xmpp.iot.control;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ControlExtension}.
 *
 * @since 1.0.0
 */
class ControlExtensionTest {

    @Test
    void testNamespace() {
        var ext = ControlExtension.set(List.of());
        assertThat(ext.getNamespace()).isEqualTo("urn:xmpp:iot:control");
    }

    @Test
    void testSetElement() {
        var params = List.of(
                ControlParameter.ofDouble("targetTemp", 22.0),
                ControlParameter.ofBoolean("enabled", true));
        var ext = ControlExtension.set(params);
        assertThat(ext.getElementName()).isEqualTo("set");
        var xml = ext.toXml();
        assertThat(xml).contains("urn:xmpp:iot:control");
        assertThat(xml).contains("<double");
        assertThat(xml).contains("<boolean");
    }

    @Test
    void testSetResponseElement() {
        var ext = ControlExtension.setResponse(true);
        assertThat(ext.getElementName()).isEqualTo("setResponse");
        assertThat(ext.isSuccess()).isTrue();
        assertThat(ext.toXml()).contains("responseCode=\"OK\"");
    }

    @Test
    void testSetResponseError() {
        var ext = ControlExtension.setResponse(false);
        assertThat(ext.toXml()).contains("responseCode=\"Error\"");
    }

    @Test
    void testParseParameters() {
        String xml = "<double name=\"temp\" value=\"22.5\"/>" +
                "<boolean name=\"on\" value=\"true\"/>" +
                "<string name=\"mode\" value=\"auto\"/>";
        var params = ControlExtension.parseParameters(xml);
        assertThat(params).hasSize(3);
        assertThat(params.get(0).type()).isEqualTo(ControlParameter.ControlParameterType.DOUBLE);
        assertThat(params.get(1).type()).isEqualTo(ControlParameter.ControlParameterType.BOOLEAN);
        assertThat(params.get(2).type()).isEqualTo(ControlParameter.ControlParameterType.STRING);
    }
}
