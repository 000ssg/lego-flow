package ssg.legoflow.messaging.stomp.adapter.tcp;

import ssg.legoflow.messaging.stomp.core.StompClient;
import ssg.legoflow.messaging.stomp.core.StompFrame;
import java.io.IOException;
import java.net.Socket;
/**
 * TCP client connecting to a STOMP broker.
 *
 * <p>Creates a TCP socket to the specified host and port, wraps it in a
 * {@link TcpStompTransport}, and provides a {@link StompClient} for
 * protocol operations.
 *
 * @since 0.1.0
 */
public class TcpStompClient implements AutoCloseable {

    private final String host;
    private final int port;
    private volatile Socket socket;
    private volatile TcpStompTransport transport;
    private volatile StompClient client;

    /**
     * Creates a new TCP STOMP client.
     *
     * @param host the broker host
     * @param port the broker port
     */
    public TcpStompClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Connects to the broker over TCP and performs the STOMP handshake.
     *
     * @param virtualHost the STOMP virtual host
     * @param login       the login (may be null)
     * @param passcode    the passcode (may be null)
     * @return the CONNECTED frame
     * @throws IOException if the TCP connection fails
     */
    public StompFrame connect(String virtualHost, String login, String passcode) throws IOException {
        socket = new Socket(host, port);
        transport = new TcpStompTransport(socket);
        client = new StompClient(transport);
        return client.connect(virtualHost, login, passcode, 0, 0);
    }

    /**
     * Connects to the broker with default settings.
     *
     * @param virtualHost the STOMP virtual host
     * @return the CONNECTED frame
     * @throws IOException if the TCP connection fails
     */
    public StompFrame connect(String virtualHost) throws IOException {
        return connect(virtualHost, null, null);
    }

    /**
     * Returns the underlying STOMP client for protocol operations.
     *
     * @return the STOMP client
     * @throws IllegalStateException if not connected
     */
    public StompClient getClient() {
        if (client == null) throw new IllegalStateException("Not connected");
        return client;
    }

    /**
     * Returns whether this client is connected.
     *
     * @return {@code true} if connected
     */
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
