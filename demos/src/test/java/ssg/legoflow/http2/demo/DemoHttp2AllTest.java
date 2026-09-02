package ssg.legoflow.http2.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive HTTP/2 demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code Http2Server}. To test against
 * an external HTTP/2 server (Nginx, Apache, Caddy), set
 * {@code DemoHttp2All.USE_EXTERNAL = true} and configure host/port before running.</p>
 */
class DemoHttp2AllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoHttp2All.runAll();

        assertThat(results.multiplexedStreams())
                .as("Multiplexed concurrent streams")
                .isEqualTo(3);

        assertThat(results.serverPush())
                .as("Server push PUSH_PROMISE")
                .isTrue();

        assertThat(results.flowControl())
                .as("Flow control window management")
                .isTrue();

        assertThat(results.hpackCompression())
                .as("HPACK header compression round-trip")
                .isTrue();

        assertThat(results.h2cUpgrade())
                .as("H2c cleartext upgrade")
                .isTrue();

        assertThat(results.streamPriorities())
                .as("Stream priority frames")
                .isTrue();

        assertThat(results.settingsFrame())
                .as("SETTINGS frame encode/decode")
                .isTrue();

        assertThat(results.goaway())
                .as("GOAWAY graceful shutdown")
                .isTrue();
    }
}
