package ssg.legoflow.service.passthrough;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A configurable TCP port redirector that listens on local ports and forwards
 * connections to remote hosts, creating transparent bidirectional TCP pipes.
 * <p>
 * Supports:
 * <ul>
 *   <li>Multiple routes (local port to remote host:port mappings)</li>
 *   <li>Data interception and transformation via {@link DataInterceptor}</li>
 *   <li>Lifecycle events via {@link PassThroughListener}</li>
 *   <li>Per-connection and global pause/resume</li>
 *   <li>I/O statistics tracking</li>
 * </ul>
 * <p>
 * Uses virtual threads for all I/O operations (one per connection direction),
 * with blocking I/O for efficient resource usage.
 *
 * @since 0.1.0
 */
public class PassThroughConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PassThroughConnection.class);
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final List<PassThroughConfig> routes = new ArrayList<>();
    private final CopyOnWriteArrayList<PassThroughListener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DataInterceptor> interceptors = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Integer, EstablishedConnection> connections = new ConcurrentHashMap<>();
    private final Set<SocketAddress> pausedAddresses = ConcurrentHashMap.newKeySet();
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();

    private int bufferSize = DEFAULT_BUFFER_SIZE;
    private volatile boolean running = false;
    private volatile boolean closed = false;
    private ExecutorService executor;
    private final List<ServerSocketChannel> serverChannels = new ArrayList<>();

    /**
     * Creates a new pass-through connection with no routes configured.
     */
    public PassThroughConnection() {
    }

    /**
     * Adds a route mapping a local listening port to a remote address.
     *
     * @param localPort     the local port to listen on
     * @param remoteAddress the remote host and port to forward to
     * @return this instance for method chaining
     * @throws IllegalArgumentException if remoteAddress is null
     * @throws IllegalStateException    if already running
     */
    public PassThroughConnection addRoute(int localPort, InetSocketAddress remoteAddress) {
        stateLock.readLock().lock();
        try {
            if (running) {
                throw new IllegalStateException("Cannot add routes while running");
            }
        } finally {
            stateLock.readLock().unlock();
        }
        routes.add(new PassThroughConfig(localPort, remoteAddress));
        return this;
    }

    /**
     * Sets the buffer size used for relay operations.
     *
     * @param bufferSize the buffer size in bytes, must be positive
     * @return this instance for method chaining
     * @throws IllegalArgumentException if bufferSize is not positive
     */
    public PassThroughConnection setBufferSize(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be positive: " + bufferSize);
        }
        this.bufferSize = bufferSize;
        return this;
    }

    /**
     * Registers an event listener.
     *
     * @param listener the listener to add, must not be null
     * @return this instance for method chaining
     */
    public PassThroughConnection addListener(PassThroughListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
        return this;
    }

    /**
     * Removes a previously registered event listener.
     *
     * @param listener the listener to remove
     * @return this instance for method chaining
     */
    public PassThroughConnection removeListener(PassThroughListener listener) {
        listeners.remove(listener);
        return this;
    }

    /**
     * Registers a data interceptor.
     *
     * @param interceptor the interceptor to add, must not be null
     * @return this instance for method chaining
     */
    public PassThroughConnection addInterceptor(DataInterceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor must not be null");
        }
        interceptors.add(interceptor);
        return this;
    }

    /**
     * Removes a previously registered data interceptor.
     *
     * @param interceptor the interceptor to remove
     * @return this instance for method chaining
     */
    public PassThroughConnection removeInterceptor(DataInterceptor interceptor) {
        interceptors.remove(interceptor);
        return this;
    }

    /**
     * Starts the pass-through connection, binding server sockets on all configured
     * local ports and accepting connections.
     *
     * @throws IOException           if a server socket cannot be bound
     * @throws IllegalStateException if already running or closed
     */
    public void start() throws IOException {
        stateLock.writeLock().lock();
        try {
            if (closed) {
                throw new IllegalStateException("Connection has been closed");
            }
            if (running) {
                throw new IllegalStateException("Already running");
            }
            if (routes.isEmpty()) {
                throw new IllegalStateException("No routes configured");
            }

            executor = Executors.newVirtualThreadPerTaskExecutor();
            Map<Integer, InetSocketAddress> bindings = new LinkedHashMap<>();

            for (PassThroughConfig config : routes) {
                ServerSocketChannel serverChannel = ServerSocketChannel.open();
                serverChannel.configureBlocking(true);
                serverChannel.setOption(java.net.StandardSocketOptions.SO_REUSEADDR, true);
                serverChannel.bind(new InetSocketAddress(config.localPort()));
                serverChannels.add(serverChannel);

                int actualPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
                bindings.put(actualPort, config.remoteAddress());
                LOG.info("Listening on port {} -> {}", actualPort, config.remoteAddress());
            }

            // Set running BEFORE submitting acceptors so they see running=true
            running = true;

            for (int i = 0; i < serverChannels.size(); i++) {
                ServerSocketChannel sc = serverChannels.get(i);
                PassThroughConfig cfg = routes.get(i);
                executor.submit(() -> acceptConnections(sc, cfg));
            }

            fireEvent(new PassThroughEvent.Started(this, Collections.unmodifiableMap(bindings), Instant.now()));
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * Stops the pass-through connection, closing all server sockets and established connections.
     */
    public void stop() {
        stateLock.writeLock().lock();
        try {
            if (!running) {
                return;
            }
            running = false;

            // Close server channels to stop accepting
            for (ServerSocketChannel sc : serverChannels) {
                try {
                    sc.close();
                } catch (IOException e) {
                    LOG.trace("Error closing server channel", e);
                }
            }
            serverChannels.clear();

            // Close all established connections
            for (EstablishedConnection conn : connections.values()) {
                conn.close();
            }
            connections.clear();

            // Shutdown executor
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }

            // Clear paused addresses
            pausedAddresses.clear();

            fireEvent(new PassThroughEvent.Stopped(this, Instant.now()));
            LOG.info("PassThroughConnection stopped");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * Closes this pass-through connection. Alias for {@link #stop()}.
     */
    @Override
    public void close() {
        closed = true;
        stop();
    }

    /**
     * Pauses all connections for the given duration, then automatically resumes.
     * This method blocks for the duration.
     *
     * @param duration the duration to pause for
     * @throws InterruptedException if the thread is interrupted while paused
     */
    public void pause(Duration duration) throws InterruptedException {
        LOG.info("Pausing all connections for {}", duration);
        for (EstablishedConnection conn : connections.values()) {
            conn.pause();
        }
        fireEvent(new PassThroughEvent.Paused(this, duration, Instant.now()));
        try {
            Thread.sleep(duration);
        } finally {
            for (EstablishedConnection conn : connections.values()) {
                conn.resume();
            }
        }
    }

    /**
     * Pauses all connections asynchronously: waits for {@code delay}, then pauses for
     * {@code pause} duration, then resumes. Runs on a virtual thread.
     *
     * @param delay the delay before pausing
     * @param pause the duration to pause for
     */
    public void pauseAsync(Duration delay, Duration pause) {
        Thread.ofVirtual().name("ptc-pause-async").start(() -> {
            try {
                Thread.sleep(delay);
                pause(pause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Pauses all connections associated with the given socket address.
     *
     * @param address the socket address to pause
     */
    public void pause(SocketAddress address) {
        pausedAddresses.add(address);
        for (EstablishedConnection conn : connections.values()) {
            if (matchesAddress(conn, address)) {
                conn.pause();
            }
        }
    }

    /**
     * Resumes all connections associated with the given socket address.
     *
     * @param address the socket address to resume
     */
    public void resume(SocketAddress address) {
        pausedAddresses.remove(address);
        for (EstablishedConnection conn : connections.values()) {
            if (matchesAddress(conn, address)) {
                conn.resume();
            }
        }
    }

    /**
     * Resumes all paused connections.
     */
    public void resumeAll() {
        pausedAddresses.clear();
        for (EstablishedConnection conn : connections.values()) {
            conn.resume();
        }
    }

    /**
     * Returns a snapshot list of all currently established connections.
     *
     * @return unmodifiable list of established connections
     */
    public List<EstablishedConnection> getConnections() {
        return List.copyOf(connections.values());
    }

    /**
     * Returns aggregate I/O statistics across all current connections.
     *
     * @return aggregate connection statistics
     */
    public ConnectionStatistics getStatistics() {
        ConnectionStatistics aggregate = new ConnectionStatistics(0, 0, 0, 0);
        for (EstablishedConnection conn : connections.values()) {
            aggregate = aggregate.add(conn.getStatistics());
        }
        return aggregate;
    }

    /**
     * Returns whether this pass-through connection is currently running.
     *
     * @return {@code true} if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns whether this pass-through connection has been closed.
     *
     * @return {@code true} if closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Finds an available port on the local machine.
     *
     * @return an available port number
     * @throws IOException if no port is available
     */
    public static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void acceptConnections(ServerSocketChannel serverChannel, PassThroughConfig config) {
        LOG.debug("Acceptor thread started for {}", config);
        try {
            while (running && serverChannel.isOpen()) {
                SocketChannel clientChannel = serverChannel.accept();
                if (clientChannel == null) {
                    continue;
                }
                clientChannel.configureBlocking(true);

                try {
                    SocketChannel remoteChannel = SocketChannel.open();
                    remoteChannel.configureBlocking(true);
                    remoteChannel.connect(config.remoteAddress());

                    EstablishedConnection conn = new EstablishedConnection(
                            clientChannel, remoteChannel, bufferSize);
                    connections.put(conn.getId(), conn);

                    // Check if this address is paused
                    for (SocketAddress pausedAddr : pausedAddresses) {
                        if (matchesAddress(conn, pausedAddr)) {
                            conn.pause();
                            break;
                        }
                    }

                    conn.startRelay(List.copyOf(interceptors), this::fireEvent);
                    fireEvent(new PassThroughEvent.ConnectionAccepted(conn, Instant.now()));
                    LOG.debug("New connection established: {}", conn);

                    // Start a cleanup thread that waits for the connection to close
                    Thread.ofVirtual().name("ptc-cleanup-" + conn.getId()).start(() -> {
                        while (!conn.isClosed()) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        ConnectionStatistics stats = conn.getStatistics();
                        connections.remove(conn.getId());
                        fireEvent(new PassThroughEvent.ConnectionClosed(conn, stats, Instant.now()));
                    });

                } catch (IOException e) {
                    LOG.debug("Failed to connect to remote {}: {}", config.remoteAddress(), e.getMessage());
                    fireEvent(new PassThroughEvent.Error(
                            this, "Failed to connect to remote: " + e.getMessage(), e, Instant.now()));
                    try {
                        clientChannel.close();
                    } catch (IOException ex) {
                        LOG.trace("Error closing client channel", ex);
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                LOG.debug("Acceptor error for {}: {}", config, e.getMessage());
                fireEvent(new PassThroughEvent.Error(
                        this, "Accept error: " + e.getMessage(), e, Instant.now()));
            }
        }
        LOG.debug("Acceptor thread exiting for {}", config);
    }

    private boolean matchesAddress(EstablishedConnection conn, SocketAddress address) {
        SocketAddress local = conn.getLocalAddress();
        SocketAddress remote = conn.getRemoteAddress();

        if (address instanceof InetSocketAddress target) {
            if (local instanceof InetSocketAddress localIsa) {
                if (localIsa.getPort() == target.getPort()) {
                    return true;
                }
            }
            if (remote instanceof InetSocketAddress remoteIsa) {
                if (remoteIsa.getPort() == target.getPort()
                        && remoteIsa.getAddress().equals(target.getAddress())) {
                    return true;
                }
            }
        }
        return address.equals(local) || address.equals(remote);
    }

    private void fireEvent(PassThroughEvent event) {
        for (PassThroughListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOG.warn("Listener threw exception for event {}: {}", event.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
