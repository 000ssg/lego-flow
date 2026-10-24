package ssg.legoflow.interop.wamp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSerializer;
/**
 * WebSocket client for WAMP interop tests.
 *
 * <p>Connects to a WAMP router (e.g., Crossbar) via WebSocket and provides
 * methods for session management, pub/sub, and RPC operations.
 *
 * @since 0.1.0
 */
public class WampWebSocketClient {

    private static final Logger LOG = LoggerFactory.getLogger(WampWebSocketClient.class);
    private static final String WAMP_SUBPROTOCOL = "wamp.2.json";

    private final String uri;
    private final String realm;
    private final WampSerializer serializer;
    private WebSocket webSocket;
    private long sessionId;
    private final AtomicReference<Consumer<WampMessage>> messageHandler = new AtomicReference<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<WampMessage> messageQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private int nextRequestId = 1;

    public WampWebSocketClient(String uri, String realm, WampSerializer serializer) {
        this.uri = uri;
        this.realm = realm;
        this.serializer = serializer;
    }

    /**
     * Connects to the WAMP router and establishes a session.
     *
     * @throws Exception if connection fails
     */
    public void connect() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI wsUri = URI.create(uri);

        CountDownLatch connectLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        CompletionStage<WebSocket> wsStage = client.newWebSocketBuilder()
                .subprotocols(List.of(WAMP_SUBPROTOCOL))
                .buildAsync(wsUri, new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket ws) {
                        WampWebSocketClient.this.webSocket = ws;
                        ws.request(1);
                        connectLatch.countDown();
                    }

                    @Override
                    public void onMessage(WebSocket ws, CharSequence text, CompletionStage<?> ack) {
                        try {
                            String json = text.toString();
                            WampMessage msg = serializer.deserialize(json);
                            messageQueue.add(msg);
                            Consumer<WampMessage> handler = messageHandler.get();
                            if (handler != null) {
                                handler.accept(msg);
                            }
                        } catch (Exception e) {
                            LOG.error("Failed to deserialize WAMP message: {}", text, e);
                        }
                        ack.complete(null);
                        ws.request(1);
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        errorRef.set(error);
                        connectLatch.countDown();
                    }
                });

        try {
            webSocket = wsStage.toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (errorRef.get() != null) {
                throw new Exception("WebSocket connection failed", errorRef.get());
            }
            throw new Exception("WebSocket connection failed", e);
        }

        if (errorRef.get() != null) {
            throw new Exception("WebSocket connection failed", errorRef.get());
        }

        // Send HELLO
        WampMessage.Hello hello = new WampMessage.Hello(realm, Map.of());
        String helloJson = serializer.serialize(hello);
        webSocket.sendText(helloJson, true);

        // Wait for WELCOME
        CountDownLatch welcomeLatch = new CountDownLatch(1);
        Consumer<WampMessage> welcomeHandler = m -> {
            if (m instanceof WampMessage.Welcome) {
                sessionId = ((WampMessage.Welcome) m).sessionId();
                welcomeLatch.countDown();
            }
        };
        messageHandler.set(welcomeHandler);

        if (!welcomeLatch.await(10, TimeUnit.SECONDS)) {
            throw new Exception("Timed out waiting for WELCOME");
        }

        LOG.info("WAMP session established: sessionId={}", sessionId);
    }

    /**
     * Receives the next WAMP message.
     *
     * @return the next message
     * @throws InterruptedException if interrupted
     */
    public WampMessage receive() throws InterruptedException {
        WampMessage msg = messageQueue.poll();
        if (msg == null) {
            throw new InterruptedException("No message received");
        }
        return msg;
    }

    /**
     * Subscribes to a topic.
     *
     * @param topic the topic to subscribe to
     */
    public void subscribe(String topic) {
        WampMessage.Subscribe subscribe = new WampMessage.Subscribe(nextRequestId++, Map.of(), topic);
        send(subscribe);
    }

    /**
     * Unsubscribes from a topic.
     *
     * @param subscriptionId the subscription ID
     */
    public void unsubscribe(long subscriptionId) {
        WampMessage.Unsubscribe unsubscribe = new WampMessage.Unsubscribe(nextRequestId++, subscriptionId);
        send(unsubscribe);
    }

    /**
     * Publishes an event to a topic.
     *
     * @param topic the topic to publish to
     * @param args the event arguments
     */
    public void publish(String topic, List<Object> args) {
        WampMessage.Publish publish = new WampMessage.Publish(topic, args, Map.of());
        send(publish);
    }

    /**
     * Calls a remote procedure.
     *
     * @param procedure the procedure URI
     * @param args the procedure arguments
     */
    public void call(String procedure, List<Object> args) {
        WampMessage.Call call = new WampMessage.Call(nextRequestId++, procedure, args, Map.of());
        send(call);
    }

    /**
     * Registers a procedure handler.
     *
     * @param procedure the procedure URI
     */
    public void register(String procedure) {
        WampMessage.Register register = new WampMessage.Register(nextRequestId++, Map.of(), procedure);
        send(register);
    }

    /**
     * Unregisters a procedure.
     *
     * @param registrationId the registration ID
     */
    public void unregister(long registrationId) {
        WampMessage.Unregister unregister = new WampMessage.Unregister(nextRequestId++, registrationId);
        send(unregister);
    }

    /**
     * Sets the message handler for incoming messages.
     *
     * @param handler the message handler
     */
    public void onMessage(Consumer<WampMessage> handler) {
        messageHandler.set(handler);
    }

    /**
     * Closes the WebSocket connection.
     */
    public void close() {
        if (webSocket != null) {
            try {
                webSocket.sendText(serializer.serialize(new WampMessage.Goodbye(Map.of(), "wamp.error.goodbye")), true);
                webSocket.close(1000);
            } catch (Exception e) {
                LOG.warn("Error closing WebSocket", e);
            }
            webSocket = null;
        }
    }

    private void send(WampMessage msg) {
        if (webSocket == null) {
            throw new IllegalStateException("WebSocket not connected");
        }
        String json = serializer.serialize(msg);
        try {
            webSocket.sendText(json, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send WAMP message", e);
        }
    }
}
