package ssg.legoflow.network.dns.transport;

import ssg.legoflow.network.dns.protocol.DnsCodec;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
/**
 * DNS transport over TCP with 2-byte length prefix (RFC 1035, Section 4.2.2).
 *
 * <p>Each DNS message is preceded by a 2-byte (big-endian) length field
 * indicating the message size. This allows messages larger than the
 * 512-byte UDP limit.
 *
 * @since 0.1.0
 */
public final class TcpTransport implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TcpTransport.class);

    private final Duration timeout;
    private Socket socket;

    /**
     * Creates a TCP transport.
     *
     * @param timeout the connection and read timeout
     * @since 0.1.0
     */
    public TcpTransport(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * Sends a DNS query over TCP and receives the response.
     *
     * @param query   the query message
     * @param address the server address
     * @return the response message
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public DnsMessage send(DnsMessage query, InetSocketAddress address) throws IOException {
        byte[] data = DnsCodec.encode(query);
        byte[] responseData = sendRaw(data, address);
        return DnsCodec.decode(responseData);
    }

    /**
     * Sends raw bytes over TCP with the 2-byte length prefix.
     *
     * @param data    the raw DNS message bytes
     * @param address the server address
     * @return the raw response bytes (without the length prefix)
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public byte[] sendRaw(byte[] data, InetSocketAddress address) throws IOException {
        try (Socket sock = new Socket()) {
            sock.setSoTimeout((int) timeout.toMillis());
            sock.connect(address, (int) timeout.toMillis());
            this.socket = sock;

            OutputStream out = sock.getOutputStream();
            // Write 2-byte length prefix
            out.write((data.length >> 8) & 0xFF);
            out.write(data.length & 0xFF);
            out.write(data);
            out.flush();
            LOG.debug("Sent TCP query to {} ({} bytes)", address, data.length);

            InputStream in = sock.getInputStream();
            // Read 2-byte length prefix
            int hi = in.read();
            int lo = in.read();
            if (hi < 0 || lo < 0) {
                throw new IOException("Connection closed before response length");
            }
            int responseLen = (hi << 8) | lo;

            byte[] response = in.readNBytes(responseLen);
            if (response.length < responseLen) {
                throw new IOException("Truncated TCP response: expected "
                        + responseLen + ", got " + response.length);
            }
            LOG.debug("Received TCP response from {} ({} bytes)", address, responseLen);
            return response;
        }
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
