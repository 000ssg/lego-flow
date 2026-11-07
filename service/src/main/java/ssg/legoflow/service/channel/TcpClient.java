package ssg.legoflow.service.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;

/**
 * Generic TCP client that creates connections to remote servers.
 *
 * <p>This eliminates the need for protocol-specific client implementations
 * that create Socket directly.
 *
 * @since 0.3.0
 */
public final class TcpClient {

    private static final Logger LOG = LoggerFactory.getLogger(TcpClient.class);

    /**
     * Creates a new TCP connection to the specified host and port.
     *
     * @param host the remote host
     * @param port the remote port
     * @return the connected socket
     * @throws IOException if the connection fails
     */
    public static Socket connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port));
        LOG.debug("Connected to {}:{} {}", host, port, socket.getRemoteSocketAddress());
        return socket;
    }

    /**
     * Creates a new TCP connection using NIO SocketChannel.
     *
     * @param host the remote host
     * @param port the remote port
     * @return the connected socket channel
     * @throws IOException if the connection fails
     */
    public static SocketChannel connectChannel(String host, int port) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(host, port));
        LOG.debug("Connected to {}:{} {}", host, port, channel.getRemoteAddress());
        return channel;
    }
}
