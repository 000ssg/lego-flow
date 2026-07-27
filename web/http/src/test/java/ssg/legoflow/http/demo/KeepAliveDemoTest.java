package ssg.legoflow.http.demo;

import ssg.legoflow.http.connection.ConnectionConfig;
import ssg.legoflow.http.connection.ConnectionManager;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.demo.server.MinimalServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class KeepAliveDemoTest {

    private MinimalServer server;
    private ConnectionManager connectionManager;

    @BeforeEach
    void setUp() {
        server = new MinimalServer();
        var config = new ConnectionConfig();
        config.setMaxConnections(5);
        config.setKeepAliveTimeout(30);
        config.setMaxKeepAliveRequests(100);
        connectionManager = new ConnectionManager(config);
    }

    @Test
    void testKeepAliveDefaultIsTrue() {
        assertThat(connectionManager.isKeepAlive(null)).isTrue();
    }

    @Test
    void testKeepAliveWithKeepAliveHeader() {
        assertThat(connectionManager.isKeepAlive("keep-alive")).isTrue();
    }

    @Test
    void testKeepAliveWithCloseHeader() {
        assertThat(connectionManager.isKeepAlive("close")).isFalse();
    }

    @Test
    void testAcquireAndReleaseConnection() {
        assertThat(connectionManager.acquireConnection("localhost")).isTrue();
        assertThat(connectionManager.getActiveCount("localhost")).isEqualTo(1);

        connectionManager.releaseConnection("localhost");
        assertThat(connectionManager.getActiveCount("localhost")).isEqualTo(0);
    }

    @Test
    void testMaxConnectionsEnforced() {
        for (int i = 0; i < 5; i++) {
            assertThat(connectionManager.acquireConnection("localhost")).isTrue();
        }
        assertThat(connectionManager.acquireConnection("localhost")).isFalse();
    }

    @Test
    void testConnectionReuse() {
        connectionManager.acquireConnection("localhost");
        connectionManager.releaseConnection("localhost");
        assertThat(connectionManager.acquireConnection("localhost")).isTrue();
    }

    @Test
    void testMultipleHostsIndependent() {
        for (int i = 0; i < 5; i++) {
            connectionManager.acquireConnection("host-a");
        }
        assertThat(connectionManager.acquireConnection("host-a")).isFalse();
        assertThat(connectionManager.acquireConnection("host-b")).isTrue();
    }

    @Test
    void testMultipleRequestsOnSameConnection() {
        var router = server.getServer().getRouter();

        for (int i = 0; i < 10; i++) {
            var request = HttpRequest.of(HttpMethod.GET, "/");
            request.getHeaders().set(HttpHeaders.CONNECTION, "keep-alive");
            var response = router.dispatch(null, request);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void testConnectionConfigValues() {
        assertThat(connectionManager.getConfig().getMaxConnections()).isEqualTo(5);
        assertThat(connectionManager.getConfig().getKeepAliveTimeout()).isEqualTo(30);
        assertThat(connectionManager.getConfig().getMaxKeepAliveRequests()).isEqualTo(100);
    }
}
