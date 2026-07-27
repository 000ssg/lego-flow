package ssg.legoflow.coap.demo;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.ContentFormat;
import ssg.legoflow.coap.resource.CoapExchange;
import ssg.legoflow.coap.resource.CoapResource;
import ssg.legoflow.coap.server.CoapServer;
import ssg.legoflow.coap.server.CoapServerConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demonstrates an IoT gateway that aggregates multiple CoAP sensor nodes
 * and provides a unified API.
 *
 * <p>The gateway acts as a CoAP server exposing resources for each registered
 * sensor node, translating requests to/from the constrained device format.
 *
 * @since 1.0.0
 */
public final class IoTGatewayDemo {

    private final CoapServer server;
    private final Map<String, SensorNode> sensorNodes = new ConcurrentHashMap<>();

    /**
     * Creates the IoT gateway demo with a server on the given port.
     *
     * @param port the UDP port
     * @since 1.0.0
     */
    public IoTGatewayDemo(int port) {
        this.server = new CoapServer(CoapServerConfig.withPort(port));

        // Add gateway management resource
        server.add(new GatewayStatusResource(sensorNodes));
    }

    /**
     * Registers a sensor node with the gateway.
     *
     * @param nodeId the unique node identifier
     * @param type   the sensor type (e.g. "temperature", "humidity")
     * @param value  the initial sensor value
     * @since 1.0.0
     */
    public void registerNode(String nodeId, String type, String value) {
        var node = new SensorNode(nodeId, type, value);
        sensorNodes.put(nodeId, node);
        server.add(new SensorNodeResource(node));
    }

    /**
     * Updates a sensor node's value.
     *
     * @param nodeId the node identifier
     * @param value  the new value
     * @since 1.0.0
     */
    public void updateNodeValue(String nodeId, String value) {
        var node = sensorNodes.get(nodeId);
        if (node != null) {
            node.setValue(value);
        }
    }

    /**
     * Starts the gateway server.
     *
     * @throws IOException if binding fails
     * @since 1.0.0
     */
    public void start() throws IOException {
        server.start();
    }

    /**
     * Stops the gateway server.
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

    /**
     * Returns registered sensor nodes.
     *
     * @return the sensor nodes map
     * @since 1.0.0
     */
    public Map<String, SensorNode> sensorNodes() {
        return sensorNodes;
    }

    /**
     * Represents a sensor node in the gateway.
     *
     * @since 1.0.0
     */
    public static final class SensorNode {
        private final String nodeId;
        private final String type;
        private volatile String value;

        /** Creates a sensor node. */
        public SensorNode(String nodeId, String type, String value) {
            this.nodeId = nodeId;
            this.type = type;
            this.value = value;
        }

        /** Returns the node ID. */
        public String nodeId() { return nodeId; }

        /** Returns the sensor type. */
        public String type() { return type; }

        /** Returns the current value. */
        public String value() { return value; }

        /** Sets the current value. */
        public void setValue(String value) { this.value = value; }
    }

    private static final class SensorNodeResource extends CoapResource {
        private final SensorNode node;

        SensorNodeResource(SensorNode node) {
            super(node.nodeId(), "/nodes/" + node.nodeId(), false);
            this.node = node;
            getAttributes().resourceType(node.type())
                    .contentFormat(ContentFormat.APPLICATION_JSON.value())
                    .title("Sensor: " + node.nodeId());
        }

        @Override
        public void handleGet(CoapExchange exchange) {
            var json = String.format("{\"nodeId\":\"%s\",\"type\":\"%s\",\"value\":\"%s\"}",
                    node.nodeId(), node.type(), node.value());
            exchange.respond(CoapCode.CONTENT,
                    json.getBytes(StandardCharsets.UTF_8),
                    ContentFormat.APPLICATION_JSON.value());
        }

        @Override
        public void handlePut(CoapExchange exchange) {
            byte[] payload = exchange.getRequest().payload();
            if (payload.length > 0) {
                node.setValue(new String(payload, StandardCharsets.UTF_8));
                exchange.respond(CoapCode.CHANGED);
            } else {
                exchange.respond(CoapCode.BAD_REQUEST);
            }
        }
    }

    private static final class GatewayStatusResource extends CoapResource {
        private final Map<String, SensorNode> nodes;

        GatewayStatusResource(Map<String, SensorNode> nodes) {
            super("gateway", "/gateway/status", false);
            this.nodes = nodes;
            getAttributes().resourceType("gateway").title("Gateway Status");
        }

        @Override
        public void handleGet(CoapExchange exchange) {
            var sb = new StringBuilder("{\"nodes\":[");
            int i = 0;
            for (var node : nodes.values()) {
                if (i++ > 0) sb.append(',');
                sb.append(String.format("{\"id\":\"%s\",\"type\":\"%s\"}", node.nodeId(), node.type()));
            }
            sb.append("]}");
            exchange.respond(CoapCode.CONTENT,
                    sb.toString().getBytes(StandardCharsets.UTF_8),
                    ContentFormat.APPLICATION_JSON.value());
        }
    }
}
