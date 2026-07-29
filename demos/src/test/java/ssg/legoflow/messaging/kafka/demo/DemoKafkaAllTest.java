package ssg.legoflow.messaging.kafka.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive Kafka demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code KafkaBroker}. To test against
 * an external Apache Kafka, set {@code DemoKafkaAll.USE_EXTERNAL = true}
 * and configure host/port before running.</p>
 */
class DemoKafkaAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoKafkaAll.runAll();

        assertThat(results.topicManagement())
                .as("Topic create/expand/delete")
                .isTrue();

        assertThat(results.produceConsume())
                .as("Produce and consume messages")
                .isGreaterThanOrEqualTo(20);

        assertThat(results.idempotentDedup())
                .as("Idempotent production (sequential offsets)")
                .isTrue();

        assertThat(results.transactionCommit())
                .as("Transaction committed messages visible")
                .isEqualTo(3);

        assertThat(results.adminOps())
                .as("Admin operations (37+ API keys)")
                .isTrue();

        assertThat(results.configOps())
                .as("Dynamic configuration describe/alter")
                .isTrue();

        assertThat(results.compactedRecords())
                .as("Log compaction retains latest per key")
                .isEqualTo(3);

        assertThat(results.rebalanceEvents())
                .as("At least one rebalance event observed")
                .isGreaterThanOrEqualTo(1);

        assertThat(results.diskPersistence())
                .as("Disk persistence survives broker restart")
                .isTrue();
    }
}
