package ssg.legoflow.http.proxy.reverse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackendServerTest {

    @Test
    void testDefaultWeight() {
        var server = new BackendServer("host", 8080);
        assertThat(server.getWeight()).isEqualTo(1);
    }

    @Test
    void testCustomWeight() {
        var server = new BackendServer("host", 8080, 5);
        assertThat(server.getWeight()).isEqualTo(5);
    }

    @Test
    void testHostAndPort() {
        var server = new BackendServer("api.example.com", 9090);
        assertThat(server.getHost()).isEqualTo("api.example.com");
        assertThat(server.getPort()).isEqualTo(9090);
    }

    @Test
    void testId() {
        var server = new BackendServer("host", 8080);
        assertThat(server.getId()).isEqualTo("host:8080");
    }

    @Test
    void testHealthyByDefault() {
        var server = new BackendServer("host", 8080);
        assertThat(server.isHealthy()).isTrue();
    }

    @Test
    void testSetHealthy() {
        var server = new BackendServer("host", 8080);
        server.setHealthy(false);
        assertThat(server.isHealthy()).isFalse();
        server.setHealthy(true);
        assertThat(server.isHealthy()).isTrue();
    }

    @Test
    void testActiveConnections() {
        var server = new BackendServer("host", 8080);
        assertThat(server.getActiveConnections()).isEqualTo(0);
        server.acquireConnection();
        server.acquireConnection();
        assertThat(server.getActiveConnections()).isEqualTo(2);
        server.releaseConnection();
        assertThat(server.getActiveConnections()).isEqualTo(1);
    }

    @Test
    void testTotalRequests() {
        var server = new BackendServer("host", 8080);
        assertThat(server.getTotalRequests()).isEqualTo(0);
        server.acquireConnection();
        server.acquireConnection();
        assertThat(server.getTotalRequests()).isEqualTo(2);
    }

    @Test
    void testFailedRequests() {
        var server = new BackendServer("host", 8080);
        assertThat(server.getFailedRequests()).isEqualTo(0);
        server.recordFailure();
        server.recordFailure();
        assertThat(server.getFailedRequests()).isEqualTo(2);
    }

    @Test
    void testEquals() {
        var server1 = new BackendServer("host", 8080);
        var server2 = new BackendServer("host", 8080);
        var server3 = new BackendServer("other", 8080);
        assertThat(server1).isEqualTo(server2);
        assertThat(server1).isNotEqualTo(server3);
    }

    @Test
    void testHashCode() {
        var server1 = new BackendServer("host", 8080);
        var server2 = new BackendServer("host", 8080);
        assertThat(server1.hashCode()).isEqualTo(server2.hashCode());
    }

    @Test
    void testToString() {
        var server = new BackendServer("host", 8080, 3);
        var str = server.toString();
        assertThat(str).contains("host:8080");
        assertThat(str).contains("weight=3");
    }

    @Test
    void testEqualsSameInstance() {
        var server = new BackendServer("host", 8080);
        assertThat(server).isEqualTo(server);
    }

    @Test
    void testNotEqualsDifferentType() {
        var server = new BackendServer("host", 8080);
        assertThat(server).isNotEqualTo("host:8080");
    }
}
