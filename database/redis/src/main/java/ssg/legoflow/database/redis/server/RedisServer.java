package ssg.legoflow.database.redis.server;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespCodec;
import ssg.legoflow.database.redis.protocol.RespParser;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Redis-compatible TCP server using virtual threads.
 *
 * <p>Accepts client connections and processes RESP2/RESP3 commands.
 * Each client connection is handled on its own virtual thread.
 * Supports pipelining (multiple commands per TCP read).
 *
 * @since 0.1.0
 */
public final class RedisServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RedisServer.class);
    private static final Set<String> TRANSACTION_BYPASS = Set.of("EXEC", "DISCARD", "MULTI", "WATCH");
    private static final Set<String> AUTH_BYPASS = Set.of("AUTH", "PING", "QUIT");

    private final CommandRegistry registry = new CommandRegistry();
    private final Database[] databases = new Database[16];
    private final PubSubManager pubSubManager = new PubSubManager();
    private final TransactionExecutor transactionExecutor = new TransactionExecutor();
    private final Set<ClientConnection> clients = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final String password;

    private volatile ServerSocket serverSocket;
    private volatile int boundPort;

    /**
     * Creates a new Redis server without password authentication.
     */
    public RedisServer() {
        this(null);
    }

    /**
     * Creates a new Redis server with optional password authentication.
     *
     * @param password the required password, or null for no authentication
     */
    public RedisServer(String password) {
        this.password = password;
        for (int i = 0; i < 16; i++) {
            databases[i] = new Database(i);
        }
        registerCommands();
    }

    private void registerCommands() {
        StringCommands.register(registry);
        ListCommands.register(registry);
        SetCommands.register(registry);
        SortedSetCommands.register(registry);
        HashCommands.register(registry);
        KeyCommands.register(registry);
        PubSubCommands.register(registry);
        StreamCommands.register(registry);
        TransactionCommands.register(registry);
        ServerCommands.register(registry);

        // AUTH command
        registry.register("AUTH", (args, client) -> {
            if (password == null) {
                return new RespType.Error("ERR", "Client sent AUTH, but no password is set. Did you mean ACL SETUSER with >password?");
            }
            if (args.size() < 2) {
                return new RespType.Error("ERR", "wrong number of arguments for 'auth' command");
            }
            String attempt = args.getString(1);
            if (password.equals(attempt)) {
                client.setAuthenticated(true);
                return new RespType.SimpleString("OK");
            } else {
                return new RespType.Error("WRONGPASS", "invalid username-password pair or user is disabled.");
            }
        });

        HyperLogLogCommands.register(registry);
        GeoCommands.register(registry);
    }

    /**
     * Starts the server on the given port.
     *
     * @param port the port to bind (0 for ephemeral)
     * @throws IOException if binding fails
     */
    public void start(int port) throws IOException {
        start("127.0.0.1", port);
    }

    /**
     * Starts the server on the given host and port.
     *
     * @param host the bind address
     * @param port the port to bind (0 for ephemeral)
     * @throws IOException if binding fails
     */
    public void start(String host, int port) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(host, port));
        boundPort = serverSocket.getLocalPort();
        running.set(true);

        LOG.info("Redis server started on {}:{}", host, boundPort);

        executor.submit(() -> {
            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executor.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running.get()) {
                        LOG.error("Error accepting connection", e);
                    }
                }
            }
        });
    }

    private void handleClient(Socket socket) {
        ClientConnection client = null;
        try {
            client = new ClientConnection(socket, this);
            clients.add(client);
            LOG.debug("Client connected: {}", client.address());

            RespParser parser = new RespParser(socket.getInputStream());

            while (running.get() && !socket.isClosed()) {
                RespType message = parser.parse();
                if (message == null) break; // EOF

                // Handle command
                if (message instanceof RespType.Array arr && arr.elements() != null) {
                    CommandArgs args = new CommandArgs(arr.elements());
                    RespType response = executeCommand(args, client);
                    if (response != null) {
                        client.writeRaw(RespCodec.encode(response));
                    }

                    // Handle QUIT
                    if ("QUIT".equals(args.commandName())) break;
                }
            }
        } catch (IOException e) {
            LOG.debug("Client disconnected: {}", e.getMessage());
        } finally {
            if (client != null) {
                clients.remove(client);
                pubSubManager.removeClient(client);
                client.close();
            }
        }
    }

    private RespType executeCommand(CommandArgs args, ClientConnection client) {
        String name = args.commandName();

        // Auth check: reject commands from unauthenticated clients when password is set
        if (password != null && !client.isAuthenticated() && !AUTH_BYPASS.contains(name)) {
            return new RespType.Error("NOAUTH", "Authentication required.");
        }

        // If in MULTI transaction, queue commands (except EXEC, DISCARD, MULTI, WATCH)
        if (client.transactionState().isInTransaction() && !TRANSACTION_BYPASS.contains(name)) {
            client.transactionState().enqueue(args);
            return TransactionCommands.queued();
        }

        try {
            return registry.dispatch(args, client);
        } catch (Exception e) {
            LOG.error("Error executing command: {}", name, e);
            return new RespType.Error("ERR", e.getMessage());
        }
    }

    /**
     * Returns the configured password, or null if no authentication is required.
     *
     * @return password or null
     */
    public String password() {
        return password;
    }

    /**
     * Returns the bound port.
     *
     * @return port number
     */
    public int port() {
        return boundPort;
    }

    /**
     * Returns the database at the given index.
     *
     * @param index database index (0-15)
     * @return the database
     */
    public Database getDatabase(int index) {
        return databases[index];
    }

    /**
     * Returns the command registry.
     *
     * @return command registry
     */
    public CommandRegistry commandRegistry() {
        return registry;
    }

    /**
     * Returns the pub/sub manager.
     *
     * @return pub/sub manager
     */
    public PubSubManager pubSubManager() {
        return pubSubManager;
    }

    /**
     * Returns the transaction executor.
     *
     * @return transaction executor
     */
    public TransactionExecutor transactionExecutor() {
        return transactionExecutor;
    }

    /**
     * Returns the number of connected clients.
     *
     * @return client count
     */
    public int connectedClients() {
        return clients.size();
    }

    /**
     * Returns whether the server is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.debug("Error closing server socket", e);
        }
        for (ClientConnection client : clients) {
            client.close();
        }
        clients.clear();
        executor.shutdownNow();
        LOG.info("Redis server stopped");
    }
}
