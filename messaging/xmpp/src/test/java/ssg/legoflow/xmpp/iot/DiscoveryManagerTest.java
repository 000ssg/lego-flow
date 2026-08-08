package ssg.legoflow.xmpp.iot;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.iot.discovery.IoTRegistry;
import ssg.legoflow.xmpp.iot.discovery.ThingDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DiscoveryManager}.
 *
 * @since 0.1.0
 */
class DiscoveryManagerTest {

    private DiscoveryManager manager;

    @BeforeEach
    void setUp() {
        manager = new DiscoveryManager();
        manager.setLocalJid(JID.parse("user@example.com"));
    }

    @Test
    void testRegisterThing() {
        var thing = new ThingDescription("s1", null, "Sensor",
                "Acme", "T-100", "SN1", Map.of("type", "sensor"), false);
        manager.registerThing(thing).join();
        assertThat(manager.getRegistry().size()).isEqualTo(1);
    }

    @Test
    void testSearchThings() {
        manager.registerThing(new ThingDescription("s1", null, "Temp",
                "Acme", null, null, Map.of("type", "sensor"), false)).join();
        manager.registerThing(new ThingDescription("s2", null, "Light",
                "Smart", null, null, Map.of("type", "actuator"), false)).join();

        var results = manager.searchThings(Map.of("type", "sensor")).join();
        assertThat(results).hasSize(1);
    }

    @Test
    void testClaimThing() {
        manager.registerThing(new ThingDescription("s1", null, "Sensor",
                null, null, null, Map.of(), false)).join();
        boolean success = manager.claimThing("s1").join();
        assertThat(success).isTrue();
        assertThat(manager.getRegistry().getThing("s1").claimed()).isTrue();
    }

    @Test
    void testClaimUnknownThing() {
        boolean success = manager.claimThing("nonexistent").join();
        assertThat(success).isFalse();
    }

    @Test
    void testGetRegistry() {
        assertThat(manager.getRegistry()).isNotNull();
        assertThat(manager.getRegistry()).isInstanceOf(IoTRegistry.class);
    }

    @Test
    void testSearchAllWithNullTags() {
        manager.registerThing(new ThingDescription("s1", null, "T1",
                null, null, null, Map.of(), false)).join();
        manager.registerThing(new ThingDescription("s2", null, "T2",
                null, null, null, Map.of(), false)).join();
        var results = manager.searchThings(null).join();
        assertThat(results).hasSize(2);
    }
}
