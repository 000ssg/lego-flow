package ssg.legoflow.xmpp.iot.discovery;

import ssg.legoflow.xmpp.core.JID;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link DiscoveryExtension}.
 *
 * @since 0.1.0
 */
class DiscoveryExtensionTest {

    @Test
    void testNamespace() {
        var ext = DiscoveryExtension.register(null);
        assertThat(ext.getNamespace()).isEqualTo("urn:xmpp:iot:discovery");
    }

    @Test
    void testRegisterElement() {
        var thing = new ThingDescription("s1", null, "Sensor",
                "Acme", "T-100", "SN1", Map.of("type", "sensor"), false);
        var ext = DiscoveryExtension.register(thing);
        assertThat(ext.getElementName()).isEqualTo("register");
        var xml = ext.toXml();
        assertThat(xml).contains("urn:xmpp:iot:discovery");
        assertThat(xml).contains("nodeId=\"s1\"");
    }

    @Test
    void testClaimedElement() {
        var thing = new ThingDescription("s1", JID.parse("user@example.com"),
                "Sensor", null, null, null, Map.of(), true);
        var ext = DiscoveryExtension.claimed(thing);
        assertThat(ext.getElementName()).isEqualTo("claimed");
        assertThat(ext.toXml()).contains("owner=\"user@example.com\"");
    }

    @Test
    void testDisownElement() {
        var thing = new ThingDescription("s1", null, "Sensor",
                null, null, null, Map.of(), false);
        var ext = DiscoveryExtension.disown(thing);
        assertThat(ext.getElementName()).isEqualTo("disown");
        assertThat(ext.toXml()).contains("nodeId=\"s1\"");
    }

    @Test
    void testUnregisterElement() {
        var thing = new ThingDescription("s1", null, "Sensor",
                null, null, null, Map.of(), false);
        var ext = DiscoveryExtension.unregister(thing);
        assertThat(ext.getElementName()).isEqualTo("unregister");
        assertThat(ext.toXml()).contains("nodeId=\"s1\"");
    }
}
