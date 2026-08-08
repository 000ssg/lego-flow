package ssg.legoflow.http.proxy.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive HTTP proxy demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house proxy components. To test against
 * an external proxy (Squid, Nginx, HAProxy), set
 * {@code DemoHttpProxyAll.USE_EXTERNAL = true} and configure host/port before running.</p>
 *
 * @since 0.1.0
 */
class DemoHttpProxyAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoHttpProxyAll.runAll();

        assertThat(results.forwardProxyBasic())
                .as("Forward proxy basic GET/POST forwarding and access control")
                .isTrue();

        assertThat(results.reverseProxyBasic())
                .as("Reverse proxy path routing and load balancing")
                .isTrue();

        assertThat(results.proxyFilters())
                .as("Request/response filter pipeline")
                .isTrue();

        assertThat(results.cacheProxy())
                .as("Caching proxy hits/misses/invalidation")
                .isTrue();

        assertThat(results.connectTunnel())
                .as("CONNECT method HTTPS tunneling")
                .isTrue();

        assertThat(results.proxyHeaders())
                .as("Via, X-Forwarded-For, X-Forwarded-Proto headers")
                .isTrue();

        assertThat(results.errorHandling())
                .as("502/504/503 error responses")
                .isTrue();
    }
}
