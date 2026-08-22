package ssg.legoflow.messaging.nats.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive NATS demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code NatsServer}. To test against
 * an external NATS server, set {@code DemoNatsAll.USE_EXTERNAL = true}
 * and configure host/port before running.</p>
 */
class DemoNatsAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoNatsAll.runAll();

        assertThat(results.pubSubMessages())
                .as("Pub/sub with wildcard subjects")
                .isEqualTo(3);

        assertThat(results.requestReply())
                .as("Request/reply returned correct result")
                .isTrue();

        assertThat(results.queueGroupTotal())
                .as("All queue group messages processed")
                .isEqualTo(12);

        assertThat(results.queueGroupWorkers())
                .as("Multiple queue group workers active")
                .isGreaterThanOrEqualTo(2);

        assertThat(results.jetStreamConsumed())
                .as("JetStream durable consumer consumed messages")
                .isEqualTo(5);

        assertThat(results.authToken())
                .as("Token authentication succeeded")
                .isTrue();

        assertThat(results.authUserPass())
                .as("User/password authentication succeeded")
                .isTrue();
    }
}
