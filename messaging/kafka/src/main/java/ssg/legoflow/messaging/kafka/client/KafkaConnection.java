package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.codec.KafkaCodec;
import ssg.legoflow.messaging.kafka.protocol.RequestHeader;
import ssg.legoflow.messaging.kafka.protocol.ResponseHeader;
import ssg.legoflow.messaging.kafka.transport.KafkaTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Low-level connection to a Kafka broker.
 *
 * <p>Handles frame-level send/receive with correlation IDs. All I/O flows through an
 * injected {@link KafkaTransport} — this class <b>never touches a socket directly</b>
 * (the lego-flow "protocol core is headless" rule; see DECISIONS D7/D11). The transport
 * is provided by the service layer (a {@code PipelineKafkaTransport} driven by the
 * {@code SelectableChannelManager}) or, in tests, an {@code InMemoryKafkaTransport}.
 *
 * @since 0.1.0
 */
final class KafkaConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConnection.class);

    private final String clientId;
    private final AtomicInteger correlationIdCounter = new AtomicInteger(0);
    private final KafkaTransport transport;

    KafkaConnection(KafkaTransport transport, String clientId) {
        if (transport == null) {
            throw new IllegalArgumentException("transport must not be null");
        }
        this.transport = transport;
        this.clientId = clientId;
    }

    boolean isConnected() {
        return transport.isOpen();
    }

    /**
     * Sends a request and receives the matching response body (matched by correlation ID).
     *
     * @param apiKey     the API key
     * @param apiVersion the API version
     * @param payload    the request body bytes
     * @return the response body bytes
     * @throws IOException if I/O error occurs or the transport is closed
     */
    ByteBuffer sendAndReceive(short apiKey, short apiVersion, byte[] payload) throws IOException {
        if (!transport.isOpen()) {
            throw new IOException("Connection closed");
        }
        int correlationId = correlationIdCounter.getAndIncrement();
        RequestHeader header = new RequestHeader(apiKey, apiVersion, correlationId, clientId);

        // Send request (length-prefixed frame).
        ByteBuffer request = KafkaCodec.encodeRequest(header, payload);
        transport.send(request);

        // Read response length.
        ByteBuffer lenBuf = ByteBuffer.allocate(4);
        readFully(lenBuf);
        lenBuf.flip();
        int responseLen = lenBuf.getInt();

        // Read response body.
        ByteBuffer responseBuf = ByteBuffer.allocate(responseLen);
        readFully(responseBuf);
        responseBuf.flip();

        // Verify correlation ID.
        ResponseHeader respHeader = KafkaCodec.decodeResponseHeader(responseBuf);
        if (respHeader.correlationId() != correlationId) {
            throw new IOException("Correlation ID mismatch: expected " + correlationId
                    + ", got " + respHeader.correlationId());
        }

        return responseBuf;
    }

    private void readFully(ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            int n = transport.receiveWithTimeout(buf, 10, TimeUnit.SECONDS);
            if (n < 0) {
                if (transport.isOpen()) {
                    continue; // read timeout — keep waiting for the rest of the frame
                }
                throw new IOException("Connection closed"); // true EOF
            }
        }
    }

    @Override
    public void close() {
        transport.close();
    }
}
