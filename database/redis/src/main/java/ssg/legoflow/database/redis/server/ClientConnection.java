package ssg.legoflow.database.redis.server;

import ssg.legoflow.database.redis.protocol.RespVersion;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single client connection to the Redis server.
 *
 * <p>Tracks per-client state: selected database, RESP protocol version,
 * transaction state, pub/sub subscriptions, and client metadata.
 *
 * @since 1.0.0
 */
public final class ClientConnection {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private final long id;
    private final Socket socket;
    private final OutputStream output;
    private final RedisServer server;
    private volatile int selectedDb = 0;
    private volatile RespVersion respVersion = RespVersion.RESP2;
    private volatile String clientName;
    private final TransactionExecutor.TransactionState transactionState = new TransactionExecutor.TransactionState();
    private volatile boolean authenticated = false;
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
    private final Set<String> patternSubscriptions = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new client connection.
     *
     * @param socket the connected socket
     * @param server the owning server
     * @throws IOException if output stream cannot be obtained
     */
    public ClientConnection(Socket socket, RedisServer server) throws IOException {
        this.id = ID_GENERATOR.getAndIncrement();
        this.socket = socket;
        this.output = socket.getOutputStream();
        this.server = server;
    }

    /**
     * Returns the unique client ID.
     *
     * @return client ID
     */
    public long id() {
        return id;
    }

    /**
     * Returns the owning server.
     *
     * @return the server
     */
    public RedisServer server() {
        return server;
    }

    /**
     * Returns the database for the selected index.
     *
     * @return the currently selected database
     */
    public Database database() {
        return server.getDatabase(selectedDb);
    }

    /**
     * Returns the selected database index.
     *
     * @return database index
     */
    public int selectedDb() {
        return selectedDb;
    }

    /**
     * Selects a database by index.
     *
     * @param index the database index (0-15)
     */
    public void selectDb(int index) {
        this.selectedDb = index;
    }

    /**
     * Returns the RESP protocol version for this client.
     *
     * @return protocol version
     */
    public RespVersion respVersion() {
        return respVersion;
    }

    /**
     * Sets the RESP protocol version.
     *
     * @param version the new version
     */
    public void setRespVersion(RespVersion version) {
        this.respVersion = version;
    }

    /**
     * Returns the client name (set via CLIENT SETNAME).
     *
     * @return client name, or null
     */
    public String clientName() {
        return clientName;
    }

    /**
     * Sets the client name.
     *
     * @param name the name
     */
    public void setClientName(String name) {
        this.clientName = name;
    }

    /**
     * Returns the transaction state.
     *
     * @return transaction state
     */
    public TransactionExecutor.TransactionState transactionState() {
        return transactionState;
    }

    /**
     * Returns the set of channel subscriptions.
     *
     * @return subscriptions
     */
    public Set<String> subscriptions() {
        return subscriptions;
    }

    /**
     * Returns the set of pattern subscriptions.
     *
     * @return pattern subscriptions
     */
    public Set<String> patternSubscriptions() {
        return patternSubscriptions;
    }

    /**
     * Returns whether this client is authenticated.
     *
     * @return true if authenticated
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Sets the authentication state of this client.
     *
     * @param authenticated true if authenticated
     */
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    /**
     * Returns whether this client is in pub/sub mode.
     *
     * @return true if subscribed to any channel or pattern
     */
    public boolean isInPubSubMode() {
        return !subscriptions.isEmpty() || !patternSubscriptions.isEmpty();
    }

    /**
     * Writes raw bytes to the client.
     *
     * @param data the bytes to write
     */
    public void writeRaw(byte[] data) {
        try {
            synchronized (output) {
                output.write(data);
                output.flush();
            }
        } catch (IOException e) {
            // Client disconnected
        }
    }

    /**
     * Returns the socket address as a string.
     *
     * @return address string
     */
    public String address() {
        return socket.getRemoteSocketAddress() != null
                ? socket.getRemoteSocketAddress().toString()
                : "unknown";
    }

    /**
     * Closes the client connection.
     */
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * Returns the pub/sub manager from the server.
     *
     * @return pub/sub manager
     */
    public PubSubManager pubSubManager() {
        return server.pubSubManager();
    }

    /**
     * Returns the transaction executor from the server.
     *
     * @return transaction executor
     */
    public TransactionExecutor transactionExecutor() {
        return server.transactionExecutor();
    }
}
