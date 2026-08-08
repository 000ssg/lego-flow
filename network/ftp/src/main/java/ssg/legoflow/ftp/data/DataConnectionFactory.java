package ssg.legoflow.ftp.data;

import java.net.InetAddress;

/**
 * Factory for creating FTP data connections in active or passive mode.
 *
 * @since 0.1.0
 */
public final class DataConnectionFactory {

    private DataConnectionFactory() {
        // utility class
    }

    /**
     * Creates an active data connection for the server side (server connects to client).
     *
     * @param clientAddress the client's IP address
     * @param clientPort    the client's data port
     * @return the data connection
     */
    public static ActiveDataConnection createActiveServer(InetAddress clientAddress, int clientPort) {
        return new ActiveDataConnection(clientAddress, clientPort, true);
    }

    /**
     * Creates an active data connection for the client side (client listens for server).
     *
     * @param localAddress the local address to bind to
     * @param localPort    the local port to listen on (0 for ephemeral)
     * @return the data connection
     */
    public static ActiveDataConnection createActiveClient(InetAddress localAddress, int localPort) {
        return new ActiveDataConnection(localAddress, localPort, false);
    }

    /**
     * Creates a passive data connection for the server side (server listens for client).
     *
     * @param localAddress the local address to bind to
     * @param localPort    the local port to listen on (0 for ephemeral)
     * @return the data connection
     */
    public static PassiveDataConnection createPassiveServer(InetAddress localAddress, int localPort) {
        return new PassiveDataConnection(localAddress, localPort, true);
    }

    /**
     * Creates a passive data connection for the client side (client connects to server).
     *
     * @param serverAddress the server's passive address
     * @param serverPort    the server's passive data port
     * @return the data connection
     */
    public static PassiveDataConnection createPassiveClient(InetAddress serverAddress, int serverPort) {
        return new PassiveDataConnection(serverAddress, serverPort, false);
    }
}
