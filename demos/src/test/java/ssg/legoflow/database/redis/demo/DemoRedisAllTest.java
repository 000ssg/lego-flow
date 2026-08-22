package ssg.legoflow.database.redis.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive Redis demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code RedisServer}. To test against
 * an external Redis, Valkey, KeyDB, or Dragonfly server, set
 * {@code DemoRedisAll.USE_EXTERNAL = true} and configure host/port
 * before running.</p>
 */
class DemoRedisAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoRedisAll.runAll();

        assertThat(results.setGetDel())
                .as("SET/GET/DEL cycle")
                .isTrue();

        assertThat(results.ttlExpiration())
                .as("TTL expiration set and verified")
                .isTrue();

        assertThat(results.pipelineResponses())
                .as("Pipeline batched responses")
                .isEqualTo(7);

        assertThat(results.pubSubMessages())
                .as("Pub/sub messages received")
                .isEqualTo(2);

        assertThat(results.dataTypeOps())
                .as("Data types (lists, hashes, sets)")
                .isTrue();

        assertThat(results.transactionResult())
                .as("MULTI/EXEC transaction")
                .isTrue();

        assertThat(results.clusterClient())
                .as("Cluster client with hash slot routing")
                .isTrue();

        assertThat(results.authentication())
                .as("AUTH password authentication")
                .isTrue();

        assertThat(results.hyperLogLog())
                .as("HyperLogLog cardinality estimation")
                .isTrue();

        assertThat(results.geoCommands())
                .as("Geo commands (GEOADD/GEODIST/GEOPOS/GEOSEARCH)")
                .isTrue();
    }
}
