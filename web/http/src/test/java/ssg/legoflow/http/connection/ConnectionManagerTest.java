package ssg.legoflow.http.connection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ConnectionManagerTest {

    @Test
    void testAcquireAndRelease() {
        // Given
        var manager = new ConnectionManager();

        // When
        boolean acquired = manager.acquireConnection("example.com");

        // Then
        assertThat(acquired).isTrue();
        assertThat(manager.getActiveCount("example.com")).isEqualTo(1);

        // When
        manager.releaseConnection("example.com");

        // Then
        assertThat(manager.getActiveCount("example.com")).isZero();
    }

    @Test
    void testMaxConnectionsEnforced() {
        // Given
        var config = new ConnectionConfig();
        config.setMaxConnections(2);
        var manager = new ConnectionManager(config);

        // When
        assertThat(manager.acquireConnection("host")).isTrue();
        assertThat(manager.acquireConnection("host")).isTrue();
        assertThat(manager.acquireConnection("host")).isFalse();
    }

    @Test
    void testCanAcceptConnection() {
        // Given
        var config = new ConnectionConfig();
        config.setMaxConnections(1);
        var manager = new ConnectionManager(config);

        // Then
        assertThat(manager.canAcceptConnection("host")).isTrue();

        // When
        manager.acquireConnection("host");

        // Then
        assertThat(manager.canAcceptConnection("host")).isFalse();
    }

    @Test
    void testGetActiveCountForUnknownHost() {
        // Given
        var manager = new ConnectionManager();

        // Then
        assertThat(manager.getActiveCount("unknown.com")).isZero();
    }

    @Test
    void testIsKeepAliveDefault() {
        // Given
        var manager = new ConnectionManager();

        // Then
        assertThat(manager.isKeepAlive(null)).isTrue();
    }

    @Test
    void testIsKeepAliveClose() {
        // Given
        var manager = new ConnectionManager();

        // Then
        assertThat(manager.isKeepAlive("close")).isFalse();
        assertThat(manager.isKeepAlive("Close")).isFalse();
    }

    @Test
    void testIsKeepAliveKeepAlive() {
        // Given
        var manager = new ConnectionManager();

        // Then
        assertThat(manager.isKeepAlive("keep-alive")).isTrue();
    }

    @Test
    void testMultipleHostsIndependent() {
        // Given
        var config = new ConnectionConfig();
        config.setMaxConnections(1);
        var manager = new ConnectionManager(config);

        // When
        assertThat(manager.acquireConnection("host1")).isTrue();
        assertThat(manager.acquireConnection("host2")).isTrue();

        // Then
        assertThat(manager.getActiveCount("host1")).isEqualTo(1);
        assertThat(manager.getActiveCount("host2")).isEqualTo(1);
    }
}
