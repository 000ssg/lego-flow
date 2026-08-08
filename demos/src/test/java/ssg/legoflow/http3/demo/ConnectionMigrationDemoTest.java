package ssg.legoflow.http3.demo;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.*;

class ConnectionMigrationDemoTest {

    @Test
    void testConnectAndMigrate() {
        // Given
        var demo = new ConnectionMigrationDemo();
        var originalAddress = new InetSocketAddress("192.168.1.1", 443);

        // When: connect
        var connection = demo.connect(originalAddress);

        // Then
        assertThat(connection).isNotNull();
        assertThat(connection.isConnected()).isTrue();
        assertThat(demo.currentRemoteAddress()).isEqualTo(originalAddress);
    }

    @Test
    void testMigrateToNewAddress() {
        // Given
        var demo = new ConnectionMigrationDemo();
        var originalAddress = new InetSocketAddress("192.168.1.1", 443);
        var newAddress = new InetSocketAddress("10.0.0.1", 443);
        demo.connect(originalAddress);

        // When
        demo.migrate(newAddress);

        // Then
        assertThat(demo.currentRemoteAddress()).isEqualTo(newAddress);
        assertThat(demo.h3Connection().isConnected()).isTrue();
    }

    @Test
    void testMigrateWithoutConnectThrows() {
        // Given
        var demo = new ConnectionMigrationDemo();

        // When/Then
        assertThatThrownBy(() -> demo.migrate(new InetSocketAddress("10.0.0.1", 443)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testQuicConnectionAccessible() {
        // Given
        var demo = new ConnectionMigrationDemo();
        demo.connect(new InetSocketAddress("localhost", 443));

        // When/Then
        assertThat(demo.quicConnection()).isNotNull();
        assertThat(demo.quicConnection().isConnected()).isTrue();
    }

    @Test
    void testConnectionSurvivesMigration() {
        // Given
        var demo = new ConnectionMigrationDemo();
        demo.connect(new InetSocketAddress("192.168.1.1", 443));

        // When: migrate multiple times
        demo.migrate(new InetSocketAddress("10.0.0.1", 443));
        demo.migrate(new InetSocketAddress("172.16.0.1", 443));

        // Then: connection still active
        assertThat(demo.h3Connection().isConnected()).isTrue();
        assertThat(demo.currentRemoteAddress())
                .isEqualTo(new InetSocketAddress("172.16.0.1", 443));
    }
}
