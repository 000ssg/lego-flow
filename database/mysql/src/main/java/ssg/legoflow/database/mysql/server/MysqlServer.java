package ssg.legoflow.database.mysql.server;

import ssg.legoflow.database.mysql.auth.*;
import ssg.legoflow.database.mysql.protocol.*;
import ssg.legoflow.database.mysql.transport.MysqlTransport;
import ssg.legoflow.database.mysql.transport.MysqlTransportStreamAdapter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MySQL server that accepts connections via a byte-level {@link MysqlTransport}.
 *
 * <p>This is the transport-agnostic version of the server that can work with
 * in-memory transports for testing or pipeline transports for production.
 *
 * @since 0.3.0
 */
public class MysqlServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MysqlServer.class);

    private final String host;
    private final int port;
    private final ConcurrentHashMap<String, InMemoryDatabase> databases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, byte[]> userHashes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AuthPlugin> authPlugins = new ConcurrentHashMap<>();
    private final QueryExecutor queryExecutor;
    private final AtomicInteger connectionCounter = new AtomicInteger(1);
    private final AtomicInteger activeConnectionCount = new AtomicInteger(0);

    private volatile ServerSocketChannel serverChannel;
    private volatile ExecutorService executor;
    private volatile boolean running;
    private volatile long startTime;
    private String defaultAuthPlugin = MysqlNativePassword.NAME;

    /**
     * Creates a new MySQL server.
     *
     * @param host the bind host
     * @param port the bind port
     */
    public MysqlServer(String host, int port) {
        this.host = host;
        this.port = port;
        this.queryExecutor = new QueryExecutor(databases);

        authPlugins.put(MysqlNativePassword.NAME, MysqlNativePassword.INSTANCE);
        authPlugins.put(CachingSha2Password.NAME, CachingSha2Password.INSTANCE);
    }

    /**
     * Creates a new MySQL server on localhost with specified port.
     */
    public MysqlServer(int port) {
        this("localhost", port);
    }

    /**
     * Starts the server with non-blocking I/O via ServerSocketChannel.
     */
    public void start() throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(host, port));
        running = true;
        startTime = System.currentTimeMillis();
        executor = Executors.newVirtualThreadPerTaskExecutor();

        LOG.info("MySQL server started on {}:{} {}", host, actualPort(), serverChannel);

        executor.submit(() -> {
            while (running) {
                try {
                    SocketChannel clientChannel = serverChannel.accept();
                    if (clientChannel == null) {
                        Thread.sleep(10);
                        continue;
                    }
                    int connId = connectionCounter.getAndIncrement();
                    activeConnectionCount.incrementAndGet();
                    LOG.debug("New connection {} from {}", connId, clientChannel.getRemoteAddress());

                    executor.submit(() -> {
                        var adapter = new MysqlTransportStreamAdapter(new PipelineMysqlTransportWrapper(clientChannel));
                        var session = new ClientSession(connId, adapter.getInputStream(), adapter.getOutputStream(), this);
                        try {
                            session.run();
                        } catch (Exception e) {
                            LOG.debug("Connection {} error: {}", connId, e.getMessage());
                        } finally {
                            activeConnectionCount.decrementAndGet();
                        }
                    });
                } catch (IOException e) {
                    if (running) {
                        LOG.error("Accept error", e);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Handles a connection from a transport (for testing with InMemory transport).
     */
    public void handleConnection(MysqlTransport transport) {
        int connId = connectionCounter.getAndIncrement();
        activeConnectionCount.incrementAndGet();
        LOG.debug("New connection {} from transport", connId);

        var adapter = new MysqlTransportStreamAdapter(transport);
        var session = new ClientSession(connId, adapter.getInputStream(), adapter.getOutputStream(), this);
        try {
            session.run();
        } catch (Exception e) {
            LOG.debug("Connection {} error: {}", connId, e.getMessage());
        } finally {
            activeConnectionCount.decrementAndGet();
            try {
                transport.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Stops the server.
     */
    public void stop() {
        running = false;
        try {
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
        } catch (IOException ignored) {}
        if (executor != null) {
            executor.close();
        }
        LOG.info("MySQL server stopped");
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Returns the actual port the server is listening on.
     */
    public int actualPort() {
        if (serverChannel != null && serverChannel.isOpen()) {
            return serverChannel.socket().getLocalPort();
        }
        return port;
    }

    /**
     * Returns whether the server is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Creates a new database.
     */
    public InMemoryDatabase createDatabase(String name) {
        var db = new InMemoryDatabase(name);
        databases.put(name, db);
        return db;
    }

    /**
     * Returns a database by name.
     */
    public InMemoryDatabase getDatabase(String name) {
        return databases.get(name);
    }

    /**
     * Checks if a database exists.
     */
    public boolean hasDatabase(String name) {
        return databases.containsKey(name);
    }

    /**
     * Returns all databases.
     */
    public Map<String, InMemoryDatabase> allDatabases() {
        return Collections.unmodifiableMap(databases);
    }

    /**
     * Adds a user with password for authentication.
     */
    public void addUser(String username, String password) {
        var plugin = authPlugins.get(defaultAuthPlugin);
        if (plugin instanceof MysqlNativePassword) {
            userHashes.put(username, MysqlNativePassword.computeStoredHash(password));
        } else if (plugin instanceof CachingSha2Password) {
            userHashes.put(username, CachingSha2Password.computeStoredHash(password));
        }
    }

    /**
     * Adds a user with pre-computed hash.
     */
    public void addUserWithHash(String username, byte[] storedHash) {
        userHashes.put(username, storedHash);
    }

    /**
     * Returns the stored hash for a user.
     */
    public byte[] getStoredHash(String username) {
        return userHashes.get(username);
    }

    /**
     * Returns the auth plugin for a given name.
     */
    public AuthPlugin getAuthPlugin(String name) {
        return authPlugins.get(name);
    }

    /**
     * Returns the default auth plugin name.
     */
    public String defaultAuthPlugin() {
        return defaultAuthPlugin;
    }

    /**
     * Sets the default auth plugin.
     */
    public void setDefaultAuthPlugin(String pluginName) {
        this.defaultAuthPlugin = pluginName;
    }

    /**
     * Returns the query executor.
     */
    public QueryExecutor queryExecutor() {
        return queryExecutor;
    }

    /**
     * Returns the server uptime in seconds.
     */
    public long uptimeSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    /**
     * Returns the number of active connections.
     */
    public int activeConnections() {
        return activeConnectionCount.get();
    }

    /**
     * A simple MysqlTransport wrapper around a SocketChannel for use in handleConnection.
     */
    private static class PipelineMysqlTransportWrapper implements MysqlTransport {
        private final SocketChannel channel;
        private boolean open = true;

        PipelineMysqlTransportWrapper(SocketChannel channel) {
            this.channel = channel;
        }

        @Override
        public void send(ByteBuffer data) {
            try {
                channel.write(data);
            } catch (IOException e) {
                open = false;
            }
        }

        @Override
        public int receiveWithTimeout(ByteBuffer buffer, long timeout, java.util.concurrent.TimeUnit unit) {
            try {
                long nanos = unit.toNanos(timeout);
                long deadline = System.nanoTime() + nanos;
                while (System.nanoTime() < deadline) {
                    int n = channel.read(buffer);
                    if (n > 0) {
                        return n;
                    }
                    if (n == 0) {
                        Thread.yield();
                    }
                    if (!channel.isOpen()) {
                        open = false;
                        return -1;
                    }
                }
                return 0;
            } catch (IOException e) {
                open = false;
                return -1;
            }
        }

        @Override
        public void close() {
            open = false;
            try {
                channel.close();
            } catch (IOException ignored) {}
        }

        @Override
        public boolean isOpen() {
            return open && channel.isOpen();
        }
    }
}
