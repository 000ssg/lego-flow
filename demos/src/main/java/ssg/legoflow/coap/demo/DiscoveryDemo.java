package ssg.legoflow.coap.demo;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.ContentFormat;
import ssg.legoflow.coap.resource.CoapExchange;
import ssg.legoflow.coap.resource.CoapResource;
import ssg.legoflow.coap.server.CoapServer;
import ssg.legoflow.coap.server.CoapServerConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Demonstrates CoAP resource discovery using the {@code /.well-known/core} endpoint.
 *
 * @since 1.0.0
 */
public final class DiscoveryDemo {

    private final CoapServer server;

    /**
     * Creates the discovery demo with a server on the given port.
     *
     * @param port the UDP port
     * @since 1.0.0
     */
    public DiscoveryDemo(int port) {
        this.server = new CoapServer(CoapServerConfig.withPort(port));

        // Add various typed resources
        server.add(createSensorResource("temperature", "/sensors/temperature", "temperature", "sensor"));
        server.add(createSensorResource("humidity", "/sensors/humidity", "humidity", "sensor"));
        server.add(createSensorResource("pressure", "/sensors/pressure", "pressure", "sensor"));
        server.add(createActuatorResource("light", "/actuators/light", "light", "actuator"));
    }

    /**
     * Starts the demo server.
     *
     * @throws IOException if binding fails
     * @since 1.0.0
     */
    public void start() throws IOException {
        server.start();
    }

    /**
     * Stops the demo server.
     *
     * @since 1.0.0
     */
    public void stop() {
        server.stop();
    }

    /**
     * Returns the server.
     *
     * @return the server
     * @since 1.0.0
     */
    public CoapServer server() {
        return server;
    }

    private static CoapResource createSensorResource(String name, String path, String rt, String iface) {
        var resource = new CoapResource(name, path) {
            @Override
            public void handleGet(CoapExchange exchange) {
                exchange.respond(CoapCode.CONTENT,
                        ("value:" + name).getBytes(StandardCharsets.UTF_8),
                        ContentFormat.TEXT_PLAIN.value());
            }
        };
        resource.getAttributes().resourceType(rt).interfaceDescription(iface)
                .contentFormat(ContentFormat.TEXT_PLAIN.value());
        return resource;
    }

    private static CoapResource createActuatorResource(String name, String path, String rt, String iface) {
        var resource = new CoapResource(name, path) {
            @Override
            public void handleGet(CoapExchange exchange) {
                exchange.respond(CoapCode.CONTENT,
                        "off".getBytes(StandardCharsets.UTF_8),
                        ContentFormat.TEXT_PLAIN.value());
            }

            @Override
            public void handlePut(CoapExchange exchange) {
                exchange.respond(CoapCode.CHANGED);
            }
        };
        resource.getAttributes().resourceType(rt).interfaceDescription(iface)
                .contentFormat(ContentFormat.TEXT_PLAIN.value());
        return resource;
    }
}
