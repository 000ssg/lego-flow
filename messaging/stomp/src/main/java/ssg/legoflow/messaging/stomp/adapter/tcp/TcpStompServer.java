package ssg.legoflow.messaging.stomp.adapter.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.messaging.stomp.core.StompBroker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * TCP server accepting STOMP connections and dispatching them to a broker.
 *
 * <p>Listens on a configurable port and creates a {@link TcpStompTransport}
 * for each accepted connection, then hands it off to the {@link StompBroker}.
 * Uses virtual threads for connection handling.
 *
 * @since 1.0.0
 */
public class TcpStompServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TcpStompServer.class);

    private final StompBroker broker;
    private final int port;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;
    private volatile Thread acceptThread;

    /**
     * Creates a new TCP STOMP server.
     *
     * @param broker the STOMP broker to dispatch connections to
     * @param port   the port to listen on (0 for any available port)
     */
    public TcpStompServer(StompBroker broker, int port) {
        this.broker = broker;
        this.port = port;
    }

    /**
     * Starts the server, binding to the configured port.
     *
     * @throws IOException if the server socket cannot be created
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        running = true;

        acceptThread = Thread.startVirtualThread(this::acceptLoop);
        LOG.info("STOMP TCP server started on port {}", getPort());
    }

    /**
     * Returns the actual port the server is listening on.
     * Useful when port 0 was specified (auto-assign).
     *
     * @return the local port
     */
    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : port;
    }

    /**
     * Returns whether the server is running.
     *
     * @return {@code true} if running
     */
    public boolean isRunning() {
        return running;
    }

    private void acceptLoop() {
        while (running) {
            try {
                var socket = serverSocket.accept();
                LOG.debug("Accepted connection from {}", socket.getRemoteSocketAddress());
                var transport = new TcpStompTransport(socket);
                broker.accept(transport);
            } catch (IOException e) {
                if (running) {
                    LOG.debug("Accept error: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public void close() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOG.debug("Error closing server socket: {}", e.getMessage());
            }
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
    }
}
