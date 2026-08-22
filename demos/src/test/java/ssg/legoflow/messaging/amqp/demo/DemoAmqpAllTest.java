package ssg.legoflow.messaging.amqp.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive AMQP demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code AmqpContainer}. To test against
 * an external RabbitMQ/Artemis/Azure Service Bus, set {@code DemoAmqpAll.USE_EXTERNAL = true}
 * and configure host/port before running.</p>
 */
class DemoAmqpAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoAmqpAll.runAll();

        assertThat(results.sendReceive())
                .as("Send and receive messages")
                .isEqualTo(5);

        assertThat(results.pubSubFanOut())
                .as("Pub/sub fan-out delivery to at least one subscriber")
                .isTrue();

        assertThat(results.requestReply())
                .as("Request/reply with correlation-id")
                .isTrue();

        assertThat(results.transactionState())
                .as("Transactional delivery states (commit/rollback)")
                .isTrue();

        assertThat(results.creditFlowControl())
                .as("Credit-based flow control allows sending")
                .isTrue();

        assertThat(results.saslAuth())
                .as("SASL PLAIN authentication succeeds")
                .isTrue();

        assertThat(results.multipleSessions())
                .as("Multiple sessions on single connection")
                .isEqualTo(3);

        assertThat(results.linkCount())
                .as("Sender and receiver links created")
                .isEqualTo(4);
    }
}
