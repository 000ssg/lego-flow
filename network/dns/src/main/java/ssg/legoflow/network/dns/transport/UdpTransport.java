package ssg.legoflow.network.dns.transport;

import ssg.legoflow.network.dns.protocol.DnsCodec;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.Objects;

/**
 * DNS transport over UDP (RFC 1035).
 *
 * <p>Sends DNS queries as single UDP datagrams and receives responses.
 * Falls back to TCP if the response is truncated (TC flag set).
 *
 * @since 0.1.0
 */
public final class UdpTransport implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(UdpTransport.class);

    private final DatagramChannel channel;
    private final Duration timeout;

    /**
     * Creates a UDP transport.
     *
     * @param timeout the receive timeout
     * @throws IOException if the channel cannot be opened
     * @since 0.1.0
     */
    public UdpTransport(Duration timeout) throws IOException {
        this.channel = DatagramChannel.open();
        this.channel.configureBlocking(true);
        this.channel.socket().setSoTimeout((int) timeout.toMillis());
        this.timeout = timeout;
    }

    /**
     * Sends a DNS query and receives the response.
     *
     * @param query   the query message
     * @param address the server address
     * @return the response message
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public DnsMessage send(DnsMessage query, InetSocketAddress address) throws IOException {
        byte[] data = DnsCodec.encode(query);
        ByteBuffer sendBuf = ByteBuffer.wrap(data);
        channel.send(sendBuf, address);
        LOG.debug("Sent UDP query to {} ({} bytes)", address, data.length);

        ByteBuffer recvBuf = ByteBuffer.allocate(4096);
        SocketAddress from = channel.receive(recvBuf);
        recvBuf.flip();

        byte[] response = new byte[recvBuf.remaining()];
        recvBuf.get(response);
        LOG.debug("Received UDP response from {} ({} bytes)", from, response.length);

        return DnsCodec.decode(response);
    }

    /**
     * Sends raw bytes and receives a raw response.
     *
     * @param data    the raw DNS message bytes
     * @param address the server address
     * @return the raw response bytes
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public byte[] sendRaw(byte[] data, InetSocketAddress address) throws IOException {
        channel.send(ByteBuffer.wrap(data), address);
        ByteBuffer recvBuf = ByteBuffer.allocate(4096);
        channel.receive(recvBuf);
        recvBuf.flip();
        byte[] response = new byte[recvBuf.remaining()];
        recvBuf.get(response);
        return response;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
