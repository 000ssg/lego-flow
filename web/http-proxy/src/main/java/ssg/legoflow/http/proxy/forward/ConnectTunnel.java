package ssg.legoflow.http.proxy.forward;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
/**
 * HTTPS CONNECT tunnel implementation providing bidirectional byte relay.
 *
 * <p>When a client issues a CONNECT request, the proxy establishes a TCP connection
 * to the target host and then relays bytes in both directions. This allows
 * encrypted (HTTPS) traffic to pass through the proxy without the proxy
 * being able to inspect the content.</p>
 *
 * <p>Uses virtual threads for the two relay directions.</p>
 *
 * @since 0.1.0
 */
public class ConnectTunnel implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectTunnel.class);
    private static final int BUFFER_SIZE = 8192;

    private final String targetHost;
    private final int targetPort;
    private final InputStream clientInput;
    private final OutputStream clientOutput;
    private final InputStream serverInput;
    private final OutputStream serverOutput;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicLong bytesRelayedToServer = new AtomicLong(0);
    private final AtomicLong bytesRelayedToClient = new AtomicLong(0);
    private final Duration idleTimeout;

    /**
     * Creates a new CONNECT tunnel.
     *
     * @param targetHost the target hostname
     * @param targetPort the target port
     * @param clientInput the input stream from the client
     * @param clientOutput the output stream to the client
     * @param serverInput the input stream from the upstream server
     * @param serverOutput the output stream to the upstream server
     * @param idleTimeout the idle timeout duration
     * @since 0.1.0
     */
    public ConnectTunnel(String targetHost, int targetPort,
                         InputStream clientInput, OutputStream clientOutput,
                         InputStream serverInput, OutputStream serverOutput,
                         Duration idleTimeout) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.clientInput = clientInput;
        this.clientOutput = clientOutput;
        this.serverInput = serverInput;
        this.serverOutput = serverOutput;
        this.idleTimeout = idleTimeout;
    }

    /**
     * Starts the bidirectional byte relay. This method blocks until the tunnel
     * is closed or an error occurs.
     *
     * @since 0.1.0
     */
    public void start() {
        active.set(true);
        LOG.debug("Starting CONNECT tunnel to {}:{}", targetHost, targetPort);

        Thread clientToServer = Thread.ofVirtual()
                .name("tunnel-c2s-" + targetHost + ":" + targetPort)
                .start(() -> relay(clientInput, serverOutput, bytesRelayedToServer, "client->server"));

        Thread serverToClient = Thread.ofVirtual()
                .name("tunnel-s2c-" + targetHost + ":" + targetPort)
                .start(() -> relay(serverInput, clientOutput, bytesRelayedToClient, "server->client"));

        try {
            clientToServer.join();
            serverToClient.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.debug("Tunnel interrupted for {}:{}", targetHost, targetPort);
        } finally {
            active.set(false);
        }
    }

    private void relay(InputStream in, OutputStream out, AtomicLong counter, String direction) {
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            int bytesRead;
            while (active.get() && (bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
                counter.addAndGet(bytesRead);
            }
        } catch (IOException e) {
            if (active.get()) {
                LOG.debug("Tunnel {} relay ended: {}", direction, e.getMessage());
            }
        } finally {
            active.set(false);
        }
    }

    /**
     * Returns whether the tunnel is currently active.
     *
     * @return true if the tunnel is active
     * @since 0.1.0
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * Returns the number of bytes relayed from client to server.
     *
     * @return bytes relayed to server
     * @since 0.1.0
     */
    public long getBytesRelayedToServer() {
        return bytesRelayedToServer.get();
    }

    /**
     * Returns the number of bytes relayed from server to client.
     *
     * @return bytes relayed to client
     * @since 0.1.0
     */
    public long getBytesRelayedToClient() {
        return bytesRelayedToClient.get();
    }

    /**
     * Returns the target host.
     *
     * @return the target host
     * @since 0.1.0
     */
    public String getTargetHost() {
        return targetHost;
    }

    /**
     * Returns the target port.
     *
     * @return the target port
     * @since 0.1.0
     */
    public int getTargetPort() {
        return targetPort;
    }

    /**
     * Closes the tunnel, stopping all relay operations.
     *
     * @since 0.1.0
     */
    @Override
    public void close() {
        active.set(false);
        LOG.debug("Closing CONNECT tunnel to {}:{} (relayed: {} to server, {} to client)",
                targetHost, targetPort, bytesRelayedToServer.get(), bytesRelayedToClient.get());
    }
}
