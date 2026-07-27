package ssg.legoflow.http.proxy.demo;

import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoadBalancedProxyDemoTest {

    private LoadBalancedProxyDemo demo;

    @BeforeEach
    void setUp() {
        demo = new LoadBalancedProxyDemo();
    }

    @Test
    void testDemoRuns() {
        String result = demo.run();
        assertThat(result).isNotEmpty();
        assertThat(result).contains("Backend distribution");
    }

    @Test
    void testRequestsDistributed() {
        for (int i = 0; i < 12; i++) {
            var response = demo.getProxy().handleRequest(
                    HttpRequest.of(HttpMethod.GET, "/api/resource"));
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        }
        assertThat(demo.getBackend1().getTotalRequests()).isGreaterThan(0);
        assertThat(demo.getBackend2().getTotalRequests()).isGreaterThan(0);
        assertThat(demo.getBackend3().getTotalRequests()).isGreaterThan(0);
    }

    @Test
    void testWeightedDistribution() {
        // backend1 has weight 2, others have weight 1
        for (int i = 0; i < 20; i++) {
            demo.getProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/api/resource"));
        }
        assertThat(demo.getBackend1().getTotalRequests())
                .isGreaterThan(demo.getBackend2().getTotalRequests());
    }

    @Test
    void testUnhealthyBackendSkipped() {
        demo.getBackend2().setHealthy(false);
        for (int i = 0; i < 6; i++) {
            demo.getProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/api/resource"));
        }
        assertThat(demo.getBackend2().getTotalRequests()).isEqualTo(0);
    }

    @Test
    void testAllBackendsUnhealthy() {
        demo.getBackend1().setHealthy(false);
        demo.getBackend2().setHealthy(false);
        demo.getBackend3().setHealthy(false);
        var response = demo.getProxy().handleRequest(
                HttpRequest.of(HttpMethod.GET, "/api/resource"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void testRequestCountTotal() {
        demo.run();
        assertThat(demo.getProxy().getRequestCount()).isGreaterThanOrEqualTo(9);
    }

    @Test
    void testBackendActiveConnections() {
        // After requests complete, connections should be released
        demo.getProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/api/resource"));
        assertThat(demo.getBackend1().getActiveConnections() +
                demo.getBackend2().getActiveConnections() +
                demo.getBackend3().getActiveConnections()).isEqualTo(0);
    }

    @Test
    void testRouteCount() {
        assertThat(demo.getProxy().getRoutes()).hasSize(2);
    }

    @Test
    void testProxyName() {
        assertThat(demo.getProxy().getConfig().getProxyName()).isEqualTo("demo-lb-proxy");
    }

    @Test
    void testRecoveryAfterUnhealthy() {
        demo.getBackend2().setHealthy(false);
        demo.getProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/api/resource"));
        assertThat(demo.getBackend2().getTotalRequests()).isEqualTo(0);

        demo.getBackend2().setHealthy(true);
        // After multiple requests, backend2 should now get traffic
        for (int i = 0; i < 12; i++) {
            demo.getProxy().handleRequest(HttpRequest.of(HttpMethod.GET, "/api/resource"));
        }
        assertThat(demo.getBackend2().getTotalRequests()).isGreaterThan(0);
    }
}
