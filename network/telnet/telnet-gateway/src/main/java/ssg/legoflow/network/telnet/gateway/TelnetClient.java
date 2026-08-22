package ssg.legoflow.network.telnet.gateway;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetConnection;
import ssg.legoflow.network.telnet.negotiation.GatewayNegotiator;
import ssg.legoflow.network.telnet.negotiation.OptionNegotiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A Telnet client that wraps a TCP socket with full RFC 854 telnet protocol support.
 *
 * <p>Manages connection lifecycle, negotiation state, and provides convenient methods
 * for sending data and commands. An optional reader thread processes incoming telnet
 * protocol automatically.
 *
 * <p>Usage:
 * <pre>{@code
 * try (TelnetClient client = TelnetClient.builder()
 *         .connect("localhost", 2223)
 *         .build()) {
 *     client.start();
 *     client.send("Hello, telnetd!\r\n");
 *     // ... wait for response via onData callback ...
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
public class TelnetClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TelnetClient.class);

    private final Socket socket;
    private final TelnetConnection connection;
    private final OptionNegotiator negotiator;
    private final java.util.List<Consumer<byte[]>> dataCallbacks;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private Thread readerThread;
    private volatile boolean running = false;

    private TelnetClient(Builder builder) throws IOException {
        this.socket = connectWithTimeout(
                builder.host, builder.port, builder.bindAddr, builder.connectTimeoutMs);
        this.socket.setSoTimeout(30000);
        this.socket.setKeepAlive(true);

        this.negotiator = builder.negotiator;
        this.dataCallbacks = new CopyOnWriteArrayList<>();

        final OptionNegotiator neg = this.negotiator;
        final java.net.Socket sock = this.socket;

        // Build TelnetConnection with callbacks for data and negotiation
        this.connection = TelnetConnection.builder()
                .writer(bytes -> {
                    try { sock.getOutputStream().write(bytes); }
                    catch (IOException e) { log.warn("Write error: {}", e.getMessage()); }
                })
                .onData(data -> {
                    for (Consumer<byte[]> cb : dataCallbacks) {
                        try { cb.accept(data); }
                        catch (Exception e) { log.warn("Data callback error: {}", e.getMessage()); }
                    }
                })
                .onNegotiate((cmd, opt) -> {
                    TelnetCommand response = neg.negotiate(cmd, opt);
                    if (!response.equals(cmd)) {
                        try { sendNegotiateDirectly(sock.getOutputStream(), response, opt); } catch (IOException e) { log.warn("Send negotiate error: {}", e.getMessage()); }
                    }
                })
                .build();
    }

    private static void sendNegotiateDirectly(java.io.OutputStream out, TelnetCommand cmd, int opt) {
        try {
            byte[] msg = {(byte)0xFF, (byte)cmd.code(), (byte)opt};
            out.write(msg);
            out.flush();
        } catch (IOException e) {
            log.warn("Send negotiate error: {}", e.getMessage());
        }
    }

    private static Socket connectWithTimeout(String host, int port, InetAddress bindAddr, int timeoutMs) throws IOException {
        Socket s = bindAddr != null ? new Socket() : new Socket();
        if (bindAddr != null) {
            s.bind(new java.net.InetSocketAddress(bindAddr, 0));
        }
        s.setSoTimeout(timeoutMs);
        s.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
        return s;
    }

    /**
     * Start a reader thread to process incoming data automatically.
     */
    public void start() {
        if (started.compareAndSet(false, true)) {
            running = true;
            readerThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                try {
                    InputStream in = socket.getInputStream();
                    while (running && !socket.isClosed()) {
                        int bytesRead = in.read(buffer);
                        if (bytesRead <= 0) break;
                        byte[] slice = new byte[bytesRead];
                        System.arraycopy(buffer, 0, slice, 0, bytesRead);
                        connection.feed(slice);
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout — not an error
                } catch (IOException e) {
                    if (running) log.warn("Reader error: {}", e.getMessage());
                } finally {
                    running = false;
                }
            }, "telnet-client-reader");
            readerThread.setDaemon(true);
            readerThread.start();
            log.debug("TelnetClient reader started");
        }
    }

    /** Stop the reader thread. */
    public void stop() {
        running = false;
        if (readerThread != null) readerThread.interrupt();
    }

    /** Send data to the remote telnet server. */
    public void send(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        connection.send(data);
    }

    /** Send a string (UTF-8 encoded) to the remote telnet server. */
    public void send(String text) {
        Objects.requireNonNull(text, "text must not be null");
        connection.send(text);
    }

    /** Send a telnet negotiation command. */
    public void sendNegotiate(TelnetCommand command, int option) {
        Objects.requireNonNull(command, "command must not be null");
        connection.sendNegotiate(command, option);
    }

    /** Register a callback for received application data. */
    public void onData(Consumer<byte[]> callback) {
        dataCallbacks.add(callback);
    }

    /** Close the client and release resources. */
    @Override
    public void close() {
        stop();
        try { socket.close(); } catch (IOException e) { log.warn("Failed to close socket: {}", e.getMessage()); }
        log.debug("TelnetClient closed");
    }

    /** Get the underlying socket. */
    public Socket socket() { return socket; }

    /** Get the option negotiator. */
    public OptionNegotiator negotiator() { return negotiator; }

    /** Check if the client is connected. */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    // --- Builder ---

    /** Builder for {@link TelnetClient}. */
    public static class Builder {
        private String host = "localhost";
        private int port = 23;
        private InetAddress bindAddr;
        private int connectTimeoutMs = 5000;
        private OptionNegotiator negotiator = new GatewayNegotiator();

        /** Set the server to connect to. */
        public Builder connect(String host, int port) {
            this.host = host;
            this.port = port;
            return this;
        }

        /** Set bind address for local socket. */
        public Builder bindAddress(InetAddress addr) {
            this.bindAddr = addr;
            return this;
        }

        /** Set connect timeout in ms (default 5000). */
        public Builder connectTimeout(int ms) {
            this.connectTimeoutMs = ms;
            return this;
        }

        /** Set custom negotiator (default GatewayNegotiator). */
        public Builder negotiator(OptionNegotiator negotiator) {
            this.negotiator = negotiator;
            return this;
        }

        /** Build the client. */
        public TelnetClient build() throws IOException {
            return new TelnetClient(this);
        }
    }

    /** Create a new builder. */
    public static Builder builder() { return new Builder(); }
}
