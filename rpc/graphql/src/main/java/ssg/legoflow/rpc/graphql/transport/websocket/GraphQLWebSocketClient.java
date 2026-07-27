package ssg.legoflow.rpc.graphql.transport.websocket;

import ssg.legoflow.rpc.graphql.execution.ExecutionResult;
import ssg.legoflow.rpc.graphql.transport.GraphQLTransport;
import ssg.legoflow.rpc.graphql.transport.JsonCodec;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * WebSocket client for GraphQL subscriptions using the graphql-ws protocol.
 *
 * @since 1.0.0
 */
public final class GraphQLWebSocketClient implements GraphQLTransport, AutoCloseable {

    private final String endpoint;
    private final HttpClient httpClient;
    private WebSocket webSocket;
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final Map<String, CompletableFuture<ExecutionResult>> pendingQueries = new ConcurrentHashMap<>();
    private final Map<String, Consumer<ExecutionResult>> subscriptionHandlers = new ConcurrentHashMap<>();
    private final CompletableFuture<Void> connectionAck = new CompletableFuture<>();
    private volatile boolean connected = false;

    /**
     * Creates a new GraphQL WebSocket client.
     *
     * @param endpoint the WebSocket endpoint URL (ws:// or wss://)
     */
    public GraphQLWebSocketClient(String endpoint) {
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Connects to the WebSocket server and performs the connection_init handshake.
     *
     * @throws InterruptedException if interrupted
     * @throws ExecutionException   if connection fails
     */
    public void connect() throws InterruptedException, ExecutionException {
        webSocket = httpClient.newWebSocketBuilder()
                .subprotocols("graphql-transport-ws")
                .buildAsync(URI.create(endpoint), new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            handleMessage(buffer.toString());
                            buffer.setLength(0);
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        connected = false;
                        return null;
                    }
                }).get();

        // Send connection_init
        sendJson(Map.of("type", "connection_init"));

        // Wait for connection_ack
        connectionAck.get();
        connected = true;
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(String message) {
        var json = JsonCodec.decodeObject(message);
        if (json == null) return;

        var type = (String) json.get("type");
        var id = (String) json.get("id");

        switch (type) {
            case "connection_ack" -> connectionAck.complete(null);
            case "next" -> {
                var payload = (Map<String, Object>) json.get("payload");
                var data = payload != null ? payload.get("data") : null;
                var result = ExecutionResult.of(data);

                // Check subscription handlers
                var handler = subscriptionHandlers.get(id);
                if (handler != null) {
                    handler.accept(result);
                }

                // Check pending queries
                var future = pendingQueries.get(id);
                if (future != null) {
                    future.complete(result);
                }
            }
            case "error" -> {
                var payload = json.get("payload");
                var errors = new ArrayList<ExecutionResult.GraphQLError>();
                if (payload instanceof List<?> list) {
                    for (var err : list) {
                        if (err instanceof Map<?, ?> errMap) {
                            errors.add(ExecutionResult.GraphQLError.of(
                                    (String) errMap.get("message")));
                        }
                    }
                }
                var result = ExecutionResult.ofErrors(errors);
                var future = pendingQueries.remove(id);
                if (future != null) future.complete(result);
            }
            case "complete" -> {
                pendingQueries.remove(id);
                subscriptionHandlers.remove(id);
            }
            case "pong" -> { /* ignore */ }
        }
    }

    @Override
    public ExecutionResult execute(String query, String operationName, Map<String, Object> variables) {
        var id = String.valueOf(idCounter.getAndIncrement());
        var future = new CompletableFuture<ExecutionResult>();
        pendingQueries.put(id, future);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("query", query);
        if (operationName != null) payload.put("operationName", operationName);
        if (variables != null) payload.put("variables", variables);

        sendJson(Map.of(
                "id", id,
                "type", "subscribe",
                "payload", payload));

        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            return ExecutionResult.ofErrors(List.of(
                    ExecutionResult.GraphQLError.of("WebSocket query failed: " + e.getMessage())));
        }
    }

    /**
     * Subscribes to a GraphQL subscription.
     *
     * @param query         the subscription query
     * @param operationName the operation name, or null
     * @param variables     the variables, or null
     * @param handler       the handler for subscription events
     * @return the subscription ID (can be used to unsubscribe)
     */
    public String subscribe(String query, String operationName,
                            Map<String, Object> variables,
                            Consumer<ExecutionResult> handler) {
        var id = String.valueOf(idCounter.getAndIncrement());
        subscriptionHandlers.put(id, handler);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("query", query);
        if (operationName != null) payload.put("operationName", operationName);
        if (variables != null) payload.put("variables", variables);

        sendJson(Map.of(
                "id", id,
                "type", "subscribe",
                "payload", payload));

        return id;
    }

    /**
     * Unsubscribes from a subscription.
     *
     * @param id the subscription ID
     */
    public void unsubscribe(String id) {
        subscriptionHandlers.remove(id);
        sendJson(Map.of("id", id, "type", "complete"));
    }

    /**
     * Returns whether the client is connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected;
    }

    @Override
    public String name() {
        return "websocket";
    }

    @Override
    public void close() {
        connected = false;
        if (webSocket != null) {
            webSocket.sendClose(1000, "Normal closure");
        }
    }

    private void sendJson(Map<String, Object> message) {
        if (webSocket != null) {
            webSocket.sendText(JsonCodec.encode(message), true);
        }
    }
}
