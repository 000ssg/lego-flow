package ssg.legoflow.rpc.graphql.transport.websocket;

import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.rpc.graphql.execution.ExecutionEngine;
import ssg.legoflow.rpc.graphql.execution.ExecutionResult;
import ssg.legoflow.rpc.graphql.execution.SubscriptionPublisher;
import ssg.legoflow.rpc.graphql.schema.GraphQLSchema;
import ssg.legoflow.rpc.graphql.transport.JsonCodec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler implementing the graphql-ws protocol for subscriptions.
 *
 * <p>Implements the graphql-transport-ws protocol:
 * <ul>
 *   <li>connection_init / connection_ack</li>
 *   <li>subscribe / next / error / complete</li>
 *   <li>ping / pong</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class GraphQLWebSocketHandler {

    private final ExecutionEngine engine;
    private final Map<String, Runnable> activeSubscriptions = new ConcurrentHashMap<>();
    private boolean connectionInitialized = false;

    /**
     * Creates a new WebSocket handler for GraphQL subscriptions.
     *
     * @param schema the GraphQL schema
     */
    public GraphQLWebSocketHandler(GraphQLSchema schema) {
        this.engine = new ExecutionEngine(schema);
    }

    /**
     * Handles an incoming WebSocket text message.
     *
     * @param session the WebSocket session
     * @param message the text message (JSON)
     */
    public void handleMessage(WebSocketSession session, String message) {
        var json = JsonCodec.decodeObject(message);
        if (json == null) return;

        var type = (String) json.get("type");
        if (type == null) return;

        switch (type) {
            case "connection_init" -> handleConnectionInit(session, json);
            case "subscribe" -> handleSubscribe(session, json);
            case "complete" -> handleComplete(session, json);
            case "ping" -> handlePing(session);
            default -> { /* ignore unknown messages */ }
        }
    }

    private void handleConnectionInit(WebSocketSession session, Map<String, Object> message) {
        connectionInitialized = true;
        sendMessage(session, Map.of("type", "connection_ack"));
    }

    @SuppressWarnings("unchecked")
    private void handleSubscribe(WebSocketSession session, Map<String, Object> message) {
        if (!connectionInitialized) {
            sendMessage(session, Map.of(
                    "type", "error",
                    "payload", Map.of("message", "Connection not initialized")));
            return;
        }

        var id = (String) message.get("id");
        var payload = (Map<String, Object>) message.get("payload");
        if (id == null || payload == null) return;

        var query = (String) payload.get("query");
        var operationName = (String) payload.get("operationName");
        var variables = (Map<String, Object>) payload.get("variables");

        // Execute the query/subscription
        var result = engine.execute(query, operationName, variables, null);

        if (result.hasErrors()) {
            sendMessage(session, Map.of(
                    "id", id,
                    "type", "error",
                    "payload", result.getErrors().stream()
                            .map(e -> Map.of("message", (Object) e.message()))
                            .toList()));
        } else {
            // Send the result as "next"
            sendMessage(session, Map.of(
                    "id", id,
                    "type", "next",
                    "payload", result.toMap()));

            // Send complete
            sendMessage(session, Map.of(
                    "id", id,
                    "type", "complete"));
        }
    }

    private void handleComplete(WebSocketSession session, Map<String, Object> message) {
        var id = (String) message.get("id");
        if (id != null) {
            var unsub = activeSubscriptions.remove(id);
            if (unsub != null) unsub.run();
        }
    }

    private void handlePing(WebSocketSession session) {
        sendMessage(session, Map.of("type", "pong"));
    }

    /**
     * Registers a subscription and sends events to the client.
     *
     * @param session   the WebSocket session
     * @param id        the subscription ID
     * @param publisher the event publisher
     */
    public void registerSubscription(WebSocketSession session, String id,
                                      SubscriptionPublisher<?> publisher) {
        var unsub = publisher.subscribe(event -> {
            if (session.isOpen()) {
                var result = ExecutionResult.of(event);
                sendMessage(session, Map.of(
                        "id", id,
                        "type", "next",
                        "payload", result.toMap()));
            }
        });
        activeSubscriptions.put(id, unsub);
    }

    /**
     * Handles session close, cleaning up active subscriptions.
     *
     * @param session the WebSocket session
     */
    public void handleClose(WebSocketSession session) {
        for (var unsub : activeSubscriptions.values()) {
            unsub.run();
        }
        activeSubscriptions.clear();
    }

    /**
     * Returns the number of active subscriptions.
     *
     * @return the count
     */
    public int activeSubscriptionCount() {
        return activeSubscriptions.size();
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        if (session.isOpen()) {
            var json = JsonCodec.encode(message);
            session.handleFrame(WebSocketFrame.text(json));
        }
    }
}
