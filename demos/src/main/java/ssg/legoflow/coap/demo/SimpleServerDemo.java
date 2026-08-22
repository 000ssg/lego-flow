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
 * Demonstrates a simple CoAP server with temperature and humidity resources
 * supporting GET and PUT operations.
 *
 * @since 0.1.0
 */
public final class SimpleServerDemo {

    private final CoapServer server;

    /**
     * Creates the demo with a server on the given port.
     *
     * @param port the UDP port
     * @since 0.1.0
     */
    public SimpleServerDemo(int port) {
        this.server = new CoapServer(CoapServerConfig.withPort(port));

        server.add(new TemperatureResource());
        server.add(new HumidityResource());
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
     * Returns the server instance.
     *
     * @return the server
     * @since 0.1.0
     */
    public CoapServer server() {
        return server;
    }

    /**
     * Temperature sensor resource supporting GET and PUT.
     *
     * @since 0.1.0
     */
    public static final class TemperatureResource extends CoapResource {

        private final AtomicReference<String> value = new AtomicReference<>("22.5");

        /** Creates the temperature resource. */
        public TemperatureResource() {
            super("temperature", "/sensors/temperature", false);
            getAttributes().resourceType("temperature")
                    .contentFormat(ContentFormat.TEXT_PLAIN.value())
                    .title("Temperature Sensor");
        }

        @Override
        public void handleGet(CoapExchange exchange) {
            exchange.respond(CoapCode.CONTENT,
                    value.get().getBytes(StandardCharsets.UTF_8),
                    ContentFormat.TEXT_PLAIN.value());
        }

        @Override
        public void handlePut(CoapExchange exchange) {
            byte[] payload = exchange.getRequest().payload();
            if (payload.length > 0) {
                value.set(new String(payload, StandardCharsets.UTF_8));
                exchange.respond(CoapCode.CHANGED);
            } else {
                exchange.respond(CoapCode.BAD_REQUEST);
            }
        }

        /** Returns the current value. */
        public String currentValue() {
            return value.get();
        }
    }

    /**
     * Humidity sensor resource supporting GET and PUT.
     *
     * @since 0.1.0
     */
    public static final class HumidityResource extends CoapResource {

        private final AtomicReference<String> value = new AtomicReference<>("65");

        /** Creates the humidity resource. */
        public HumidityResource() {
            super("humidity", "/sensors/humidity", false);
            getAttributes().resourceType("humidity")
                    .contentFormat(ContentFormat.TEXT_PLAIN.value())
                    .title("Humidity Sensor");
        }

        @Override
        public void handleGet(CoapExchange exchange) {
            exchange.respond(CoapCode.CONTENT,
                    value.get().getBytes(StandardCharsets.UTF_8),
                    ContentFormat.TEXT_PLAIN.value());
        }

        @Override
        public void handlePut(CoapExchange exchange) {
            byte[] payload = exchange.getRequest().payload();
            if (payload.length > 0) {
                value.set(new String(payload, StandardCharsets.UTF_8));
                exchange.respond(CoapCode.CHANGED);
            } else {
                exchange.respond(CoapCode.BAD_REQUEST);
            }
        }

        /** Returns the current value. */
        public String currentValue() {
            return value.get();
        }
    }
}
