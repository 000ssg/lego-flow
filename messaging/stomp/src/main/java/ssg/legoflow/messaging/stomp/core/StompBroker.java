package ssg.legoflow.messaging.stomp.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.messaging.stomp.core.transport.StompTransport;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
/**
 * STOMP 1.2 message broker.
 *
 * <p>Manages destination routing, subscription management, transactions,
 * receipts, acknowledgment modes, and heart-beat negotiation. Transport-agnostic:
 * accepts connections via {@link StompTransport} and processes frames on virtual threads.
 *
 * <p>Acknowledgment modes:
 * <ul>
 *   <li>{@code auto} — messages are considered acknowledged as soon as they are sent</li>
 *   <li>{@code client} — cumulative ACK: acknowledging message N also acknowledges all
 *       messages up to N</li>
 *   <li>{@code client-individual} — per-message ACK: each message must be acknowledged
 *       individually</li>
 * </ul>
 *
 * @since 0.1.0
 */
public class StompBroker implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(StompBroker.class);
    private static final String SUPPORTED_VERSIONS = "1.0,1.1,1.2";
    private static final String SERVER_NAME = "LegoFlow-STOMP/1.2";

    private final StompBrokerConfig config;

    /** Broker's heart-beat capability: can send at 10s, wants to receive at 10s. */
    private int brokerSendCapability;
    private int brokerReceiveDesire;

    private final Map<String, StompSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, StompTransport> transports = new ConcurrentHashMap<>();
    private final Map<String, HeartbeatMonitor> heartbeats = new ConcurrentHashMap<>();

    // Subscriptions: destination → priority queue of QueuedSubscription
    private final Map<String, CopyOnWriteArrayList<QueuedSubscription>> destinationSubscriptions = new ConcurrentHashMap<>();

    // Subscription lookup: sessionId:subscriptionId → QueuedSubscription
    private final Map<String, QueuedSubscription> subscriptionIndex = new ConcurrentHashMap<>();

    // Transactions: sessionId:transactionId → StompTransaction
    private final Map<String, StompTransaction> transactions = new ConcurrentHashMap<>();

    // Pending ACKs: ackId → PendingAck
    private final Map<String, PendingAck> pendingAcks = new ConcurrentHashMap<>();

    // Ack ordering per subscription: sessionId:subscriptionId → list of ack IDs in order
    private final Map<String, CopyOnWriteArrayList<String>> ackOrder = new ConcurrentHashMap<>();

    private final AtomicLong sessionCounter = new AtomicLong(0);
    private volatile boolean running = true;

    /** Protocol flow listener — set to null for no-ops. */
    private volatile StompEventListener listener = null;

    /**
     * Sets the protocol event listener.
     *
     * @param listener the listener, or {@code null} to disable
     */
    public void setListener(StompEventListener listener) {
        this.listener = listener;
    }

    /**
     * Returns the current protocol event listener, or {@link StompEventListener#noOp()} if none.
     */
    public StompEventListener getListener() {
        return listener != null ? listener : StompEventListener.noOp();
    }

    /**
     * Internal subscription record with selector, priority queue, and max-size.
     */
    record QueuedSubscription(String sessionId, String subscriptionId, String destination,
                               String ackMode, String selector,
                               LinkedBlockingQueue<StompFrame> messageQueue,
                               int maxQueueSize) {
    }

    /**
     * Internal pending acknowledgment record.
     */
    record PendingAck(String ackId, String sessionId, String subscriptionId, StompFrame message) {
    }

    /**
     * Creates a broker with default settings (no auth, no ACL, no persistence).
     */
    public StompBroker() {
        this(StompBrokerConfig.defaults());
    }

    /**
     * Creates a broker with the given configuration.
     *
     * @param config the broker configuration
     */
    public StompBroker(StompBrokerConfig config) {
        this.config = Objects.requireNonNull(config);
        this.brokerSendCapability = config.heartbeatSend();
        this.brokerReceiveDesire = config.heartbeatReceive();
    }

    /**
     * Sets the broker's heart-beat capabilities.
     *
     * @param sendCapability  smallest interval (ms) the broker can send heart-beats (0 = cannot)
     * @param receiveDesire   smallest interval (ms) the broker wants to receive (0 = none)
     */
    public void setHeartbeatCapability(int sendCapability, int receiveDesire) {
        this.brokerSendCapability = sendCapability;
        this.brokerReceiveDesire = receiveDesire;
    }

    /**
     * Accepts a new STOMP connection and processes frames on a virtual thread.
     *
     * @param transport the transport for this connection
     */
    public void accept(StompTransport transport) {
        Thread.startVirtualThread(() -> handleConnection(transport));
    }

    /**
     * Handles the lifecycle of a single client connection.
     */
    private void handleConnection(StompTransport transport) {
        String sessionId = null;
        boolean gracefulDisconnect = false;
        try {
            while (running && transport.isOpen()) {
                StompFrame frame = transport.receive();
                if (frame == null) break;

                if (frame.isHeartbeat()) {
                    if (sessionId != null) {
                        var hb = heartbeats.get(sessionId);
                        if (hb != null) hb.markReceived();
                    }
                    continue;
                }

                switch (frame.command()) {
                    case CONNECT, STOMP -> sessionId = handleConnect(transport, frame);
                    case SEND -> handleSend(sessionId, frame);
                    case SUBSCRIBE -> handleSubscribe(sessionId, frame);
                    case UNSUBSCRIBE -> handleUnsubscribe(sessionId, frame);
                    case ACK -> handleAck(sessionId, frame);
                    case NACK -> handleNack(sessionId, frame);
                    case BEGIN -> handleBegin(sessionId, frame);
                    case COMMIT -> handleCommit(sessionId, frame);
                    case ABORT -> handleAbort(sessionId, frame);
                    case DISCONNECT -> {
                        handleDisconnect(sessionId, frame);
                        gracefulDisconnect = true;
                        return;
                    }
                    default -> sendError(sessionId, "Unsupported command: " + frame.command(), null);
                }
            }
        } catch (Exception e) {
            LOG.debug("Connection error for session {}: {}", sessionId, e.getMessage());
        } finally {
            if (sessionId != null) {
                cleanupSession(sessionId, !gracefulDisconnect);
            }
        }
    }

    /**
     * Handles CONNECT/STOMP frame.
     */
    private String handleConnect(StompTransport transport, StompFrame frame) {
        // Version negotiation
        String acceptVersion = frame.header(StompHeaders.ACCEPT_VERSION);
        String negotiatedVersion = negotiateVersion(acceptVersion);
        if (negotiatedVersion == null) {
            var errorHeaders = new StompHeaders();
            errorHeaders.put(StompHeaders.VERSION, SUPPORTED_VERSIONS);
            errorHeaders.put(StompHeaders.CONTENT_TYPE, "text/plain");
            var errorFrame = StompFrame.withText(StompCommand.ERROR, errorHeaders,
                    "Supported protocol versions are " + SUPPORTED_VERSIONS);
            transport.send(errorFrame);
            transport.close();
            return null;
        }

        // Authentication
        String login = frame.header(StompHeaders.LOGIN);
        String passcode = frame.header(StompHeaders.PASSCODE);
        if (config.authenticator() != null && !config.authenticator().authenticate(login, passcode)) {
            var errorHeaders = new StompHeaders();
            errorHeaders.put(StompHeaders.CONTENT_TYPE, "text/plain");
            var errorFrame = StompFrame.withText(StompCommand.ERROR, errorHeaders,
                    "Bad login or passcode");
            transport.send(errorFrame);
            transport.close();
            return null;
        }

        String sessionId = "session-" + sessionCounter.incrementAndGet();
        var session = new StompSession(sessionId);
        session.setNegotiatedVersion(negotiatedVersion);
        session.setLogin(login);
        session.setState(StompSession.State.CONNECTED);

        sessions.put(sessionId, session);
        transports.put(sessionId, transport);

        // Heart-beat negotiation
        var connectedHeaders = new StompHeaders();
        connectedHeaders.put(StompHeaders.VERSION, negotiatedVersion);
        connectedHeaders.put(StompHeaders.SERVER, SERVER_NAME);
        connectedHeaders.put(StompHeaders.SESSION, sessionId);

        String clientHeartbeat = frame.header(StompHeaders.HEART_BEAT);
        if (clientHeartbeat != null) {
            int[] clientHb = HeartbeatMonitor.parseHeartbeat(clientHeartbeat);
            session.setClientHeartbeat(clientHb[0], clientHb[1]);

            // Negotiate from server perspective
            int[] negotiated = HeartbeatMonitor.negotiate(
                    brokerSendCapability, brokerReceiveDesire,
                    clientHb[0], clientHb[1]);
            // Note: from server perspective, we reverse the negotiate call
            int[] serverNegotiated = HeartbeatMonitor.negotiate(
                    clientHb[0], clientHb[1],
                    brokerSendCapability, brokerReceiveDesire);
            session.setServerHeartbeat(brokerSendCapability, brokerReceiveDesire);

            connectedHeaders.put(StompHeaders.HEART_BEAT,
                    HeartbeatMonitor.formatHeartbeat(brokerSendCapability, brokerReceiveDesire));

            // Start heart-beat monitor with negotiated values
            var hbMonitor = new HeartbeatMonitor();
            hbMonitor.start(serverNegotiated[0], serverNegotiated[1]);
            heartbeats.put(sessionId, hbMonitor);
        }

        transport.send(new StompFrame(StompCommand.CONNECTED, connectedHeaders));
        LOG.debug("Session {} connected (version {})", sessionId, negotiatedVersion);
        StompEventListener ev = listener;
        if (ev != null) {
            ev.onEvent(StompEventListener.EventType.SESSION_CONNECTED, sessionId, negotiatedVersion);
        }
        return sessionId;
    }

    /**
     * Handles SEND frame.
     */
    private void handleSend(String sessionId, StompFrame frame) {
        if (!checkConnected(sessionId)) return;

        String destination = frame.header(StompHeaders.DESTINATION);
        if (destination == null) {
            sendError(sessionId, "SEND frame missing destination header", frame.header(StompHeaders.RECEIPT));
            return;
        }

        // ACL check
        var session = sessions.get(sessionId);
        if (config.aclChecker() != null && session != null) {
            if (!config.aclChecker().check(session.getLogin(), destination, "send")) {
                sendError(sessionId, "Access denied: cannot send to " + destination,
                        frame.header(StompHeaders.RECEIPT));
                return;
            }
        }

        String transactionId = frame.header(StompHeaders.TRANSACTION);
        if (transactionId != null) {
            String txKey = sessionId + ":" + transactionId;
            var tx = transactions.get(txKey);
            if (tx == null) {
                sendError(sessionId, "Transaction " + transactionId + " not started",
                        frame.header(StompHeaders.RECEIPT));
                return;
            }
            tx.buffer(frame);
        } else {
            deliverMessage(destination, frame, sessionId);
        }

        sendReceipt(sessionId, frame);
    }

    /**
     * Delivers a message to all subscribers of a destination.
     */
    void deliverMessage(String destination, StompFrame sendFrame, String senderSessionId) {
        var subs = destinationSubscriptions.get(destination);
        if (subs == null) return;

        int priority = Integer.parseInt(sendFrame.headers().getOrDefault("priority", "4"));

        for (var sub : subs) {
            var session = sessions.get(sub.sessionId());
            var transport = transports.get(sub.sessionId());
            if (session == null || transport == null || !transport.isOpen()) continue;

            // Selector filtering
            if (sub.selector() != null && !evaluateSelector(sub.selector(), sendFrame)) {
                continue;
            }

            // Max-size check
            if (sub.maxQueueSize() > 0 && sub.messageQueue().size() >= sub.maxQueueSize()) {
                LOG.debug("Queue full for subscription {} on {}", sub.subscriptionId(), destination);
                continue;
            }

            String messageId = session.nextMessageId();
            var msgHeaders = new StompHeaders();
            msgHeaders.put(StompHeaders.DESTINATION, destination);
            msgHeaders.put(StompHeaders.MESSAGE_ID, messageId);
            msgHeaders.put(StompHeaders.SUBSCRIPTION, sub.subscriptionId());
            if (priority >= 0 && priority <= 9) {
                msgHeaders.put("priority", String.valueOf(priority));
            }

            // Copy content-type if present
            String contentType = sendFrame.header(StompHeaders.CONTENT_TYPE);
            if (contentType != null) {
                msgHeaders.put(StompHeaders.CONTENT_TYPE, contentType);
            }

            // Add ack header for client/client-individual modes
            String ackId = messageId;
            if (!"auto".equals(sub.ackMode())) {
                msgHeaders.put(StompHeaders.ACK, ackId);
                var pendingAck = new PendingAck(ackId, sub.sessionId(), sub.subscriptionId(),
                        new StompFrame(StompCommand.MESSAGE, msgHeaders, sendFrame.body()));
                pendingAcks.put(ackId, pendingAck);

                // Track ordering for cumulative ack
                String orderKey = sub.sessionId() + ":" + sub.subscriptionId();
                ackOrder.computeIfAbsent(orderKey, k -> new CopyOnWriteArrayList<>()).add(ackId);
            }

            // Add content-length for binary bodies
            if (sendFrame.body().length > 0) {
                msgHeaders.put(StompHeaders.CONTENT_LENGTH, String.valueOf(sendFrame.body().length));
            }

            var messageFrame = new StompFrame(StompCommand.MESSAGE, msgHeaders, sendFrame.body());

            // Store in queue for persistence, then deliver
            if (config.persistenceAdapter() != null) {
                config.persistenceAdapter().queueMessage(destination, messageFrame);
            }
            sub.messageQueue().add(messageFrame);

            try {
                transport.send(messageFrame);
                StompEventListener ev = listener;
                if (ev != null) {
                    ev.onEvent(StompEventListener.EventType.MESSAGE_DELIVERED, sub.sessionId(), destination);
                }
            } catch (Exception e) {
                LOG.debug("Failed to deliver message to session {}: {}", sub.sessionId(), e.getMessage());
            }
        }
    }

    /**
     * Evaluates a STOMP selector expression against a message frame.
     * Supports simple comparison expressions: header &lt; op &gt; value
     * e.g., "priority &gt; 5", "type = 'alert'"
     */
    private boolean evaluateSelector(String selector, StompFrame frame) {
        try {
            // Simple selector: header op value
            if (selector.contains(">=")) return parseSelector(selector, ">=", frame);
            if (selector.contains("<=")) return parseSelector(selector, "<=", frame);
            if (selector.contains("=")) return parseSelector(selector, "=", frame);
            if (selector.contains(">")) return parseSelector(selector, ">", frame);
            if (selector.contains("<")) return parseSelector(selector, "<", frame);
            if (selector.contains("!=")) return parseSelector(selector, "!=", frame);
            // If no operator found, return true (accept)
            return true;
        } catch (Exception e) {
            LOG.debug("Selector evaluation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean parseSelector(String selector, String op, StompFrame frame) {
        int opIndex = selector.indexOf(op);
        if (opIndex < 0) return true;
        String header = selector.substring(0, opIndex).trim();
        String value = selector.substring(opIndex + op.length()).trim();
        // Remove quotes
        if (value.startsWith("'") && value.endsWith("'")) {
            value = value.substring(1, value.length() - 1);
        }

        String frameValue = frame.header(header);
        if (frameValue == null) return false;

        if (op.equals("=")) return value.equals(frameValue);
        if (op.equals("!=")) return !value.equals(frameValue);
        // Numeric comparison
        try {
            double fv = Double.parseDouble(frameValue);
            double sv = Double.parseDouble(value);
            return compare(fv, sv, op);
        } catch (NumberFormatException e) {
            // String comparison
            return compare(frameValue.compareTo(value), 0, op);
        }
    }

    private boolean compare(double a, double b, String op) {
        return switch (op) {
            case "=" -> a == b;
            case "!=" -> a != b;
            case ">" -> a > b;
            case "<" -> a < b;
            case ">=" -> a >= b;
            case "<=" -> a <= b;
            default -> true;
        };
    }

    private boolean compare(int cmp, int zero, String op) {
        return switch (op) {
            case "=" -> cmp == zero;
            case "!=" -> cmp != zero;
            case ">" -> cmp > zero;
            case "<" -> cmp < zero;
            case ">=" -> cmp >= zero;
            case "<=" -> cmp <= zero;
            default -> true;
        };
    }

    /**
     * Handles SUBSCRIBE frame.
     */
    private void handleSubscribe(String sessionId, StompFrame frame) {
        if (!checkConnected(sessionId)) return;

        String subId = frame.header(StompHeaders.ID);
        String destination = frame.header(StompHeaders.DESTINATION);
        if (subId == null || destination == null) {
            sendError(sessionId, "SUBSCRIBE requires 'id' and 'destination' headers",
                    frame.header(StompHeaders.RECEIPT));
            return;
        }

        // ACL check
        var session = sessions.get(sessionId);
        if (config.aclChecker() != null && session != null) {
            if (!config.aclChecker().check(session.getLogin(), destination, "subscribe")) {
                sendError(sessionId, "Access denied: cannot subscribe to " + destination,
                        frame.header(StompHeaders.RECEIPT));
                return;
            }
        }

        String ackMode = frame.headers().getOrDefault(StompHeaders.ACK, "auto");
        if (!"auto".equals(ackMode) && !"client".equals(ackMode) && !"client-individual".equals(ackMode)) {
            sendError(sessionId, "Invalid ack mode: " + ackMode, frame.header(StompHeaders.RECEIPT));
            return;
        }

        String selector = frame.header("selector");
        int maxQueueSize = config.defaultMaxQueueSize();

        var sub = new QueuedSubscription(sessionId, subId, destination, ackMode, selector,
                new LinkedBlockingQueue<>(), maxQueueSize);
        destinationSubscriptions.computeIfAbsent(destination, k -> new CopyOnWriteArrayList<>()).add(sub);
        subscriptionIndex.put(sessionId + ":" + subId, sub);

        if (session != null) {
            session.addSubscription(subId, destination);
        }

        sendReceipt(sessionId, frame);
        LOG.debug("Session {} subscribed to {} (id={}, ack={}, selector={}, max={})",
                sessionId, destination, subId, ackMode, selector, maxQueueSize);
    }

    /**
     * Handles UNSUBSCRIBE frame.
     */
    private void handleUnsubscribe(String sessionId, StompFrame frame) {
        if (!checkConnected(sessionId)) return;

        String subId = frame.header(StompHeaders.ID);
        if (subId == null) {
            sendError(sessionId, "UNSUBSCRIBE requires 'id' header", frame.header(StompHeaders.RECEIPT));
            return;
        }

        String subKey = sessionId + ":" + subId;
        var sub = subscriptionIndex.remove(subKey);
        if (sub != null) {
            var subs = destinationSubscriptions.get(sub.destination());
            if (subs != null) {
                subs.remove(sub);
            }
            var session = sessions.get(sessionId);
            if (session != null) {
                session.removeSubscription(subId);
            }
        }

        // Clean up pending acks for this subscription
        ackOrder.remove(subKey);

        sendReceipt(sessionId, frame);
        LOG.debug("Session {} unsubscribed (id={})", sessionId, subId);
    }

    /**
     * Handles ACK frame.
     */
    private void handleAck(String sessionId, StompFrame frame) {
        if (!checkConnected(sessionId)) return;

        String ackId = frame.header(StompHeaders.ID);
        if (ackId == null) {
            sendError(sessionId, "ACK requires 'id' header", frame.header(StompHeaders.RECEIPT));
            return;
        }

        String transactionId = frame.header(StompHeaders.TRANSACTION);
        if (transactionId != null) {
            String txKey = sessionId + ":" + transactionId;
            var tx = transactions.get(txKey);
            if (tx == null) {
                sendError(sessionId, "Transaction " + transactionId + " not started",
                        frame.header(StompHeaders.RECEIPT));
                return;
            }
            tx.buffer(frame);
        } else {
            processAck(sessionId, ackId);
        }

        sendReceipt(sessionId, frame);
    }

    /**
     * Processes an ACK, handling cumulative vs individual modes.
     */
    private void processAck(String sessionId, String ackId) {
        var pending = pendingAcks.get(ackId);
        if (pending == null) return;

        var sub = subscriptionIndex.get(sessionId + ":" + pending.subscriptionId());
        if (sub == null) {
            pendingAcks.remove(ackId);
            return;
        }

        if ("client".equals(sub.ackMode())) {
            // Cumulative: acknowledge this and all previous messages
            String orderKey = sessionId + ":" + pending.subscriptionId();
            var ordered = ackOrder.get(orderKey);
            if (ordered != null) {
                int idx = ordered.indexOf(ackId);
                if (idx >= 0) {
                    // Remove all acks up to and including this one
                    var toRemove = new java.util.ArrayList<>(ordered.subList(0, idx + 1));
                    for (String aid : toRemove) {
                        pendingAcks.remove(aid);
                        ordered.remove(aid);
                    }
                }
            }
        } else {
            // client-individual: just acknowledge this one
            pendingAcks.remove(ackId);
            String orderKey = sessionId + ":" + pending.subscriptionId();
            var ordered = ackOrder.get(orderKey);
            if (ordered != null) {
                ordered.remove(ackId);
            }
        }
    }

    /**
     * Handles NACK frame.
     */
    private void handleNack(String sessionId, StompFrame frame) {
        if (!checkConnected(sessionId)) return;

        String ackId = frame.header(StompHeaders.ID);
        if (ackId == null) {
            sendError(sessionId, "NACK requires 'id' header", frame.header(StompHeaders.RECEIPT));
            return;
        }

        String transactionId = frame.header(StompHeaders.TRANSACTION);
        if (transactionId != null) {
            String txKey = sessionId + ":" + transactionId;
            var tx = transactions.get(txKey);
            if (tx == null) {
                sendError(sessionId, "Transaction " + transactionId + " not started",
                        frame.header(StompHeaders.RECEIPT));
                return;
            }
            tx.buffer(frame);
        } else {
            processNack(sessionId, ackId);
        }

        sendReceipt(sessionId, frame);
    }

    /**
     * Processes a NACK — removes the pending ack (message was not consumed).
     */
    private void processNack(String sessionId, String ackId) {
        var pending = pendingAcks.remove(ackId);
        if (pending != null) {
            String orderKey = sessionId + ":" + pending.subscriptionId();
            var ordered = ackOrder.get(orderKey);
            if (ordered != null) {
                ordered.remove(ackId);
            }
        }
    }

    /**
     * Handles BEGIN frame.
     */
    private void handleBegin(String sessionId, StompFrame frame) {
        if (!checkConnected(sessionId)) return;

        String transactionId = frame.header(StompHeaders.TRANSACTION);
        if (transactionId == null) {
            sendError(sessionId, "BEGIN requires 'transaction' header", frame.header(StompHeaders.RECEIPT));
            return;
        }

        String txKey = sessionId + ":" + transactionId;
        if (transactions.containsKey(txKey)) {
            sendError(sessionId, "Transaction " + transactionId + " already active",
                    frame.header(StompHeaders.RECEIPT));
            return;
        }

        transactions.put(txKey, new StompTransaction(transactionId));
        var session = sessions.get(sessionId);
        if (session != null) {
            session.beginTransaction(transactionId);
        }

        sendReceipt(sessionId, frame);
        LOG.debug("Session {} began transaction {}", sessionId, transactionId);
    }

    /**
     * Handles COMMIT frame.
     */
    private void handleCommit(String sessionId, StompFrame frame) {
        if (!checkConnected(sessionId)) return;

        String transactionId = frame.header(StompHeaders.TRANSACTION);
        if (transactionId == null) {
            sendError(sessionId, "COMMIT requires 'transaction' header", frame.header(StompHeaders.RECEIPT));
            return;
        }

        String txKey = sessionId + ":" + transactionId;
        var tx = transactions.remove(txKey);
        if (tx == null) {
            sendError(sessionId, "Transaction " + transactionId + " not found",
                    frame.header(StompHeaders.RECEIPT));
            return;
        }

        // Apply all buffered frames
        List<StompFrame> buffered = tx.commit();
        for (var bufferedFrame : buffered) {
            switch (bufferedFrame.command()) {
                case SEND -> {
                    String dest = bufferedFrame.header(StompHeaders.DESTINATION);
                    if (dest != null) {
                        deliverMessage(dest, bufferedFrame, sessionId);
                    }
                }
                case ACK -> {
                    String ackId = bufferedFrame.header(StompHeaders.ID);
                    if (ackId != null) processAck(sessionId, ackId);
                }
                case NACK -> {
                    String ackId = bufferedFrame.header(StompHeaders.ID);
                    if (ackId != null) processNack(sessionId, ackId);
                }
                default -> { /* ignore */ }
            }
        }

        var session = sessions.get(sessionId);
        if (session != null) {
            session.endTransaction(transactionId);
        }

        sendReceipt(sessionId, frame);
        LOG.debug("Session {} committed transaction {} ({} frames)", sessionId, transactionId, buffered.size());
        StompEventListener ev = listener;
        if (ev != null) {
            ev.onEvent(StompEventListener.EventType.TRANSACTION_COMMITTED, sessionId, transactionId);
        }
    }

    /**
     * Handles ABORT frame.
     */
    private void handleAbort(String sessionId, StompFrame frame) {
        if (!checkConnected(sessionId)) return;

        String transactionId = frame.header(StompHeaders.TRANSACTION);
        if (transactionId == null) {
            sendError(sessionId, "ABORT requires 'transaction' header", frame.header(StompHeaders.RECEIPT));
            return;
        }

        String txKey = sessionId + ":" + transactionId;
        var tx = transactions.remove(txKey);
        if (tx == null) {
            sendError(sessionId, "Transaction " + transactionId + " not found",
                    frame.header(StompHeaders.RECEIPT));
            return;
        }

        tx.abort();
        var session = sessions.get(sessionId);
        if (session != null) {
            session.endTransaction(transactionId);
        }

        sendReceipt(sessionId, frame);
        LOG.debug("Session {} aborted transaction {}", sessionId, transactionId);
        StompEventListener ev = listener;
        if (ev != null) {
            ev.onEvent(StompEventListener.EventType.TRANSACTION_ABORTED, sessionId, transactionId);
        }
    }

    /**
     * Handles DISCONNECT frame.
     */
    private void handleDisconnect(String sessionId, StompFrame frame) {
        sendReceipt(sessionId, frame);

        var session = sessions.get(sessionId);
        if (session != null) {
            session.setState(StompSession.State.DISCONNECTED);
        }

        // Do not call cleanupSession here — the finally block in handleConnection
        // will do it after this method returns and the transport is properly flushed.
        LOG.debug("Session {} disconnected", sessionId);
        StompEventListener ev = listener;
        if (ev != null) {
            ev.onEvent(StompEventListener.EventType.SESSION_DISCONNECTED, sessionId, null);
        }
    }

    /**
     * Sends a RECEIPT frame if the original frame had a receipt header.
     */
    private void sendReceipt(String sessionId, StompFrame originalFrame) {
        String receiptId = originalFrame.header(StompHeaders.RECEIPT);
        if (receiptId != null && sessionId != null) {
            var receiptHeaders = new StompHeaders();
            receiptHeaders.put(StompHeaders.RECEIPT_ID, receiptId);
            var receiptFrame = new StompFrame(StompCommand.RECEIPT, receiptHeaders);
            var transport = transports.get(sessionId);
            if (transport != null && transport.isOpen()) {
                transport.send(receiptFrame);
            }
        }
    }

    /**
     * Sends an ERROR frame to a session.
     */
    void sendError(String sessionId, String message, String receiptId) {
        if (sessionId == null) return;
        var transport = transports.get(sessionId);
        if (transport == null || !transport.isOpen()) return;

        var headers = new StompHeaders();
        headers.put(StompHeaders.MESSAGE_HEADER, message);
        headers.put(StompHeaders.CONTENT_TYPE, "text/plain");
        if (receiptId != null) {
            headers.put(StompHeaders.RECEIPT_ID, receiptId);
        }

        var errorFrame = StompFrame.withText(StompCommand.ERROR, headers, message);
        transport.send(errorFrame);
        LOG.debug("Sent error to session {}: {}", sessionId, message);
    }

    /**
     * Checks whether a session is connected.
     */
    private boolean checkConnected(String sessionId) {
        if (sessionId == null) return false;
        var session = sessions.get(sessionId);
        return session != null && session.isConnected();
    }

    /**
     * Negotiates the best mutual STOMP version.
     */
    private String negotiateVersion(String acceptVersion) {
        if (acceptVersion == null || acceptVersion.isBlank()) {
            // No accept-version implies 1.0 only
            return "1.0";
        }

        String[] requested = acceptVersion.split(",");
        // Prefer highest version
        String best = null;
        for (String v : requested) {
            String trimmed = v.trim();
            if ("1.2".equals(trimmed)) return "1.2";
            if ("1.1".equals(trimmed)) best = "1.1";
            if ("1.0".equals(trimmed) && best == null) best = "1.0";
        }
        return best;
    }

    /**
     * Cleans up all resources associated with a session.
     *
     * @param sessionId      the session to clean up
     * @param closeTransport whether to close the transport (false for graceful disconnect
     *                       to let the client read the final RECEIPT before the socket closes)
     */
    private void cleanupSession(String sessionId, boolean closeTransport) {
        sessions.remove(sessionId);
        var transport = transports.remove(sessionId);
        var hb = heartbeats.remove(sessionId);
        if (hb != null) hb.stop();

        // Remove subscriptions
        subscriptionIndex.entrySet().removeIf(e -> e.getKey().startsWith(sessionId + ":"));
        for (var subs : destinationSubscriptions.values()) {
            subs.removeIf(s -> s.sessionId().equals(sessionId));
        }

        // Remove transactions
        transactions.entrySet().removeIf(e -> e.getKey().startsWith(sessionId + ":"));

        // Remove pending acks
        pendingAcks.entrySet().removeIf(e -> e.getValue().sessionId().equals(sessionId));
        ackOrder.entrySet().removeIf(e -> e.getKey().startsWith(sessionId + ":"));

        if (closeTransport && transport != null && transport.isOpen()) {
            try {
                transport.close();
            } catch (Exception e) {
                LOG.debug("Error closing transport for session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * Returns the current number of connected sessions.
     *
     * @return the session count
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * Returns the set of active destinations (those with at least one subscription).
     *
     * @return set of destination strings
     */
    public Set<String> getDestinations() {
        return Set.copyOf(destinationSubscriptions.keySet());
    }

    /**
     * Returns a session by ID.
     *
     * @param sessionId the session identifier
     * @return the session, or null
     */
    public StompSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Returns the number of subscriptions for a destination.
     *
     * @param destination the destination
     * @return the number of subscriptions
     */
    public int getSubscriptionCount(String destination) {
        var subs = destinationSubscriptions.get(destination);
        return subs != null ? subs.size() : 0;
    }

    @Override
    public void close() {
        running = false;
        for (var sessionId : Set.copyOf(sessions.keySet())) {
            cleanupSession(sessionId, true);
        }
    }
}
