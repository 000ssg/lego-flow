package ssg.legoflow.xmpp.demo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link IoTDiscoveryDemo}.
 *
 * @since 1.0.0
 */
class IoTDiscoveryDemoTest {

    private IoTDiscoveryDemo demo;

    @BeforeEach
    void setUp() {
        demo = new IoTDiscoveryDemo();
        demo.setup("example.com");
        demo.registerSampleThings();
    }

    @AfterEach
    void tearDown() {
        demo.shutdown();
    }

    @Test
    void testThingsRegistered() {
        assertThat(demo.getClient().getDiscoveryManager().getRegistry().size()).isEqualTo(4);
    }

    @Test
    void testSearchByType() {
        var sensors = demo.searchThings(Map.of("type", "sensor"));
        assertThat(sensors).hasSize(2);
    }

    @Test
    void testSearchByLocation() {
        var kitchenThings = demo.searchThings(Map.of("location", "kitchen"));
        assertThat(kitchenThings).hasSize(1);
    }

    @Test
    void testClaimThing() {
        boolean success = demo.claimThing("sensor-001");
        assertThat(success).isTrue();
        var thing = demo.getClient().getDiscoveryManager().getRegistry().getThing("sensor-001");
        assertThat(thing.claimed()).isTrue();
    }

    @Test
    void testClaimAlreadyClaimedThing() {
        demo.claimThing("sensor-001");
        boolean secondClaim = demo.claimThing("sensor-001");
        assertThat(secondClaim).isFalse();
    }
}
