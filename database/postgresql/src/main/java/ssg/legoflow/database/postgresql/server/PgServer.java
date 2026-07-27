package ssg.legoflow.database.postgresql.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.database.postgresql.auth.PgAuthenticator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PostgreSQL v3 wire protocol server using virtual threads.
 *
 * <p>Listens for client connections and spawns a {@link ClientSession}
 * for each one using virtual threads.
 *
 * @since 1.0.0
 */
public final class PgServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PgServer.class);

    private final InMemoryDatabase database;
    private final QueryExecutor executor;
    private final NotificationManager notifications;
    private final PgAuthenticator authenticator;
    private final AtomicInteger processIdCounter = new AtomicInteger(1000);
    private final AtomicInteger secretKeyCounter = new AtomicInteger(42);
    private final CopyOnWriteArrayList<ClientSession> sessions = new CopyOnWriteArrayList<>();

    private ServerSocket serverSocket;
    private volatile boolean running;
    private Thread acceptThread;
    private int port;

    /**
     * Creates a new PostgreSQL server with no authentication.
     */
    public PgServer() {
        this(null);
    }

    /**
     * Creates a new PostgreSQL server with the given authenticator.
     *
     * @param authenticator the authenticator, or null for trust authentication
     */
    public PgServer(PgAuthenticator authenticator) {
        this.database = new InMemoryDatabase();
        this.executor = new QueryExecutor(database);
        this.notifications = new NotificationManager();
        this.authenticator = authenticator;
    }

    /**
     * Returns the underlying database.
     *
     * @return the in-memory database
     */
    public InMemoryDatabase database() {
        return database;
    }

    /**
     * Returns the notification manager.
     *
     * @return the notification manager
     */
    public NotificationManager notifications() {
        return notifications;
    }

    /**
     * Starts the server on the given port.
     *
     * @param port the port to listen on (0 for ephemeral)
     * @throws IOException if the server cannot be started
     */
    public void start(int port) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress("127.0.0.1", port));
        this.port = serverSocket.getLocalPort();
        running = true;

        acceptThread = Thread.ofVirtual().name("pg-server-accept").start(this::acceptLoop);
        LOG.info("PostgreSQL server started on port {}", this.port);
    }

    /**
     * Returns the port the server is listening on.
     *
     * @return the port
     */
    public int port() {
        return port;
    }

    /**
     * Returns the number of active client sessions.
     *
     * @return the session count
     */
    public int sessionCount() {
        return sessions.size();
    }

    @Override
    public void close() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            // ignore
        }
        for (ClientSession session : sessions) {
            session.stop();
        }
        sessions.clear();
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
        LOG.info("PostgreSQL server stopped");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                int pid = processIdCounter.incrementAndGet();
                int secret = secretKeyCounter.incrementAndGet();
                var session = new ClientSession(clientSocket, executor, notifications,
                        authenticator, pid, secret);
                sessions.add(session);
                Thread.ofVirtual().name("pg-client-" + pid).start(() -> {
                    try {
                        session.run();
                    } finally {
                        sessions.remove(session);
                    }
                });
            } catch (IOException e) {
                if (running) {
                    LOG.debug("Accept error: {}", e.getMessage());
                }
            }
        }
    }
}
