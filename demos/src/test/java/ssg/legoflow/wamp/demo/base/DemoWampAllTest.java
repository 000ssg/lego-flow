package ssg.legoflow.wamp.demo.base;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive WAMP demo and verifies all feature sections.
 *
 * <p>By default, uses in-memory transport. To test against a WebSocket
 * server, set {@code DemoWampAll.USE_EXTERNAL = true} before running.</p>
 *
 * @since 0.1.0
 */
class DemoWampAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoWampAll.runAll();

        assertThat(results.rpcResultCorrect())
                .as("RPC call returns correct result (3+5=8)")
                .isTrue();

        assertThat(results.pubSubEventsReceived())
                .as("Pub/Sub delivers at least 2 events")
                .isGreaterThanOrEqualTo(2);

        assertThat(results.realmIsolated())
                .as("Procedures in one realm are invisible to another")
                .isTrue();

        assertThat(results.sessionEstablished())
                .as("HELLO/WELCOME handshake establishes session with positive ID")
                .isTrue();

        assertThat(results.calculatorSum())
                .as("Calculator add(17.5, 24.5) returns 42.0")
                .isEqualTo(42.0);

        assertThat(results.chatMessageCount())
                .as("Chat room delivers messages to participants")
                .isGreaterThanOrEqualTo(2);

        assertThat(results.serializationOk())
                .as("JSON serialization round-trip preserves HELLO message")
                .isTrue();

        assertThat(results.prefixMatchCount())
                .as("Prefix subscription matches at least 3 events")
                .isGreaterThanOrEqualTo(3);
    }
}
