package ssg.legoflow.coap.demo;

import ssg.legoflow.coap.client.CoapClient;
import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.ContentFormat;
import ssg.legoflow.coap.resource.CoapExchange;
import ssg.legoflow.coap.resource.CoapResource;
import ssg.legoflow.coap.server.CoapServer;
import ssg.legoflow.coap.server.CoapServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Comprehensive demo of all CoAP module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link CoapServer}</b> -- No external dependencies.
 * Runs anywhere without installation. Supports all CoAP message types (CON, NON, ACK, RST),
 * REST methods (GET, PUT, POST, DELETE), observe notifications, blockwise transfer,
 * resource discovery, and content format negotiation.
 * Ideal for development, testing, CI/CD, and learning the CoAP protocol.</p>
 *
 * <p><b>Alternative: External CoAP server (Californium, libcoap)</b> -- Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for production IoT deployments, DTLS security testing, and cross-implementation
 * interoperability validation.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>GET/PUT -- read and update sensor resources</li>
 *   <li>POST/DELETE -- create and remove items via REST</li>
 *   <li>Observe -- server-push notifications on resource changes</li>
 *   <li>Resource discovery -- /.well-known/core endpoint with CoRE Link Format</li>
 *   <li>Content negotiation -- text/plain and application/json formats</li>
 *   <li>IoT gateway -- aggregated multi-sensor node management</li>
 *   <li>Blockwise transfer -- large payload assembly</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoCoapAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoCoapAll.class);

    /** Set to {@code true} to connect to an external CoAP server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external CoAP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external CoAP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 5683;

    private DemoCoapAll() {}

    /**
     * Results from running the full demo.
     *
     * @param getSensorOk       true if GET on sensor resource returned 2.05 Content
     * @param putSensorOk       true if PUT updated the sensor and re-GET confirmed the new value
     * @param postItemOk        true if POST created a new item (2.01 Created)
     * @param deleteItemOk      true if DELETE removed the item (2.02 Deleted)
     * @param observeCount      number of observe notifications received
     * @param discoveryLinks    number of resource links found in /.well-known/core
     * @param contentFormatOk   true if content format negotiation worked (JSON response)
     * @param gatewayNodeCount  number of sensor nodes registered through the IoT gateway
     * @param blockTransferOk   true if large payload was retrieved successfully
     */
    public record Results(
            boolean getSensorOk,
            boolean putSensorOk,
            boolean postItemOk,
            boolean deleteItemOk,
            int observeCount,
            int discoveryLinks,
            boolean contentFormatOk,
            int gatewayNodeCount,
            boolean blockTransferOk
    ) {}

    /**
     * Runs the comprehensive demo covering all CoAP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runWithExternalServer(EXTERNAL_HOST, EXTERNAL_PORT);
        }

        // Use SimpleServerDemo for GET/PUT (proven pattern)
        var simpleDemo = new SimpleServerDemo(0);
        simpleDemo.start();
        int simplePort = simpleDemo.server().getPort();

        // Separate server for REST, discovery, content format, block transfer
        var server = new CoapServer(CoapServerConfig.withPort(0));
        server.add(new ObservableSensorResource());
        server.add(new JsonResource());
        server.add(new ItemCollectionResource(server));
        server.add(new LargePayloadResource());
        server.start();

        try {
            int port = server.getPort();
            LOG.info("In-house CoapServer started on port {} (simple on {})", port, simplePort);

            boolean getOk;
            boolean putOk;
            try (var simpleClient = new CoapClient("localhost", simplePort)) {
                getOk = demoGetSensor(simpleClient);
                putOk = demoPutSensor(simpleClient);
            }

            boolean postOk;
            boolean deleteOk;
            int discoveryLinks;
            boolean contentFormat;
            boolean blockOk;

            try (var client = new CoapClient("localhost", port)) {
                postOk = demoPostItem(client);
                deleteOk = demoDeleteItem(client);
                discoveryLinks = demoDiscovery(client);
                contentFormat = demoContentFormat(client);
                blockOk = demoBlockTransfer(client);
            }

            int observeCount = demoObserve("localhost", port, server);
            int gatewayNodes = demoIoTGateway();

            return new Results(getOk, putOk, postOk, deleteOk, observeCount,
                    discoveryLinks, contentFormat, gatewayNodes, blockOk);
        } finally {
            server.stop();
            simpleDemo.stop();
        }
    }

    private static Results runWithExternalServer(String host, int port) throws Exception {
        try (var client = new CoapClient(host, port)) {
            return new Results(
                    client.get("/sensors/temperature").isSuccess(),
                    client.put("/sensors/humidity", "72".getBytes(StandardCharsets.UTF_8),
                            ContentFormat.TEXT_PLAIN.value()).code().equals(CoapCode.CHANGED),
                    false, false, 0, 0, false, 0, false
            );
        }
    }

    // ======================== 1. GET SENSOR ================================

    /**
     * Demonstrates GET request on a sensor resource.
     */
    static boolean demoGetSensor(CoapClient client) throws Exception {
        LOG.info("=== 1. GET Sensor ===");
        var response = client.get("/sensors/temperature");
        LOG.info("GET /sensors/temperature -> {} payload={}", response.code(), response.getPayloadString());
        return response.isSuccess() && "22.5".equals(response.getPayloadString());
    }

    // ======================== 2. PUT SENSOR ================================

    /**
     * Demonstrates PUT request to update a sensor value, then GET to confirm the update persisted.
     */
    static boolean demoPutSensor(CoapClient client) throws Exception {
        LOG.info("=== 2. PUT Sensor ===");
        var putResponse = client.put("/sensors/temperature",
                "25.0".getBytes(StandardCharsets.UTF_8),
                ContentFormat.TEXT_PLAIN.value());
        LOG.info("PUT /sensors/temperature -> {}", putResponse.code());

        var getResponse = client.get("/sensors/temperature");
        LOG.info("GET /sensors/temperature after PUT -> payload={}", getResponse.getPayloadString());
        return CoapCode.CHANGED.equals(putResponse.code()) && "25.0".equals(getResponse.getPayloadString());
    }

    // ======================== 3. POST ITEM =================================

    /**
     * Demonstrates POST to create a new item in a collection resource.
     */
    static boolean demoPostItem(CoapClient client) throws Exception {
        LOG.info("=== 3. POST Item ===");
        var response = client.post("/items",
                "new-item-data".getBytes(StandardCharsets.UTF_8),
                ContentFormat.TEXT_PLAIN.value());
        LOG.info("POST /items -> {}", response.code());
        return CoapCode.CREATED.equals(response.code());
    }

    // ======================== 4. DELETE ITEM ================================

    /**
     * Demonstrates DELETE to remove an item. First creates one via POST, then deletes it.
     */
    static boolean demoDeleteItem(CoapClient client) throws Exception {
        LOG.info("=== 4. DELETE Item ===");
        // Create first
        client.post("/items", "to-delete".getBytes(StandardCharsets.UTF_8),
                ContentFormat.TEXT_PLAIN.value());

        // Delete item with id=2 (second POST)
        var response = client.delete("/items/2");
        LOG.info("DELETE /items/2 -> {}", response.code());
        return CoapCode.DELETED.equals(response.code());
    }

    // ======================== 5. OBSERVE ===================================

    /**
     * Demonstrates observe: client registers for notifications, server pushes updates.
     */
    static int demoObserve(String host, int port, CoapServer server) throws Exception {
        LOG.info("=== 5. Observe ===");
        var notifications = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(2);

        try (var client = new CoapClient(host, port)) {
            var relation = client.observe("/sensors/observable", (response) -> {
                notifications.add(response.getPayloadString());
                latch.countDown();
            });

            // Trigger notifications by updating the resource
            if (server != null) {
                var resource = (ObservableSensorResource) server.getResource("/sensors/observable");
                if (resource != null) {
                    Thread.sleep(100);
                    resource.setValue("25.0");
                    resource.notifyObservers();
                    Thread.sleep(100);
                    resource.setValue("26.0");
                    resource.notifyObservers();
                }
            }

            latch.await(5, TimeUnit.SECONDS);
            client.cancelObserve(relation);
        }
        LOG.info("Observe notifications received: {}", notifications.size());
        return notifications.size();
    }

    // ======================== 6. DISCOVERY =================================

    /**
     * Demonstrates resource discovery via /.well-known/core.
     */
    static int demoDiscovery(CoapClient client) throws Exception {
        LOG.info("=== 6. Resource Discovery ===");
        var response = client.get("/.well-known/core");
        String linkFormat = response.getPayloadString();
        LOG.info("Discovery payload length: {}", linkFormat.length());

        // Count links by counting '<' occurrences
        int links = 0;
        for (char c : linkFormat.toCharArray()) {
            if (c == '<') links++;
        }
        LOG.info("Discovered {} resource links", links);
        return links;
    }

    // ======================== 7. CONTENT FORMAT =============================

    /**
     * Demonstrates content format negotiation with a JSON resource.
     */
    static boolean demoContentFormat(CoapClient client) throws Exception {
        LOG.info("=== 7. Content Format Negotiation ===");
        var response = client.get("/data/json");
        LOG.info("GET /data/json -> contentFormat={} payload={}",
                response.getContentFormat(), response.getPayloadString());
        return response.isSuccess()
                && response.getContentFormat() == ContentFormat.APPLICATION_JSON.value()
                && response.getPayloadString().contains("\"status\"");
    }

    // ======================== 8. IOT GATEWAY ================================

    /**
     * Demonstrates IoT gateway pattern: multiple sensor nodes behind a single server.
     */
    static int demoIoTGateway() throws Exception {
        LOG.info("=== 8. IoT Gateway ===");
        // Use a separate IoTGatewayDemo instance with its own server
        var gateway = new IoTGatewayDemo(0);
        gateway.registerNode("temp-1", "temperature", "22.0");
        gateway.registerNode("hum-1", "humidity", "55");
        gateway.registerNode("pres-1", "pressure", "1013");
        gateway.start();

        try (var client = new CoapClient("localhost", gateway.server().getPort())) {
            // Read each node
            for (String nodeId : List.of("temp-1", "hum-1", "pres-1")) {
                var response = client.get("/nodes/" + nodeId);
                LOG.info("Gateway GET /nodes/{} -> {}", nodeId, response.getPayloadString());
            }

            // Read gateway status
            var status = client.get("/gateway/status");
            LOG.info("Gateway status: {}", status.getPayloadString());
        } finally {
            gateway.stop();
        }

        int nodeCount = gateway.sensorNodes().size();
        LOG.info("IoT gateway managed {} nodes", nodeCount);
        return nodeCount;
    }

    // ======================== 9. BLOCK TRANSFER =============================

    /**
     * Demonstrates blockwise transfer for large payloads.
     */
    static boolean demoBlockTransfer(CoapClient client) throws Exception {
        LOG.info("=== 9. Block Transfer ===");
        var response = client.get("/large");
        LOG.info("GET /large -> {} payload size={}", response.code(),
                response.payload() != null ? response.payload().length : 0);
        return response.isSuccess() && response.payload() != null && response.payload().length > 100;
    }

    // ======================== RESOURCE DEFINITIONS ==========================

    /**
     * Simple sensor resource for GET/PUT.
     *
     * @since 1.0.0
     */
    static final class SensorResource extends CoapResource {
        private final AtomicReference<String> value;

        SensorResource(String name, String path, String initialValue) {
            super(name, path, false);
            this.value = new AtomicReference<>(initialValue);
            getAttributes().resourceType("sensor")
                    .contentFormat(ContentFormat.TEXT_PLAIN.value())
                    .title(name + " sensor");
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
    }

    /**
     * Observable sensor resource that supports observe notifications.
     *
     * @since 1.0.0
     */
    static final class ObservableSensorResource extends CoapResource {
        private final AtomicReference<String> value = new AtomicReference<>("20.0");

        ObservableSensorResource() {
            super("observable", "/sensors/observable", true);
            getAttributes().resourceType("sensor").observable(true)
                    .contentFormat(ContentFormat.TEXT_PLAIN.value())
                    .title("Observable Sensor");
        }

        @Override
        public void handleGet(CoapExchange exchange) {
            exchange.respond(CoapCode.CONTENT,
                    value.get().getBytes(StandardCharsets.UTF_8),
                    ContentFormat.TEXT_PLAIN.value());
        }

        void setValue(String v) { value.set(v); }
    }

    /**
     * JSON resource for content format negotiation demo.
     *
     * @since 1.0.0
     */
    static final class JsonResource extends CoapResource {
        JsonResource() {
            super("json", "/data/json", false);
            getAttributes().resourceType("data")
                    .contentFormat(ContentFormat.APPLICATION_JSON.value())
                    .title("JSON Data");
        }

        @Override
        public void handleGet(CoapExchange exchange) {
            var json = "{\"status\":\"ok\",\"version\":\"1.0\",\"uptime\":12345}";
            exchange.respond(CoapCode.CONTENT,
                    json.getBytes(StandardCharsets.UTF_8),
                    ContentFormat.APPLICATION_JSON.value());
        }
    }

    /**
     * Item collection resource supporting POST (create) and child item DELETE.
     *
     * @since 1.0.0
     */
    static final class ItemCollectionResource extends CoapResource {
        private final java.util.Map<String, String> items = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicInteger idCounter = new java.util.concurrent.atomic.AtomicInteger(0);
        private final CoapServer server;

        ItemCollectionResource(CoapServer server) {
            super("items", "/items", false);
            this.server = server;
            getAttributes().resourceType("collection")
                    .contentFormat(ContentFormat.APPLICATION_JSON.value())
                    .title("Items");
        }

        @Override
        public void handleGet(CoapExchange exchange) {
            var sb = new StringBuilder("[");
            int i = 0;
            for (var entry : items.entrySet()) {
                if (i++ > 0) sb.append(',');
                sb.append(String.format("{\"id\":\"%s\",\"data\":\"%s\"}", entry.getKey(), entry.getValue()));
            }
            sb.append(']');
            exchange.respond(CoapCode.CONTENT,
                    sb.toString().getBytes(StandardCharsets.UTF_8),
                    ContentFormat.APPLICATION_JSON.value());
        }

        @Override
        public void handlePost(CoapExchange exchange) {
            byte[] payload = exchange.getRequest().payload();
            if (payload.length == 0) {
                exchange.respond(CoapCode.BAD_REQUEST);
                return;
            }
            String id = String.valueOf(idCounter.incrementAndGet());
            items.put(id, new String(payload, StandardCharsets.UTF_8));
            server.add(new ItemResource(id, items));
            exchange.respond(CoapCode.CREATED,
                    String.format("{\"id\":\"%s\"}", id).getBytes(StandardCharsets.UTF_8),
                    ContentFormat.APPLICATION_JSON.value());
        }
    }

    /**
     * Individual item resource supporting GET and DELETE.
     *
     * @since 1.0.0
     */
    static final class ItemResource extends CoapResource {
        private final String itemId;
        private final java.util.Map<String, String> items;

        ItemResource(String itemId, java.util.Map<String, String> items) {
            super(itemId, "/items/" + itemId, false);
            this.itemId = itemId;
            this.items = items;
        }

        @Override
        public void handleGet(CoapExchange exchange) {
            var data = items.get(itemId);
            if (data == null) {
                exchange.respond(CoapCode.NOT_FOUND);
                return;
            }
            exchange.respond(CoapCode.CONTENT,
                    String.format("{\"id\":\"%s\",\"data\":\"%s\"}", itemId, data)
                            .getBytes(StandardCharsets.UTF_8),
                    ContentFormat.APPLICATION_JSON.value());
        }

        @Override
        public void handleDelete(CoapExchange exchange) {
            if (items.remove(itemId) != null) {
                exchange.respond(CoapCode.DELETED);
            } else {
                exchange.respond(CoapCode.NOT_FOUND);
            }
        }
    }

    /**
     * Large payload resource for blockwise transfer demo.
     *
     * @since 1.0.0
     */
    static final class LargePayloadResource extends CoapResource {
        private final byte[] data;

        LargePayloadResource() {
            super("large", "/large", false);
            getAttributes().resourceType("large-data")
                    .contentFormat(ContentFormat.TEXT_PLAIN.value())
                    .title("Large Resource");
            var sb = new StringBuilder(2048);
            int line = 0;
            while (sb.length() < 2048) {
                sb.append(String.format("Line %04d: blockwise transfer test data.\n", line++));
            }
            this.data = sb.substring(0, 2048).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handleGet(CoapExchange exchange) {
            exchange.respond(CoapCode.CONTENT, data, ContentFormat.TEXT_PLAIN.value());
        }
    }
}
