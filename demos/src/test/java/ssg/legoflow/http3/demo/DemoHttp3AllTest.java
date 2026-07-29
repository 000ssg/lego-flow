package ssg.legoflow.http3.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive HTTP/3 demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code Http3Server}. To test against
 * an external HTTP/3 server (Nginx QUIC, Caddy, Cloudflare), set
 * {@code DemoHttp3All.USE_EXTERNAL = true} and configure host/port
 * before running.</p>
 */
class DemoHttp3AllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoHttp3All.runAll();

        assertThat(results.quicTransport())
                .as("QUIC connection lifecycle (handshake, streams, close)")
                .isTrue();

        assertThat(results.multiplexedStreams())
                .as("Multiplexed streams (no head-of-line blocking)")
                .isGreaterThanOrEqualTo(3);

        assertThat(results.zeroRttEstablished())
                .as("0-RTT connection establishment (session resumption)")
                .isTrue();

        assertThat(results.connectionMigrated())
                .as("Connection migration (network path change)")
                .isTrue();

        assertThat(results.qpackCompression())
                .as("QPACK header compression round-trip")
                .isGreaterThanOrEqualTo(10);

        assertThat(results.serverPushCompleted())
                .as("Server push (PUSH_PROMISE + pushed response)")
                .isTrue();

        assertThat(results.flowControlVerified())
                .as("Flow control (connection-level + stream-level)")
                .isTrue();

        assertThat(results.streamTypesVerified())
                .as("Stream types (bidi + uni + HTTP/3 control streams)")
                .isGreaterThanOrEqualTo(7);

        assertThat(results.tlsHandshakeVerified())
                .as("TLS 1.3 handshake (ALPN, cipher, protocol negotiation)")
                .isTrue();

        assertThat(results.dynamicTableEntries())
                .as("QPACK dynamic table entries")
                .isGreaterThanOrEqualTo(4);
    }
}
