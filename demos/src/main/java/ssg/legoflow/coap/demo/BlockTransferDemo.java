package ssg.legoflow.coap.demo;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.ContentFormat;
import ssg.legoflow.coap.resource.CoapExchange;
import ssg.legoflow.coap.resource.CoapResource;
import ssg.legoflow.coap.server.CoapServer;
import ssg.legoflow.coap.server.CoapServerConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
/**
 * Demonstrates large payload transfer using CoAP Block1 and Block2 options (RFC 7959).
 *
 * @since 0.1.0
 */
public final class BlockTransferDemo {

    private final CoapServer server;
    private final LargeResource largeResource;

    /**
     * Creates the block transfer demo with a server on the given port.
     *
     * @param port the UDP port
     * @since 0.1.0
     */
    public BlockTransferDemo(int port) {
        this.server = new CoapServer(CoapServerConfig.withPort(port));
        this.largeResource = new LargeResource();
        server.add(largeResource);
    }

    /**
     * Starts the demo server.
     *
     * @throws IOException if binding fails
     * @since 0.1.0
     */
    public void start() throws IOException {
        server.start();
    }

    /**
     * Stops the demo server.
     *
     * @since 0.1.0
     */
    public void stop() {
        server.stop();
    }

    /**
     * Returns the server.
     *
     * @return the server
     * @since 0.1.0
     */
    public CoapServer server() {
        return server;
    }

    /**
     * Returns the large resource.
     *
     * @return the large resource
     * @since 0.1.0
     */
    public LargeResource largeResource() {
        return largeResource;
    }

    /**
     * Generates a large payload for testing blockwise transfer.
     *
     * @param sizeBytes the desired payload size in bytes
     * @return the generated payload
     * @since 0.1.0
     */
    public static byte[] generateLargePayload(int sizeBytes) {
        var sb = new StringBuilder(sizeBytes);
        int line = 0;
        while (sb.length() < sizeBytes) {
            sb.append(String.format("Line %04d: This is test data for blockwise transfer.\n", line++));
        }
        return sb.substring(0, sizeBytes).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A resource that holds a large payload for blockwise transfer testing.
     *
     * @since 0.1.0
     */
    public static final class LargeResource extends CoapResource {

        private final AtomicReference<byte[]> data = new AtomicReference<>(
                generateLargePayload(2048));

        /** Creates the large resource. */
        public LargeResource() {
            super("large", "/large", false);
            getAttributes().resourceType("large-data")
                    .contentFormat(ContentFormat.TEXT_PLAIN.value())
                    .title("Large Resource");
        }

        @Override
        public void handleGet(CoapExchange exchange) {
            exchange.respond(CoapCode.CONTENT, data.get(), ContentFormat.TEXT_PLAIN.value());
        }

        @Override
        public void handlePut(CoapExchange exchange) {
            byte[] payload = exchange.getRequest().payload();
            if (payload.length > 0) {
                data.set(payload);
                exchange.respond(CoapCode.CHANGED);
            } else {
                exchange.respond(CoapCode.BAD_REQUEST);
            }
        }

        /** Returns the current data. */
        public byte[] currentData() {
            return data.get().clone();
        }

        /** Sets the data. */
        public void setData(byte[] data) {
            this.data.set(data.clone());
        }
    }

    private static byte[] generateLargePayloadStatic(int sizeBytes) {
        return generateLargePayload(sizeBytes);
    }
}
