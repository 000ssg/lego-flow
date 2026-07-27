package ssg.legoflow.xmpp.iot.discovery;

import ssg.legoflow.xmpp.core.JID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link IoTRegistry}.
 *
 * @since 1.0.0
 */
class IoTRegistryTest {

    private IoTRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new IoTRegistry();
    }

    @Test
    void testRegister() {
        var thing = new ThingDescription("sensor-1", null, "Temp Sensor",
                "Acme", "T-100", "SN001", Map.of("type", "sensor"), false);
        registry.register(thing);
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.getThing("sensor-1")).isNotNull();
    }

    @Test
    void testUnregister() {
        var thing = new ThingDescription("sensor-1", null, "Temp",
                null, null, null, Map.of(), false);
        registry.register(thing);
        var removed = registry.unregister("sensor-1");
        assertThat(removed).isNotNull();
        assertThat(registry.size()).isEqualTo(0);
    }

    @Test
    void testClaim() {
        var thing = new ThingDescription("sensor-1", null, "Temp",
                null, null, null, Map.of(), false);
        registry.register(thing);
        var owner = JID.parse("user@example.com");
        boolean success = registry.claim("sensor-1", owner);
        assertThat(success).isTrue();
        assertThat(registry.getThing("sensor-1").claimed()).isTrue();
        assertThat(registry.getThing("sensor-1").owner()).isEqualTo(owner);
    }

    @Test
    void testClaimAlreadyClaimed() {
        var thing = new ThingDescription("sensor-1", null, "Temp",
                null, null, null, Map.of(), false);
        registry.register(thing);
        registry.claim("sensor-1", JID.parse("user1@example.com"));
        boolean success = registry.claim("sensor-1", JID.parse("user2@example.com"));
        assertThat(success).isFalse();
    }

    @Test
    void testDisown() {
        var thing = new ThingDescription("sensor-1", null, "Temp",
                null, null, null, Map.of(), false);
        registry.register(thing);
        registry.claim("sensor-1", JID.parse("user@example.com"));
        boolean success = registry.disown("sensor-1");
        assertThat(success).isTrue();
        assertThat(registry.getThing("sensor-1").claimed()).isFalse();
    }

    @Test
    void testSearchByTags() {
        registry.register(new ThingDescription("s1", null, "Temp",
                "Acme", null, null, Map.of("type", "sensor", "location", "kitchen"), false));
        registry.register(new ThingDescription("s2", null, "Light",
                "Smart", null, null, Map.of("type", "actuator", "location", "kitchen"), false));
        registry.register(new ThingDescription("s3", null, "Humid",
                "Acme", null, null, Map.of("type", "sensor", "location", "bedroom"), false));

        var sensors = registry.search(Map.of("type", "sensor"));
        assertThat(sensors).hasSize(2);

        var kitchenSensors = registry.search(Map.of("type", "sensor", "location", "kitchen"));
        assertThat(kitchenSensors).hasSize(1);
    }

    @Test
    void testSearchByManufacturer() {
        registry.register(new ThingDescription("s1", null, "T1",
                "Acme", null, null, Map.of(), false));
        registry.register(new ThingDescription("s2", null, "T2",
                "Smart", null, null, Map.of(), false));
        var results = registry.search(Map.of("manufacturer", "Acme"));
        assertThat(results).hasSize(1);
    }

    @Test
    void testGetAllThings() {
        registry.register(new ThingDescription("s1", null, "T1",
                null, null, null, Map.of(), false));
        registry.register(new ThingDescription("s2", null, "T2",
                null, null, null, Map.of(), false));
        assertThat(registry.getThings()).hasSize(2);
    }
}
