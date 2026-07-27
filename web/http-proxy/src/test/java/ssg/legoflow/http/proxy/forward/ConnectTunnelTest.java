package ssg.legoflow.http.proxy.forward;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectTunnelTest {

    @Test
    void testTunnelCreation() {
        var tunnel = createTunnel("example.com", 443);
        assertThat(tunnel.getTargetHost()).isEqualTo("example.com");
        assertThat(tunnel.getTargetPort()).isEqualTo(443);
        assertThat(tunnel.isActive()).isFalse();
    }

    @Test
    void testTunnelInitialCounters() {
        var tunnel = createTunnel("example.com", 443);
        assertThat(tunnel.getBytesRelayedToServer()).isEqualTo(0);
        assertThat(tunnel.getBytesRelayedToClient()).isEqualTo(0);
    }

    @Test
    void testTunnelClose() {
        var tunnel = createTunnel("example.com", 443);
        tunnel.close();
        assertThat(tunnel.isActive()).isFalse();
    }

    @Test
    void testBidirectionalRelay() throws Exception {
        // Set up pipes for bidirectional communication
        var clientToProxyOut = new PipedOutputStream();
        var clientToProxyIn = new PipedInputStream(clientToProxyOut);
        var proxyToClientOut = new ByteArrayOutputStream();

        var serverToProxyOut = new PipedOutputStream();
        var serverToProxyIn = new PipedInputStream(serverToProxyOut);
        var proxyToServerOut = new ByteArrayOutputStream();

        var tunnel = new ConnectTunnel("example.com", 443,
                clientToProxyIn, proxyToClientOut,
                serverToProxyIn, proxyToServerOut,
                Duration.ofSeconds(5));

        // Start tunnel in a virtual thread
        var tunnelThread = Thread.ofVirtual().start(tunnel::start);

        // Send data from client to server
        byte[] clientData = "Hello Server".getBytes(StandardCharsets.UTF_8);
        clientToProxyOut.write(clientData);
        clientToProxyOut.flush();
        Thread.sleep(100);

        // Send data from server to client
        byte[] serverData = "Hello Client".getBytes(StandardCharsets.UTF_8);
        serverToProxyOut.write(serverData);
        serverToProxyOut.flush();
        Thread.sleep(100);

        // Close streams to end the relay
        clientToProxyOut.close();
        serverToProxyOut.close();
        tunnelThread.join(2000);

        assertThat(proxyToServerOut.toByteArray()).isEqualTo(clientData);
        assertThat(proxyToClientOut.toByteArray()).isEqualTo(serverData);
    }

    @Test
    void testBytesRelayedCounters() throws Exception {
        var clientToProxyOut = new PipedOutputStream();
        var clientToProxyIn = new PipedInputStream(clientToProxyOut);
        var proxyToClientOut = new ByteArrayOutputStream();

        var serverToProxyOut = new PipedOutputStream();
        var serverToProxyIn = new PipedInputStream(serverToProxyOut);
        var proxyToServerOut = new ByteArrayOutputStream();

        var tunnel = new ConnectTunnel("example.com", 443,
                clientToProxyIn, proxyToClientOut,
                serverToProxyIn, proxyToServerOut,
                Duration.ofSeconds(5));

        var tunnelThread = Thread.ofVirtual().start(tunnel::start);

        byte[] data = "test data".getBytes(StandardCharsets.UTF_8);
        clientToProxyOut.write(data);
        clientToProxyOut.flush();
        Thread.sleep(100);

        clientToProxyOut.close();
        serverToProxyOut.close();
        tunnelThread.join(2000);

        assertThat(tunnel.getBytesRelayedToServer()).isEqualTo(data.length);
    }

    @Test
    void testTunnelActiveState() throws Exception {
        var clientToProxyOut = new PipedOutputStream();
        var clientToProxyIn = new PipedInputStream(clientToProxyOut);
        var proxyToClientOut = new ByteArrayOutputStream();

        var serverToProxyOut = new PipedOutputStream();
        var serverToProxyIn = new PipedInputStream(serverToProxyOut);
        var proxyToServerOut = new ByteArrayOutputStream();

        var tunnel = new ConnectTunnel("example.com", 443,
                clientToProxyIn, proxyToClientOut,
                serverToProxyIn, proxyToServerOut,
                Duration.ofSeconds(5));

        assertThat(tunnel.isActive()).isFalse();

        var tunnelThread = Thread.ofVirtual().start(tunnel::start);
        Thread.sleep(100);
        assertThat(tunnel.isActive()).isTrue();

        clientToProxyOut.close();
        serverToProxyOut.close();
        tunnelThread.join(2000);
        assertThat(tunnel.isActive()).isFalse();
    }

    @Test
    void testEmptyStreams() throws Exception {
        var clientIn = new ByteArrayInputStream(new byte[0]);
        var clientOut = new ByteArrayOutputStream();
        var serverIn = new ByteArrayInputStream(new byte[0]);
        var serverOut = new ByteArrayOutputStream();

        var tunnel = new ConnectTunnel("example.com", 443,
                clientIn, clientOut, serverIn, serverOut, Duration.ofSeconds(1));

        var tunnelThread = Thread.ofVirtual().start(tunnel::start);
        tunnelThread.join(2000);

        assertThat(tunnel.getBytesRelayedToServer()).isEqualTo(0);
        assertThat(tunnel.getBytesRelayedToClient()).isEqualTo(0);
    }

    @Test
    void testCloseStopsRelay() throws Exception {
        var clientToProxyOut = new PipedOutputStream();
        var clientToProxyIn = new PipedInputStream(clientToProxyOut);
        var proxyToClientOut = new ByteArrayOutputStream();

        var serverToProxyOut = new PipedOutputStream();
        var serverToProxyIn = new PipedInputStream(serverToProxyOut);
        var proxyToServerOut = new ByteArrayOutputStream();

        var tunnel = new ConnectTunnel("example.com", 443,
                clientToProxyIn, proxyToClientOut,
                serverToProxyIn, proxyToServerOut,
                Duration.ofSeconds(5));

        var tunnelThread = Thread.ofVirtual().start(tunnel::start);
        Thread.sleep(100);
        tunnel.close();

        clientToProxyOut.close();
        serverToProxyOut.close();
        tunnelThread.join(2000);
        assertThat(tunnel.isActive()).isFalse();
    }

    @Test
    void testTargetHostAndPort() {
        var tunnel = createTunnel("proxy.example.com", 8443);
        assertThat(tunnel.getTargetHost()).isEqualTo("proxy.example.com");
        assertThat(tunnel.getTargetPort()).isEqualTo(8443);
    }

    private ConnectTunnel createTunnel(String host, int port) {
        return new ConnectTunnel(host, port,
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(),
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(),
                Duration.ofSeconds(5));
    }
}
