package ssg.legoflow.network.telnet.gateway;

import ssg.legoflow.network.telnet.negotiation.GatewayNegotiator;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.base.io.TerminalFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A simple Telnet server that accepts connections and bridges them to VT100 terminals
 * via {@link TelnetGateway}.
 *
 * <p>Usage:
 * <pre>{@code
 * try (TelnetServer server = TelnetServer.builder().port(2323).build()) {
 *     server.start();
 *     System.out.println("Listening on " + server.getPort());
 *     // ... run tests ...
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
public class TelnetServer implements Runnable, Closeable {

    private static final Logger log = LoggerFactory.getLogger(TelnetServer.class);

    private final int port;
    private final int backlog;
    private final InetAddress bindAddress;
    private final int sessionTimeoutMs;
    private final String terminalType;
    private final java.util.List<Consumer<TelnetSession>> connectionHandlers;
    private final java.util.List<Consumer<TelnetSession>> sessionListeners;

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private Thread acceptorThread;
    private volatile int actualPort;

    /** A single telnet session managing a socket connection. */
    public static class TelnetSession {
        private final Socket socket;
        private final TelnetGateway gateway;
        private final Terminal terminal;
        private final AtomicBoolean active;

        public TelnetSession(Socket socket, TelnetGateway gateway, Terminal terminal) {
            this.socket = Objects.requireNonNull(socket);
            this.gateway = Objects.requireNonNull(gateway);
            this.terminal = Objects.requireNonNull(terminal);
            this.active = new AtomicBoolean(true);
        }

        public Socket socket() { return socket; }
        public TelnetGateway gateway() { return gateway; }
        public Terminal terminal() { return terminal; }
        public boolean isActive() { return active.get(); }

        /** Send data to this session's socket. */
        public void send(byte[] data) {
            try {
                socket.getOutputStream().write(data);
                socket.getOutputStream().flush();
            } catch (IOException e) {
                log.warn("Failed to send to session: {}", e.getMessage());
            }
        }

        /** Send a string to this session's socket. */
        public void send(String text) {
            send(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        /** Close the session. */
        public void close() {
            active.set(false);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private TelnetServer(Builder builder) {
        this.port = builder.port;
        this.backlog = builder.backlog;
        this.bindAddress = builder.bindAddress;
        this.sessionTimeoutMs = builder.sessionTimeoutMs;
        this.terminalType = builder.terminalType;
        this.connectionHandlers = builder.connectionHandlers;
        this.sessionListeners = builder.sessionListeners;
    }

    /** Start the server in a background thread. */
    public void start() throws IOException {
        if (running) throw new IllegalStateException("Server already running");
        serverSocket = bindAddress != null
                ? new ServerSocket(port, backlog, bindAddress)
                : new ServerSocket(port, backlog);
        serverSocket.setSoTimeout(1000); // Check for shutdown every second
        actualPort = serverSocket.getLocalPort();
        running = true;
        acceptorThread = new Thread(this, "telnet-server-acceptor");
        acceptorThread.start();
        log.info("TelnetServer started on {}:{}",
                bindAddress != null ? bindAddress.getHostAddress() : "0.0.0.0", actualPort);
    }

    /** Stop the server and close all connections. */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.warn("Failed to close server socket: {}", e.getMessage());
        }
        if (acceptorThread != null) {
            acceptorThread.interrupt();
        }
        log.info("TelnetServer stopped");
    }

    @Override
    public void close() { stop(); }

    /** The port the server is listening on. */
    public int getPort() { return actualPort; }

    /** Check if the server is running. */
    public boolean isRunning() { return running; }

    /** Process a single client connection. Called by the acceptor thread. */
    private void handleConnection(Socket socket) {
        try {
            
            
            socket.setSoTimeout(sessionTimeoutMs);
            socket.setKeepAlive(true);
            

            InputStream in = socket.getInputStream();
            java.io.OutputStream out = socket.getOutputStream();

            
            Terminal terminal = TerminalFactory.create(terminalType);
            
            TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                    .writer(bytes -> {
                        try { out.write(bytes); }
                        catch (IOException e) { log.warn("Write error: {}", e.getMessage()); }
                    })
                    .negotiator(new GatewayNegotiator())
                    .build();

            
            TelnetSession session = new TelnetSession(socket, gateway, terminal);
            

            
            for (Consumer<TelnetSession> handler : connectionHandlers) {
                try { handler.accept(session); }
                catch (Exception e) { log.warn("Connection handler error: {}", e.getMessage()); }
            }

            
            for (Consumer<TelnetSession> listener : sessionListeners) {
                try { listener.accept(session); }
                catch (Exception e) { log.warn("Session listener error: {}", e.getMessage()); }
            }

            // Reader thread per session
            final AtomicBoolean sessionActive = session.active;
            final java.net.Socket sessionSocket = socket;
            Thread readerThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                try {
                    while (sessionActive.get() && !sessionSocket.isClosed()) {
                        int bytesRead = in.read(buffer);
                        if (bytesRead <= 0) break;
                        byte[] slice = new byte[bytesRead];
                        System.arraycopy(buffer, 0, slice, 0, bytesRead);
                        gateway.feed(slice);
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout — not an error, just no data yet
                } catch (IOException e) {
                    if (sessionActive.get()) log.warn("Reader error for {}: {}", socket.getRemoteSocketAddress(), e.getMessage());
                } finally {
                    sessionActive.set(false);
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }, "telnet-session-reader-" + System.identityHashCode(socket));
            readerThread.setDaemon(true);
            readerThread.start();

            log.debug("Session opened for {}", socket.getRemoteSocketAddress());
        } catch (Exception e) {
            log.warn("Failed to handle connection: {}", e.getMessage());
        }
    }

    @Override
    public void run() {
        
                while (running) {
            try {
                
                Socket socket = serverSocket.accept();
                
                handleConnection(socket);
                
            } catch (SocketTimeoutException e) {
                // Accept timeout — just retry
            } catch (IOException e) {
                if (running) if (running) log.warn("Accept error: {}", e.getMessage());
            }
        }
        
    }

    // --- Builder ---

    /** Builder for {@link TelnetServer}. */
    public static class Builder {
        private int port = 23;
        private int backlog = 50;
        private InetAddress bindAddress;
        private int sessionTimeoutMs = 60000;
        private String terminalType = "vt100";
        private final java.util.List<Consumer<TelnetSession>> connectionHandlers = new CopyOnWriteArrayList<>();
        private final java.util.List<Consumer<TelnetSession>> sessionListeners = new CopyOnWriteArrayList<>();

        public Builder port(int port) { this.port = port; return this; }
        public Builder backlog(int backlog) { this.backlog = backlog; return this; }

        /** Bind to a specific address (default 0.0.0.0). */
        public Builder bindAddress(InetAddress address) { this.bindAddress = address; return this; }

        /** Set session read timeout in ms (default 60000). */
        public Builder sessionTimeoutMs(int timeoutMs) { this.sessionTimeoutMs = timeoutMs; return this; }

        /** Set terminal type for sessions (default vt100). */
        public Builder terminalType(String type) { this.terminalType = type; return this; }

        /** Add a handler called for each new connection. */
        public Builder onConnection(Consumer<TelnetSession> handler) { this.connectionHandlers.add(handler); return this; }

        /** Add a listener notified for each session. */
        public Builder onSession(Consumer<TelnetSession> listener) { this.sessionListeners.add(listener); return this; }

        public TelnetServer build() { return new TelnetServer(this); }
    }

    /** Create a new builder. */
    public static Builder builder() { return new Builder(); }
}
