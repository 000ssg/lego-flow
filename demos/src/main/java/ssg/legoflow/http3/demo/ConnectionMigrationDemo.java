package ssg.legoflow.http3.demo;

import ssg.legoflow.http3.Http3Connection;
import ssg.legoflow.http3.config.Http3Config;
import ssg.legoflow.http3.quic.QuicConnection;
import ssg.legoflow.http3.quic.QuicSettings;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Demonstrates QUIC connection migration with HTTP/3.
 *
 * <p>QUIC connections can survive network path changes (e.g., switching
 * from Wi-Fi to cellular) without disrupting ongoing requests. This
 * demo establishes a connection and migrates it to a new address.</p>
 *
 * @since 1.0.0
 */
public class ConnectionMigrationDemo {

    private final Http3Config config;
    private volatile QuicConnection quicConnection;
    private volatile Http3Connection h3Connection;

    /**
     * Creates a new connection migration demo.
     *
     * @since 1.0.0
     */
    public ConnectionMigrationDemo() {
        this.config = Http3Config.defaults();
    }

    /**
     * Returns the configuration.
     *
     * @return the config
     * @since 1.0.0
     */
    public Http3Config config() {
        return config;
    }

    /**
     * Establishes an initial connection.
     *
     * @param address the initial remote address
     * @return the HTTP/3 connection
     * @since 1.0.0
     */
    public Http3Connection connect(SocketAddress address) {
        var quicSettings = QuicSettings.builder()
                .maxIdleTimeout(config.maxIdleTimeout())
                .initialMaxStreamsBidi(config.maxConcurrentStreams())
                .disableActiveMigration(false)
                .build();

        quicConnection = new QuicConnection(1L, false, quicSettings);
        quicConnection.connect(address);

        h3Connection = new Http3Connection(quicConnection);
        h3Connection.initialize();
        return h3Connection;
    }

    /**
     * Migrates the connection to a new address.
     *
     * @param newAddress the new remote address
     * @throws IllegalStateException if not connected or migration is disabled
     * @since 1.0.0
     */
    public void migrate(SocketAddress newAddress) {
        if (quicConnection == null || !quicConnection.isConnected()) {
            throw new IllegalStateException("Not connected");
        }
        quicConnection.migrate(newAddress);
    }

    /**
     * Returns the underlying QUIC connection.
     *
     * @return the QUIC connection
     * @since 1.0.0
     */
    public QuicConnection quicConnection() {
        return quicConnection;
    }

    /**
     * Returns the HTTP/3 connection.
     *
     * @return the HTTP/3 connection
     * @since 1.0.0
     */
    public Http3Connection h3Connection() {
        return h3Connection;
    }

    /**
     * Returns the current remote address.
     *
     * @return the remote address
     * @since 1.0.0
     */
    public SocketAddress currentRemoteAddress() {
        return quicConnection != null ? quicConnection.remoteAddress() : null;
    }
}
