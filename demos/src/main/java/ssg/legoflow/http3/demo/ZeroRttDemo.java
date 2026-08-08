package ssg.legoflow.http3.demo;

import ssg.legoflow.http3.Http3Connection;
import ssg.legoflow.http3.config.Http3Config;
import ssg.legoflow.http3.quic.QuicConnection;
import ssg.legoflow.http3.quic.QuicSettings;

import java.net.InetSocketAddress;

/**
 * Demonstrates 0-RTT connection resumption with HTTP/3.
 *
 * <p>After an initial handshake, subsequent connections can send
 * data immediately without waiting for the handshake to complete,
 * reducing latency for repeat connections.</p>
 *
 * @since 0.1.0
 */
public class ZeroRttDemo {

    private final Http3Config config;
    private volatile QuicConnection firstConnection;
    private volatile Http3Connection firstH3Connection;

    /**
     * Creates a new 0-RTT demo with 0-RTT enabled.
     *
     * @since 0.1.0
     */
    public ZeroRttDemo() {
        this.config = Http3Config.defaults().enable0Rtt(true);
    }

    /**
     * Returns the configuration.
     *
     * @return the config
     * @since 0.1.0
     */
    public Http3Config config() {
        return config;
    }

    /**
     * Performs the initial connection (full handshake).
     *
     * @return the HTTP/3 connection
     * @since 0.1.0
     */
    public Http3Connection initialConnect() {
        var quicSettings = QuicSettings.builder()
                .maxIdleTimeout(config.maxIdleTimeout())
                .initialMaxStreamsBidi(config.maxConcurrentStreams())
                .build();

        firstConnection = new QuicConnection(1L, false, quicSettings);
        firstConnection.connect(new InetSocketAddress(config.host(), config.port()));

        firstH3Connection = new Http3Connection(firstConnection);
        firstH3Connection.initialize();
        return firstH3Connection;
    }

    /**
     * Performs a 0-RTT resumption connection, simulating sending early data.
     *
     * @return the resumed HTTP/3 connection
     * @throws IllegalStateException if no initial connection was established
     * @since 0.1.0
     */
    public Http3Connection resumeConnection() {
        if (firstConnection == null) {
            throw new IllegalStateException("Must call initialConnect() first");
        }

        // Simulate 0-RTT: create a new connection that "resumes" the previous session
        var quicSettings = QuicSettings.builder()
                .maxIdleTimeout(config.maxIdleTimeout())
                .initialMaxStreamsBidi(config.maxConcurrentStreams())
                .build();

        var resumedConn = new QuicConnection(2L, false, quicSettings);
        resumedConn.connect(new InetSocketAddress(config.host(), config.port()));

        var h3Connection = new Http3Connection(resumedConn);
        h3Connection.initialize();
        return h3Connection;
    }

    /**
     * Returns the initial HTTP/3 connection.
     *
     * @return the first connection
     * @since 0.1.0
     */
    public Http3Connection firstConnection() {
        return firstH3Connection;
    }
}
