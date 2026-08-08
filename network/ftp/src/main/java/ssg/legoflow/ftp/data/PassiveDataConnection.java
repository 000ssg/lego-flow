package ssg.legoflow.ftp.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

/**
 * Passive mode FTP data connection.
 *
 * <p>In passive mode (PASV/EPSV), the server opens a listening port and
 * communicates it to the client. The client then connects to that port.
 *
 * <p>On the server side, this class listens for an incoming client connection.
 * On the client side, this class connects to the server's specified address/port.
 *
 * @since 0.1.0
 */
public final class PassiveDataConnection implements DataConnection {

    private static final Logger LOG = LoggerFactory.getLogger(PassiveDataConnection.class);

    private final InetAddress address;
    private final int port;
    private final boolean serverSide;
    private volatile Socket socket;
    private volatile ServerSocket serverSocket;

    /**
     * Creates a passive data connection.
     *
     * @param address    the address (server's bind address for server side, server's address for client side)
     * @param port       the port (0 for ephemeral on server side, server's data port on client side)
     * @param serverSide {@code true} if this is the server side (listens for client),
     *                   {@code false} if client side (connects to server)
     */
    public PassiveDataConnection(InetAddress address, int port, boolean serverSide) {
        this.address = Objects.requireNonNull(address, "address");
        this.port = port;
        this.serverSide = serverSide;
    }

    @Override
    public Socket open() throws IOException {
        if (serverSide) {
            if (serverSocket == null) {
                // Server opens a listening socket, client will connect
                serverSocket = new ServerSocket(port, 1, address);
                serverSocket.setSoTimeout(30_000);
                LOG.debug("Passive mode: server listening on {}:{}",
                        address.getHostAddress(), serverSocket.getLocalPort());
            }
            socket = serverSocket.accept();
            LOG.debug("Passive mode: client connected from {}", socket.getRemoteSocketAddress());
        } else {
            // Client connects to the server's passive port
            socket = new Socket();
            socket.connect(new InetSocketAddress(address, port), 10_000);
            LOG.debug("Passive mode: client connected to server at {}:{}", address.getHostAddress(), port);
        }
        return socket;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        ensureOpen();
        return socket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        ensureOpen();
        return socket.getOutputStream();
    }

    @Override
    public boolean isOpen() {
        return socket != null && !socket.isClosed();
    }

    /**
     * Returns the local port of the listening socket (server side only).
     * Must be called after the server socket is created.
     *
     * @return the local port, or the configured port if not yet listening
     */
    public int getLocalPort() {
        if (serverSocket != null) {
            return serverSocket.getLocalPort();
        }
        return port;
    }

    /**
     * Creates the server socket without accepting a connection.
     * Useful for the server to determine the port before sending the PASV reply.
     *
     * @return the local port number
     * @throws IOException if the socket cannot be created
     */
    public int listen() throws IOException {
        if (!serverSide) {
            throw new IOException("listen() is only for server side");
        }
        serverSocket = new ServerSocket(port, 1, address);
        serverSocket.setSoTimeout(30_000);
        LOG.debug("Passive mode: server listening on {}:{}",
                address.getHostAddress(), serverSocket.getLocalPort());
        return serverSocket.getLocalPort();
    }

    /**
     * Accepts an incoming connection on the server socket.
     * Must be called after {@link #listen()}.
     *
     * @return the connected socket
     * @throws IOException if accept fails
     */
    public Socket accept() throws IOException {
        if (serverSocket == null) {
            throw new IOException("Server socket not initialized; call listen() first");
        }
        socket = serverSocket.accept();
        LOG.debug("Passive mode: client connected from {}", socket.getRemoteSocketAddress());
        return socket;
    }

    /**
     * Returns the address for this connection.
     *
     * @return the address
     */
    public InetAddress getAddress() {
        return address;
    }

    @Override
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
        if (serverSocket != null) {
            serverSocket.close();
        }
    }

    private void ensureOpen() throws IOException {
        if (!isOpen()) {
            throw new IOException("Data connection is not open");
        }
    }

    /**
     * Parses a PASV reply to extract the address and port.
     *
     * <p>Reply format: {@code 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2).}
     *
     * @param reply the PASV reply text
     * @return a two-element array: [InetAddress, port as Integer]
     * @throws IOException if the reply cannot be parsed
     */
    public static Object[] parsePasvReply(String reply) throws IOException {
        int start = reply.indexOf('(');
        int end = reply.indexOf(')');
        if (start < 0 || end < 0 || end <= start) {
            throw new IOException("Invalid PASV reply: " + reply);
        }
        return ActiveDataConnection.parsePortArgument(reply.substring(start + 1, end));
    }

    /**
     * Formats a PASV reply argument: {@code (h1,h2,h3,h4,p1,p2)}.
     *
     * @param address the server address
     * @param port    the data port
     * @return the formatted PASV argument including parentheses
     */
    public static String formatPasvReply(InetAddress address, int port) {
        return "(" + ActiveDataConnection.formatPortArgument(address, port) + ")";
    }

    /**
     * Parses an EPSV reply to extract the port.
     *
     * <p>Reply format: {@code 229 Entering Extended Passive Mode (|||port|)}
     *
     * @param reply the EPSV reply text
     * @return the data port
     * @throws IOException if the reply cannot be parsed
     */
    public static int parseEpsvReply(String reply) throws IOException {
        int start = reply.indexOf("|||");
        int end = reply.indexOf("|", start + 3);
        if (start < 0 || end < 0) {
            throw new IOException("Invalid EPSV reply: " + reply);
        }
        try {
            return Integer.parseInt(reply.substring(start + 3, end));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid port in EPSV reply: " + reply, e);
        }
    }
}
