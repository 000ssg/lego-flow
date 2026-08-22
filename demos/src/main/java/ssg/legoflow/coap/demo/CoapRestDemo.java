package ssg.legoflow.coap.demo;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.ContentFormat;
import ssg.legoflow.coap.resource.CoapExchange;
import ssg.legoflow.coap.resource.CoapResource;
import ssg.legoflow.coap.server.CoapServer;
import ssg.legoflow.coap.server.CoapServerConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Demonstrates RESTful CRUD operations over CoAP, similar to HTTP REST
 * but designed for constrained environments.
 *
 * <p>Provides a {@code /items} collection resource supporting:
 * <ul>
 *   <li>GET /items — list all items</li>
 *   <li>POST /items — create a new item</li>
 *   <li>GET /items/{id} — read a specific item</li>
 *   <li>PUT /items/{id} — update an item</li>
 *   <li>DELETE /items/{id} — delete an item</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class CoapRestDemo {

    private final CoapServer server;
    private final ItemCollectionResource collectionResource;

    /**
     * Creates the REST demo with a server on the given port.
     *
     * @param port the UDP port
     * @since 0.1.0
     */
    public CoapRestDemo(int port) {
        this.server = new CoapServer(CoapServerConfig.withPort(port));
        this.collectionResource = new ItemCollectionResource(server);
        server.add(collectionResource);
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
     * Returns the item collection resource.
     *
     * @return the collection resource
     * @since 0.1.0
     */
    public ItemCollectionResource collectionResource() {
        return collectionResource;
    }

    /**
     * Collection resource for managing items via CRUD operations.
     *
     * @since 0.1.0
     */
    public static final class ItemCollectionResource extends CoapResource {

        private final Map<String, String> items = new ConcurrentHashMap<>();
        private final AtomicInteger idCounter = new AtomicInteger(0);
        private final CoapServer server;

        /** Creates the items collection resource. */
        public ItemCollectionResource(CoapServer server) {
            super("items", "/items", false);
            this.server = server;
            getAttributes().resourceType("collection")
                    .contentFormat(ContentFormat.APPLICATION_JSON.value())
                    .title("Items Collection");
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
            String data = new String(payload, StandardCharsets.UTF_8);
            items.put(id, data);

            // Register a new individual item resource
            server.add(new ItemResource(id, items));

            var response = ssg.legoflow.coap.protocol.CoapMessage.builder()
                    .type(exchange.getRequest().type() == ssg.legoflow.coap.protocol.CoapType.CONFIRMABLE
                            ? ssg.legoflow.coap.protocol.CoapType.ACKNOWLEDGEMENT
                            : ssg.legoflow.coap.protocol.CoapType.NON_CONFIRMABLE)
                    .code(CoapCode.CREATED)
                    .messageId(exchange.getRequest().messageId())
                    .token(exchange.getRequest().token())
                    .option(CoapOption.locationPath("items"))
                    .option(CoapOption.locationPath(id))
                    .payload(String.format("{\"id\":\"%s\"}", id).getBytes(StandardCharsets.UTF_8))
                    .build();
            exchange.respond(CoapCode.CREATED,
                    String.format("{\"id\":\"%s\"}", id).getBytes(StandardCharsets.UTF_8),
                    ContentFormat.APPLICATION_JSON.value());
        }

        /** Returns the items map for testing. */
        public Map<String, String> items() {
            return items;
        }
    }

    /**
     * Individual item resource supporting GET, PUT, DELETE.
     *
     * @since 0.1.0
     */
    public static final class ItemResource extends CoapResource {

        private final String itemId;
        private final Map<String, String> items;

        /** Creates an item resource. */
        public ItemResource(String itemId, Map<String, String> items) {
            super(itemId, "/items/" + itemId, false);
            this.itemId = itemId;
            this.items = items;
            getAttributes().resourceType("item")
                    .contentFormat(ContentFormat.APPLICATION_JSON.value());
        }

        @Override
        public void handleGet(CoapExchange exchange) {
            var data = items.get(itemId);
            if (data == null) {
                exchange.respond(CoapCode.NOT_FOUND);
                return;
            }
            var json = String.format("{\"id\":\"%s\",\"data\":\"%s\"}", itemId, data);
            exchange.respond(CoapCode.CONTENT,
                    json.getBytes(StandardCharsets.UTF_8),
                    ContentFormat.APPLICATION_JSON.value());
        }

        @Override
        public void handlePut(CoapExchange exchange) {
            byte[] payload = exchange.getRequest().payload();
            if (payload.length == 0) {
                exchange.respond(CoapCode.BAD_REQUEST);
                return;
            }
            if (!items.containsKey(itemId)) {
                exchange.respond(CoapCode.NOT_FOUND);
                return;
            }
            items.put(itemId, new String(payload, StandardCharsets.UTF_8));
            exchange.respond(CoapCode.CHANGED);
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
}
