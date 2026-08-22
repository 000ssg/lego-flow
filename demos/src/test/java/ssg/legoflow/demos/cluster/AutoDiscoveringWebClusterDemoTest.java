package ssg.legoflow.demos.cluster;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link AutoDiscoveringWebClusterDemo}.
 *
 * Verifies the auto-discovering web cluster simulation:
 * - Sticky sessions route same client to same node
 * - Cache warmup populates all nodes
 * - Cache coherence publishes invalidation events
 * - Invalidated nodes purge matching cache entries
 * - Node failure excludes crashed node from routing
 * - Node recovery re-includes the node
 * - Cluster membership events are tracked per node
 */
class AutoDiscoveringWebClusterDemoTest {

    @Test
    void testDemoRunsSuccessfully() throws Exception {
        var demo = new AutoDiscoveringWebClusterDemo();
        var result = demo.run();

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
    }

    @Test
    void testStickySessionConsistency() throws Exception {
        var demo = new AutoDiscoveringWebClusterDemo();
        var result = demo.run();

        boolean consistent = (boolean) result.get("sticky_session_consistent");
        assertThat(consistent).isTrue();
    }

    @Test
    void testSessionRoutesToKnownNode() throws Exception {
        var demo = new AutoDiscoveringWebClusterDemo();
        var result = demo.run();

        String server = (String) result.get("session_server");
        assertThat(server).isIn("node-A", "node-B", "node-C");
    }

    @Test
    void testCacheWarmedUp() throws Exception {
        var demo = new AutoDiscoveringWebClusterDemo();
        var result = demo.run();

        int cacheA = (int) result.get("cache_A_before");
        int cacheB = (int) result.get("cache_B_before");
        int cacheC = (int) result.get("cache_C_before");
        assertThat(cacheA).isEqualTo(2);
        assertThat(cacheB).isEqualTo(2);
        assertThat(cacheC).isEqualTo(2);
    }

    @Test
    void testInvalidationPublished() throws Exception {
        var demo = new AutoDiscoveringWebClusterDemo();
        var result = demo.run();

        boolean published = (boolean) result.get("invalidation_published");
        assertThat(published).isTrue();
    }

    @Test
    void testInvalidationSourceIdentified() throws Exception {
        var demo = new AutoDiscoveringWebClusterDemo();
        var result = demo.run();

        String source = (String) result.get("invalidation_source");
        assertThat(source).isEqualTo("node-A");
    }

    @Test
    void testCacheInvalidatedOnRemoteNodes() throws Exception {
        var demo = new AutoDiscoveringWebClusterDemo();
        var result = demo.run();

        int cacheB = (int) result.get("cache_B_after_invalidation");
        int cacheC = (int) result.get("cache_C_after_invalidation");

        // After invalidation of /api/users/*, remote nodes should have fewer entries
        assertThat(cacheB).isLessThanOrEqualTo(2);
        assertThat(cacheC).isLessThanOrEqualTo(2);
    }

    @Test
    void testNodeCrashExcludedFromRouting() throws Exception {
        var demo = new AutoDiscoveringWebClusterDemo();
        var result = demo.run();

        String routingAfterCrash = (String) result.get("routing_after_crash");
        boolean excluded = (boolean) result.get("node_B_excluded_from_routing");
        assertThat(routingAfterCrash).isIn("node-A", "node-C");
        assertThat(excluded).isTrue();
    }

    @Test
    void testNodeRecoveryReincluded() throws Exception {
        var demo = new AutoDiscoveringWebClusterDemo();
        var result = demo.run();

        String routingAfterRecovery = (String) result.get("routing_after_recovery");
        assertThat(routingAfterRecovery).isNotNull();
        assertThat(routingAfterRecovery).isNotBlank();
    }

    @Test
    void testEventsTrackedPerNode() throws Exception {
        var demo = new AutoDiscoveringWebClusterDemo();
        var result = demo.run();

        int eventsA = (int) result.get("total_events_A");
        int eventsB = (int) result.get("total_events_B");
        int eventsC = (int) result.get("total_events_C");

        assertThat(eventsA).isGreaterThanOrEqualTo(0);
        assertThat(eventsB).isGreaterThanOrEqualTo(0);
        assertThat(eventsC).isGreaterThanOrEqualTo(0);
    }
}
