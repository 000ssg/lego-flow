package ssg.legoflow.service.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generic TCP server that accepts connections and delegates to a handler.
 *
 * <p>This eliminates the need for protocol-specific server implementations
 * that create ServerSocket directly.
 *
 * @since 0.3.0
 */
public final class TcpServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TcpServer.class);

    private final int port;
    private volatile ServerSocket serverSocket;
    private volatile ExecutorService executor;
    private volatile boolean running;
    private final ConnectionHandler handler;

    /**
     * Creates a new TCP server.
     *
     * @param port the port to listen on
     * @param handler the connection handler
     */
    public TcpServer(int port, ConnectionHandler handler) {
        this.port = port;
        this.handler = handler;
    }

    /**
     * Starts the server and begins accepting connections.
     */
    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Server already started");
        }
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        running = true;
        executor = Executors.newVirtualThreadPerTaskExecutor();
        LOG.info("TCP server started on port {}", port);
        executor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handler.handleConnection(clientSocket));
            } catch (IOException e) {
                if (running) {
                    LOG.warn("Accept error: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Stops the server.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.warn("Error closing server socket: {}", e.getMessage());
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        LOG.info("TCP server stopped");
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Returns the actual port the server is listening on.
     */
    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : port;
    }

    /**
     * Handler for incoming connections.
     */
    @FunctionalInterface
    public interface ConnectionHandler {
        void handleConnection(Socket socket);
    }
}
