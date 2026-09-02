package ssg.legoflow.http.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive HTTP demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code HttpServer}. To test against
 * an external HTTP server (Apache, Nginx, Caddy), set
 * {@code DemoHttpAll.USE_EXTERNAL = true} and configure host/port before running.</p>
 */
class DemoHttpAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoHttpAll.runAll();

        assertThat(results.crudOperations())
                .as("GET/POST/PUT/DELETE CRUD operations")
                .isTrue();

        assertThat(results.contentNegotiation())
                .as("Content negotiation (media type, encoding)")
                .isTrue();

        assertThat(results.caching())
                .as("Caching (ETag, If-Modified-Since, 304)")
                .isTrue();

        assertThat(results.compression())
                .as("Gzip compression and decompression")
                .isTrue();

        assertThat(results.chunkedTransfer())
                .as("Chunked transfer encoding")
                .isTrue();

        assertThat(results.byteRanges())
                .as("Byte range requests (206 Partial Content)")
                .isTrue();

        assertThat(results.keepAlive())
                .as("Keep-alive connection handling")
                .isTrue();

        assertThat(results.webSocket())
                .as("WebSocket handshake and framing")
                .isTrue();

        assertThat(results.routing())
                .as("Path-based routing with 404/405")
                .isTrue();

        assertThat(results.featureProfiles())
                .as("Feature profiles (minimal, standard, full)")
                .isTrue();
    }
}
