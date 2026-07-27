package ssg.legoflow.messaging.stomp.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive STOMP demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code StompBroker} with {@code InMemoryStompTransport}.
 * To test against an external ActiveMQ/RabbitMQ STOMP plugin, set
 * {@code DemoStompAll.USE_EXTERNAL = true} and configure host/port before running.</p>
 */
class DemoStompAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoStompAll.runAll();

        assertThat(results.connectDisconnect())
                .as("CONNECT/DISCONNECT lifecycle")
                .isTrue();

        assertThat(results.sendSubscribe())
                .as("SEND/SUBSCRIBE delivers messages")
                .isEqualTo(3);

        assertThat(results.unsubscribeOk())
                .as("UNSUBSCRIBE stops message delivery")
                .isTrue();

        assertThat(results.messageHeaders())
                .as("MESSAGE frames contain required headers")
                .isTrue();

        assertThat(results.transactionCommit())
                .as("Transaction COMMIT delivers all buffered messages")
                .isEqualTo(3);

        assertThat(results.transactionAbort())
                .as("Transaction ABORT discards all buffered messages")
                .isTrue();

        assertThat(results.ackNack())
                .as("ACK/NACK in client-individual mode")
                .isTrue();

        assertThat(results.receiptReceived())
                .as("RECEIPT frame received for sent message")
                .isTrue();

        assertThat(results.contentTypeOk())
                .as("Content-type header preserved in MESSAGE frame")
                .isTrue();
    }
}
