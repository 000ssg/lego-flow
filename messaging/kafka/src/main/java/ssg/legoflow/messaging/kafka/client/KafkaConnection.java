package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.codec.KafkaCodec;
import ssg.legoflow.messaging.kafka.protocol.RequestHeader;
import ssg.legoflow.messaging.kafka.protocol.ResponseHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Low-level TCP connection to a Kafka broker.
 *
 * <p>Handles frame-level send/receive with correlation IDs.
 *
 * @since 1.0.0
 */
final class KafkaConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConnection.class);

    private final String host;
    private final int port;
    private final String clientId;
    private final AtomicInteger correlationIdCounter = new AtomicInteger(0);
    private volatile SocketChannel channel;

    KafkaConnection(String host, int port, String clientId) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
    }

    void connect() throws IOException {
        channel = SocketChannel.open();
        channel.configureBlocking(true);
        channel.connect(new InetSocketAddress(host, port));
        LOG.debug("Connected to {}:{}", host, port);
    }

    boolean isConnected() {
        return channel != null && channel.isConnected();
    }

    void ensureConnected() throws IOException {
        if (!isConnected()) {
            connect();
        }
    }

    /**
     * Sends a request and receives the response body.
     *
     * @param apiKey     the API key
     * @param apiVersion the API version
     * @param payload    the request body bytes
     * @return the response body bytes
     * @throws IOException if I/O error occurs
     */
    ByteBuffer sendAndReceive(short apiKey, short apiVersion, byte[] payload) throws IOException {
        ensureConnected();
        int correlationId = correlationIdCounter.getAndIncrement();
        RequestHeader header = new RequestHeader(apiKey, apiVersion, correlationId, clientId);

        // Send request
        ByteBuffer request = KafkaCodec.encodeRequest(header, payload);
        writeFully(request);

        // Read response length
        ByteBuffer lenBuf = ByteBuffer.allocate(4);
        readFully(lenBuf);
        lenBuf.flip();
        int responseLen = lenBuf.getInt();

        // Read response body
        ByteBuffer responseBuf = ByteBuffer.allocate(responseLen);
        readFully(responseBuf);
        responseBuf.flip();

        // Skip response header (correlation ID)
        ResponseHeader respHeader = KafkaCodec.decodeResponseHeader(responseBuf);
        if (respHeader.correlationId() != correlationId) {
            throw new IOException("Correlation ID mismatch: expected " + correlationId
                    + ", got " + respHeader.correlationId());
        }

        return responseBuf;
    }

    private void readFully(ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            int n = channel.read(buf);
            if (n < 0) throw new IOException("Connection closed");
        }
    }

    private void writeFully(ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    @Override
    public void close() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                LOG.debug("Error closing connection", e);
            }
        }
    }
}
