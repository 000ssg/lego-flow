package ssg.legoflow.messaging.stomp.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.messaging.stomp.core.transport.StompTransport;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
/**
 * STOMP 1.2 client for connecting to a STOMP broker.
 *
 * <p>Provides methods for the full STOMP client command set: connect, send,
 * subscribe, unsubscribe, ack, nack, begin, commit, abort, disconnect.
 *
 * <p>Messages received from subscriptions are dispatched to registered handlers.
 * Receipt confirmations are tracked via {@link CompletableFuture}.
 *
 * @since 0.1.0
 */
public class StompClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(StompClient.class);

    private final StompTransport transport;
    private final StompSession session;
    private final HeartbeatMonitor heartbeatMonitor = new HeartbeatMonitor();

    private final Map<String, Consumer<StompFrame>> subscriptionHandlers = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<StompFrame>> receiptFutures = new ConcurrentHashMap<>();
    private final BlockingQueue<StompFrame> errorQueue = new LinkedBlockingQueue<>();
    private final AtomicLong subscriptionCounter = new AtomicLong(0);
    private final AtomicLong receiptCounter = new AtomicLong(0);

    private volatile Consumer<StompFrame> errorHandler;
    private volatile Thread receiverThread;

    /**
     * Creates a new STOMP client over the given transport.
     *
     * @param transport the transport to use
     */
    public StompClient(StompTransport transport) {
        this.transport = transport;
        this.session = new StompSession("client-" + System.nanoTime());
    }

    /**
     * Connects to the STOMP broker.
     *
     * @param host          the virtual host
     * @param login         the login (may be null)
     * @param passcode      the passcode (may be null)
     * @param heartbeatSend the client's send heart-beat capability (ms), 0 to disable
     * @param heartbeatRecv the client's receive heart-beat desire (ms), 0 to disable
     * @return the CONNECTED frame from the broker
     * @throws StompProtocolException if connection fails
     */
    public StompFrame connect(String host, String login, String passcode,
                              int heartbeatSend, int heartbeatRecv) {
        var headers = new StompHeaders();
        headers.put(StompHeaders.ACCEPT_VERSION, "1.0,1.1,1.2");
        headers.put(StompHeaders.HOST, host != null ? host : "/");
        if (login != null) headers.put(StompHeaders.LOGIN, login);
        if (passcode != null) headers.put(StompHeaders.PASSCODE, passcode);
        if (heartbeatSend > 0 || heartbeatRecv > 0) {
            headers.put(StompHeaders.HEART_BEAT,
                    HeartbeatMonitor.formatHeartbeat(heartbeatSend, heartbeatRecv));
        }

        transport.send(new StompFrame(StompCommand.STOMP, headers));
        session.setState(StompSession.State.CONNECTING);

        // Wait for CONNECTED
        StompFrame response = transport.receive();
        if (response.command() == StompCommand.ERROR) {
            session.setState(StompSession.State.DISCONNECTED);
            throw new StompProtocolException("Connection refused: " + response.bodyAsText());
        }
        if (response.command() != StompCommand.CONNECTED) {
            throw new StompProtocolException("Expected CONNECTED, got " + response.command());
        }

        session.setState(StompSession.State.CONNECTED);
        session.setNegotiatedVersion(response.header(StompHeaders.VERSION));
        session.setServerName(response.header(StompHeaders.SERVER));
        session.setLogin(login);

        // Heart-beat negotiation
        String serverHeartbeat = response.header(StompHeaders.HEART_BEAT);
        if (serverHeartbeat != null) {
            int[] serverHb = HeartbeatMonitor.parseHeartbeat(serverHeartbeat);
            int[] negotiated = HeartbeatMonitor.negotiate(
                    heartbeatSend, heartbeatRecv, serverHb[0], serverHb[1]);
            heartbeatMonitor.start(negotiated[0], negotiated[1]);
            session.setClientHeartbeat(heartbeatSend, heartbeatRecv);
            session.setServerHeartbeat(serverHb[0], serverHb[1]);
        }

        // Start receiver thread
        startReceiver();

        LOG.debug("Connected to broker (version {})", session.getNegotiatedVersion());
        return response;
    }

    /**
     * Connects to the STOMP broker with default settings (no auth, no heart-beat).
     *
     * @param host the virtual host
     * @return the CONNECTED frame
     */
    public StompFrame connect(String host) {
        return connect(host, null, null, 0, 0);
    }

    /**
     * Sends a message to a destination.
     *
     * @param destination the destination
     * @param body        the message body
     * @param contentType the content type (may be null for text/plain)
     */
    public void send(String destination, String body, String contentType) {
        send(destination, body.getBytes(java.nio.charset.StandardCharsets.UTF_8), contentType);
    }

    /**
     * Sends a binary message to a destination.
     *
     * @param destination the destination
     * @param body        the message body bytes
     * @param contentType the content type (may be null)
     */
    public void send(String destination, byte[] body, String contentType) {
        checkConnected();
        var headers = new StompHeaders();
        headers.put(StompHeaders.DESTINATION, destination);
        if (contentType != null) {
            headers.put(StompHeaders.CONTENT_TYPE, contentType);
        }
        if (body.length > 0) {
            headers.put(StompHeaders.CONTENT_LENGTH, String.valueOf(body.length));
        }
        transport.send(new StompFrame(StompCommand.SEND, headers, body));
        heartbeatMonitor.markSent();
    }

    /**
     * Sends a message to a destination within a transaction.
     *
     * @param destination   the destination
     * @param body          the message body
     * @param contentType   the content type
     * @param transactionId the transaction identifier
     */
    public void send(String destination, String body, String contentType, String transactionId) {
        checkConnected();
        var headers = new StompHeaders();
        headers.put(StompHeaders.DESTINATION, destination);
        if (contentType != null) {
            headers.put(StompHeaders.CONTENT_TYPE, contentType);
        }
        headers.put(StompHeaders.TRANSACTION, transactionId);
        byte[] bodyBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bodyBytes.length > 0) {
            headers.put(StompHeaders.CONTENT_LENGTH, String.valueOf(bodyBytes.length));
        }
        transport.send(new StompFrame(StompCommand.SEND, headers, bodyBytes));
        heartbeatMonitor.markSent();
    }

    /**
     * Sends a message and requests a receipt.
     *
     * @param destination the destination
     * @param body        the message body
     * @param contentType the content type
     * @return a future that completes when the receipt is received
     */
    public CompletableFuture<StompFrame> sendWithReceipt(String destination, String body, String contentType) {
        checkConnected();
        String receiptId = "receipt-" + receiptCounter.incrementAndGet();
        var future = new CompletableFuture<StompFrame>();
        receiptFutures.put(receiptId, future);

        var headers = new StompHeaders();
        headers.put(StompHeaders.DESTINATION, destination);
        headers.put(StompHeaders.RECEIPT, receiptId);
        if (contentType != null) {
            headers.put(StompHeaders.CONTENT_TYPE, contentType);
        }
        byte[] bodyBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bodyBytes.length > 0) {
            headers.put(StompHeaders.CONTENT_LENGTH, String.valueOf(bodyBytes.length));
        }
        transport.send(new StompFrame(StompCommand.SEND, headers, bodyBytes));
        heartbeatMonitor.markSent();
        return future;
    }

    /**
     * Subscribes to a destination.
     *
     * @param destination the destination to subscribe to
     * @param ackMode     the acknowledgment mode ("auto", "client", "client-individual")
     * @param handler     the message handler
     * @return the subscription identifier
     */
    public String subscribe(String destination, String ackMode, Consumer<StompFrame> handler) {
        checkConnected();
        String subId = "sub-" + subscriptionCounter.incrementAndGet();
        subscriptionHandlers.put(subId, handler);

        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, subId);
        headers.put(StompHeaders.DESTINATION, destination);
        headers.put(StompHeaders.ACK, ackMode);

        transport.send(new StompFrame(StompCommand.SUBSCRIBE, headers));
        session.addSubscription(subId, destination);
        heartbeatMonitor.markSent();

        LOG.debug("Subscribed to {} (id={}, ack={})", destination, subId, ackMode);
        return subId;
    }

    /**
     * Subscribes to a destination with auto acknowledgment.
     *
     * @param destination the destination
     * @param handler     the message handler
     * @return the subscription identifier
     */
    public String subscribe(String destination, Consumer<StompFrame> handler) {
        return subscribe(destination, "auto", handler);
    }

    /**
     * Unsubscribes from a subscription.
     *
     * @param subscriptionId the subscription identifier
     */
    public void unsubscribe(String subscriptionId) {
        checkConnected();
        subscriptionHandlers.remove(subscriptionId);
        session.removeSubscription(subscriptionId);

        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, subscriptionId);
        transport.send(new StompFrame(StompCommand.UNSUBSCRIBE, headers));
        heartbeatMonitor.markSent();
    }

    /**
     * Acknowledges a message.
     *
     * @param ackId the ack identifier from the MESSAGE frame
     */
    public void ack(String ackId) {
        checkConnected();
        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, ackId);
        transport.send(new StompFrame(StompCommand.ACK, headers));
        heartbeatMonitor.markSent();
    }

    /**
     * Acknowledges a message within a transaction.
     *
     * @param ackId         the ack identifier
     * @param transactionId the transaction identifier
     */
    public void ack(String ackId, String transactionId) {
        checkConnected();
        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, ackId);
        headers.put(StompHeaders.TRANSACTION, transactionId);
        transport.send(new StompFrame(StompCommand.ACK, headers));
        heartbeatMonitor.markSent();
    }

    /**
     * Negative acknowledges a message.
     *
     * @param ackId the ack identifier from the MESSAGE frame
     */
    public void nack(String ackId) {
        checkConnected();
        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, ackId);
        transport.send(new StompFrame(StompCommand.NACK, headers));
        heartbeatMonitor.markSent();
    }

    /**
     * Begins a transaction.
     *
     * @param transactionId the transaction identifier
     */
    public void begin(String transactionId) {
        checkConnected();
        var headers = new StompHeaders();
        headers.put(StompHeaders.TRANSACTION, transactionId);
        transport.send(new StompFrame(StompCommand.BEGIN, headers));
        session.beginTransaction(transactionId);
        heartbeatMonitor.markSent();
    }

    /**
     * Commits a transaction.
     *
     * @param transactionId the transaction identifier
     */
    public void commit(String transactionId) {
        checkConnected();
        var headers = new StompHeaders();
        headers.put(StompHeaders.TRANSACTION, transactionId);
        transport.send(new StompFrame(StompCommand.COMMIT, headers));
        session.endTransaction(transactionId);
        heartbeatMonitor.markSent();
    }

    /**
     * Aborts a transaction.
     *
     * @param transactionId the transaction identifier
     */
    public void abort(String transactionId) {
        checkConnected();
        var headers = new StompHeaders();
        headers.put(StompHeaders.TRANSACTION, transactionId);
        transport.send(new StompFrame(StompCommand.ABORT, headers));
        session.endTransaction(transactionId);
        heartbeatMonitor.markSent();
    }

    /**
     * Disconnects from the broker gracefully.
     *
     * @return a future that completes when the receipt for DISCONNECT is received
     */
    public CompletableFuture<StompFrame> disconnect() {
        if (!session.isConnected()) {
            return CompletableFuture.completedFuture(null);
        }

        String receiptId = "disconnect-receipt";
        var future = new CompletableFuture<StompFrame>();
        receiptFutures.put(receiptId, future);

        session.setState(StompSession.State.DISCONNECTING);
        var headers = new StompHeaders();
        headers.put(StompHeaders.RECEIPT, receiptId);
        transport.send(new StompFrame(StompCommand.DISCONNECT, headers));
        return future;
    }

    /**
     * Registers an error handler for ERROR frames from the broker.
     *
     * @param handler the error handler
     */
    public void onError(Consumer<StompFrame> handler) {
        this.errorHandler = handler;
    }

    /**
     * Returns the session for this client.
     *
     * @return the session
     */
    public StompSession getSession() {
        return session;
    }

    /**
     * Returns the heart-beat monitor.
     *
     * @return the heart-beat monitor
     */
    public HeartbeatMonitor getHeartbeatMonitor() {
        return heartbeatMonitor;
    }

    /**
     * Returns whether the client is connected.
     *
     * @return {@code true} if connected
     */
    public boolean isConnected() {
        return session.isConnected() && transport.isOpen();
    }

    @Override
    public void close() {
        heartbeatMonitor.stop();
        session.clear();
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
        transport.close();
    }

    /**
     * Starts the background receiver thread.
     */
    private void startReceiver() {
        receiverThread = Thread.startVirtualThread(() -> {
            try {
                while ((session.isConnected()
                        || session.getState() == StompSession.State.DISCONNECTING)
                        && transport.isOpen()) {
                    StompFrame frame;
                    try {
                        frame = transport.receive();
                    } catch (Exception e) {
                        if (session.isConnected()) {
                            LOG.debug("Receiver error: {}", e.getMessage());
                        }
                        break;
                    }

                    if (frame == null) break;

                    if (frame.isHeartbeat()) {
                        heartbeatMonitor.markReceived();
                        continue;
                    }

                    switch (frame.command()) {
                        case MESSAGE -> {
                            heartbeatMonitor.markReceived();
                            String subId = frame.header(StompHeaders.SUBSCRIPTION);
                            var handler = subId != null ? subscriptionHandlers.get(subId) : null;
                            if (handler != null) {
                                try {
                                    handler.accept(frame);
                                } catch (Exception e) {
                                    LOG.debug("Handler error for subscription {}: {}", subId, e.getMessage());
                                }
                            }
                        }
                        case RECEIPT -> {
                            heartbeatMonitor.markReceived();
                            String receiptId = frame.header(StompHeaders.RECEIPT_ID);
                            if (receiptId != null) {
                                var future = receiptFutures.remove(receiptId);
                                if (future != null) {
                                    future.complete(frame);
                                }
                                session.confirmReceipt(receiptId);

                                // If this was the disconnect receipt, clean up
                                if (session.getState() == StompSession.State.DISCONNECTING) {
                                    session.setState(StompSession.State.DISCONNECTED);
                                    break;
                                }
                            }
                        }
                        case ERROR -> {
                            heartbeatMonitor.markReceived();
                            var handler = errorHandler;
                            if (handler != null) {
                                handler.accept(frame);
                            }
                            errorQueue.offer(frame);

                            // Complete any pending receipt future with error
                            String receiptId = frame.header(StompHeaders.RECEIPT_ID);
                            if (receiptId != null) {
                                var future = receiptFutures.remove(receiptId);
                                if (future != null) {
                                    future.completeExceptionally(
                                            new StompProtocolException("Error: " + frame.bodyAsText()));
                                }
                            }
                        }
                        default -> LOG.debug("Unexpected frame: {}", frame.command());
                    }
                }
            } catch (Exception e) {
                if (session.isConnected()) {
                    LOG.debug("Receiver thread ended: {}", e.getMessage());
                }
            }
        });
    }

    private void checkConnected() {
        if (!session.isConnected()) {
            throw new IllegalStateException("Not connected");
        }
    }
}
