package ssg.legoflow.database.mysql.server;

import ssg.legoflow.database.mysql.auth.*;
import ssg.legoflow.database.mysql.protocol.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory MySQL server using virtual threads.
 *
 * <p>Accepts TCP connections and handles the MySQL wire protocol including
 * handshake, authentication, and command processing. Each client gets its
 * own virtual thread via {@link ClientSession}.
 *
 * <p>Example usage:
 * <pre>{@code
 * var server = new MysqlServer("localhost", 3306);
 * server.createDatabase("testdb");
 * server.addUser("root", "password123");
 * server.start();
 * // ... server runs until stopped
 * server.stop();
 * }</pre>
 *
 * @since 1.0.0
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

    private volatile ServerSocket serverSocket;
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

        // Register default auth plugins
        authPlugins.put(MysqlNativePassword.NAME, MysqlNativePassword.INSTANCE);
        authPlugins.put(CachingSha2Password.NAME, CachingSha2Password.INSTANCE);
    }

    /**
     * Creates a new MySQL server on localhost with specified port.
     *
     * @param port the bind port
     */
    public MysqlServer(int port) {
        this("localhost", port);
    }

    /**
     * Starts the server.
     *
     * @throws IOException if the server socket cannot be opened
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(host, port));
        running = true;
        startTime = System.currentTimeMillis();
        executor = Executors.newVirtualThreadPerTaskExecutor();

        LOG.info("MySQL server started on {}:{}", host, actualPort());

        executor.submit(() -> {
            while (running) {
                try {
                    var socket = serverSocket.accept();
                    int connId = connectionCounter.getAndIncrement();
                    activeConnectionCount.incrementAndGet();
                    LOG.debug("New connection {} from {}", connId, socket.getRemoteSocketAddress());

                    executor.submit(() -> {
                        try {
                            var session = new ClientSession(
                                    connId,
                                    socket.getInputStream(),
                                    socket.getOutputStream(),
                                    this
                            );
                            session.run();
                        } catch (IOException e) {
                            LOG.debug("Connection {} error: {}", connId, e.getMessage());
                        } finally {
                            activeConnectionCount.decrementAndGet();
                            try {
                                socket.close();
                            } catch (IOException ignored) {}
                        }
                    });
                } catch (IOException e) {
                    if (running) {
                        LOG.error("Accept error", e);
                    }
                }
            }
        });
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
     *
     * @return the local port
     */
    public int actualPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : port;
    }

    /**
     * Returns whether the server is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Creates a new database.
     *
     * @param name the database name
     * @return the created database
     */
    public InMemoryDatabase createDatabase(String name) {
        var db = new InMemoryDatabase(name);
        databases.put(name, db);
        return db;
    }

    /**
     * Returns a database by name.
     *
     * @param name the database name
     * @return the database, or null if not found
     */
    public InMemoryDatabase getDatabase(String name) {
        return databases.get(name);
    }

    /**
     * Checks if a database exists.
     *
     * @param name the database name
     * @return true if the database exists
     */
    public boolean hasDatabase(String name) {
        return databases.containsKey(name);
    }

    /**
     * Returns all databases.
     *
     * @return unmodifiable view of the databases map
     */
    public Map<String, InMemoryDatabase> allDatabases() {
        return Collections.unmodifiableMap(databases);
    }

    /**
     * Adds a user with password for authentication.
     *
     * @param username the username
     * @param password the password
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
     *
     * @param username the username
     * @param storedHash the pre-computed hash
     */
    public void addUserWithHash(String username, byte[] storedHash) {
        userHashes.put(username, storedHash);
    }

    /**
     * Returns the stored hash for a user.
     *
     * @param username the username
     * @return the stored hash, or null if not found
     */
    public byte[] getStoredHash(String username) {
        return userHashes.get(username);
    }

    /**
     * Returns the auth plugin for a given name.
     *
     * @param name the plugin name
     * @return the auth plugin, or null if not found
     */
    public AuthPlugin getAuthPlugin(String name) {
        return authPlugins.get(name);
    }

    /**
     * Returns the default auth plugin name.
     *
     * @return the default auth plugin name
     */
    public String defaultAuthPlugin() {
        return defaultAuthPlugin;
    }

    /**
     * Sets the default auth plugin.
     *
     * @param pluginName the plugin name
     */
    public void setDefaultAuthPlugin(String pluginName) {
        this.defaultAuthPlugin = pluginName;
    }

    /**
     * Returns the query executor.
     *
     * @return the query executor
     */
    public QueryExecutor queryExecutor() {
        return queryExecutor;
    }

    /**
     * Returns the server uptime in seconds.
     *
     * @return uptime in seconds
     */
    public long uptimeSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    /**
     * Returns the number of active connections.
     *
     * @return active connection count
     */
    public int activeConnections() {
        return activeConnectionCount.get();
    }
}
