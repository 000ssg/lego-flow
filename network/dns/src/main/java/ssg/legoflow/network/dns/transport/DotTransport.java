package ssg.legoflow.network.dns.transport;

import ssg.legoflow.network.dns.protocol.DnsCodec;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * DNS-over-TLS transport (RFC 7858).
 *
 * <p>Connects to a DNS server on port 853 using TLS. Messages use the
 * same 2-byte length prefix format as TCP DNS.
 *
 * @since 0.1.0
 */
public final class DotTransport implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DotTransport.class);

    /** Default DNS-over-TLS port. */
    public static final int DEFAULT_PORT = 853;

    private final Duration timeout;
    private final SSLSocketFactory sslFactory;
    private SSLSocket socket;

    /**
     * Creates a DoT transport with the default SSL context.
     *
     * @param timeout the connection and read timeout
     * @since 0.1.0
     */
    public DotTransport(Duration timeout) {
        this.timeout = timeout;
        try {
            SSLContext ctx = SSLContext.getDefault();
            this.sslFactory = ctx.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSL context", e);
        }
    }

    /**
     * Creates a DoT transport with a custom SSL context.
     *
     * @param timeout    the connection and read timeout
     * @param sslContext the SSL context to use
     * @since 0.1.0
     */
    public DotTransport(Duration timeout, SSLContext sslContext) {
        this.timeout = timeout;
        this.sslFactory = sslContext.getSocketFactory();
    }

    /**
     * Sends a DNS query over TLS and receives the response.
     *
     * @param query   the query message
     * @param address the server address (port defaults to 853)
     * @return the response message
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public DnsMessage send(DnsMessage query, InetSocketAddress address) throws IOException {
        int port = address.getPort() == 0 ? DEFAULT_PORT : address.getPort();
        InetSocketAddress target = new InetSocketAddress(address.getAddress(), port);

        byte[] data = DnsCodec.encode(query);

        socket = (SSLSocket) sslFactory.createSocket();
        socket.setSoTimeout((int) timeout.toMillis());
        socket.connect(target, (int) timeout.toMillis());
        socket.startHandshake();
        LOG.debug("Established TLS connection to {}", target);

        try {
            OutputStream out = socket.getOutputStream();
            out.write((data.length >> 8) & 0xFF);
            out.write(data.length & 0xFF);
            out.write(data);
            out.flush();

            InputStream in = socket.getInputStream();
            int hi = in.read();
            int lo = in.read();
            if (hi < 0 || lo < 0) {
                throw new IOException("Connection closed before response length");
            }
            int responseLen = (hi << 8) | lo;
            byte[] response = in.readNBytes(responseLen);
            if (response.length < responseLen) {
                throw new IOException("Truncated DoT response");
            }
            LOG.debug("Received DoT response ({} bytes)", responseLen);
            return DnsCodec.decode(response);
        } finally {
            socket.close();
        }
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
