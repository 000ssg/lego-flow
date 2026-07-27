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
 * Active mode FTP data connection.
 *
 * <p>In active mode (PORT/EPRT), the client opens a listening socket and
 * tells the server the address/port via PORT or EPRT. The server then connects
 * to the client's listening socket from port 20 (or an ephemeral port).
 *
 * <p>On the server side, this class connects to the client's specified address/port.
 * On the client side, this class listens and accepts an incoming connection.
 *
 * @since 1.0.0
 */
public final class ActiveDataConnection implements DataConnection {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveDataConnection.class);

    private final InetAddress address;
    private final int port;
    private final boolean serverSide;
    private volatile Socket socket;
    private volatile ServerSocket serverSocket;

    /**
     * Creates an active data connection.
     *
     * @param address    the remote address to connect to (server side) or local bind address (client side)
     * @param port       the port
     * @param serverSide {@code true} if this is the server side (connects to client),
     *                   {@code false} if client side (listens for server connection)
     */
    public ActiveDataConnection(InetAddress address, int port, boolean serverSide) {
        this.address = Objects.requireNonNull(address, "address");
        this.port = port;
        this.serverSide = serverSide;
    }

    @Override
    public Socket open() throws IOException {
        if (serverSide) {
            // Server connects TO the client's listening port
            socket = new Socket();
            socket.connect(new InetSocketAddress(address, port), 10_000);
            LOG.debug("Active mode: server connected to client at {}:{}", address.getHostAddress(), port);
        } else {
            // Client opens a listening socket and waits for server to connect
            serverSocket = new ServerSocket(port, 1, address);
            serverSocket.setSoTimeout(30_000);
            LOG.debug("Active mode: client listening on {}:{}", address.getHostAddress(),
                    serverSocket.getLocalPort());
            socket = serverSocket.accept();
            LOG.debug("Active mode: server connected from {}", socket.getRemoteSocketAddress());
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
     * Returns the local port of the listening socket (client side only).
     *
     * @return the local port, or -1 if not listening
     */
    public int getLocalPort() {
        if (serverSocket != null) {
            return serverSocket.getLocalPort();
        }
        return port;
    }

    /**
     * Returns the address for this connection.
     *
     * @return the address
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Returns the port for this connection.
     *
     * @return the port
     */
    public int getPort() {
        return port;
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
     * Formats the address/port as a PORT command argument: {@code h1,h2,h3,h4,p1,p2}.
     *
     * @param address the IP address
     * @param port    the port number
     * @return the PORT argument string
     */
    public static String formatPortArgument(InetAddress address, int port) {
        byte[] addr = address.getAddress();
        int p1 = (port >> 8) & 0xFF;
        int p2 = port & 0xFF;
        return String.format("%d,%d,%d,%d,%d,%d",
                addr[0] & 0xFF, addr[1] & 0xFF, addr[2] & 0xFF, addr[3] & 0xFF,
                p1, p2);
    }

    /**
     * Parses a PORT command argument into address and port.
     *
     * @param argument the PORT argument (e.g., "192,168,1,1,4,1")
     * @return a two-element array: [InetAddress, port as Integer]
     * @throws IOException if the argument is malformed
     */
    public static Object[] parsePortArgument(String argument) throws IOException {
        String[] parts = argument.split(",");
        if (parts.length != 6) {
            throw new IOException("Invalid PORT argument: " + argument);
        }
        try {
            byte[] addr = new byte[4];
            for (int i = 0; i < 4; i++) {
                addr[i] = (byte) Integer.parseInt(parts[i].trim());
            }
            int port = (Integer.parseInt(parts[4].trim()) << 8) | Integer.parseInt(parts[5].trim());
            return new Object[]{InetAddress.getByAddress(addr), port};
        } catch (NumberFormatException e) {
            throw new IOException("Invalid PORT argument: " + argument, e);
        }
    }

    /**
     * Formats an EPRT command argument: {@code |protocol|address|port|}.
     *
     * @param address the IP address
     * @param port    the port number
     * @return the EPRT argument string
     */
    public static String formatEprtArgument(InetAddress address, int port) {
        int protocol = address.getAddress().length == 4 ? 1 : 2;
        return String.format("|%d|%s|%d|", protocol, address.getHostAddress(), port);
    }

    /**
     * Parses an EPRT command argument.
     *
     * @param argument the EPRT argument (e.g., "|1|192.168.1.1|6789|")
     * @return a two-element array: [InetAddress, port as Integer]
     * @throws IOException if the argument is malformed
     */
    public static Object[] parseEprtArgument(String argument) throws IOException {
        // Format: |protocol|address|port|
        if (!argument.startsWith("|") || !argument.endsWith("|")) {
            throw new IOException("Invalid EPRT argument: " + argument);
        }
        String[] parts = argument.substring(1, argument.length() - 1).split("\\|");
        if (parts.length != 3) {
            throw new IOException("Invalid EPRT argument: " + argument);
        }
        try {
            InetAddress addr = InetAddress.getByName(parts[1]);
            int port = Integer.parseInt(parts[2]);
            return new Object[]{addr, port};
        } catch (Exception e) {
            throw new IOException("Invalid EPRT argument: " + argument, e);
        }
    }
}
