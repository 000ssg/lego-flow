package ssg.legoflow.messaging.stomp.core;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages the lifecycle of a single STOMP session.
 *
 * <p>Session states: {@link State#CONNECTING} → {@link State#CONNECTED} →
 * {@link State#DISCONNECTING} → {@link State#DISCONNECTED}.
 *
 * <p>Tracks subscriptions, pending receipts, and active transactions for the session.
 *
 * @since 0.1.0
 */
public class StompSession {

    /**
     * Session lifecycle states.
     */
    public enum State {
        /** Initial state, CONNECT/STOMP sent but CONNECTED not received. */
        CONNECTING,
        /** Session established, frames can be exchanged. */
        CONNECTED,
        /** DISCONNECT sent, awaiting RECEIPT. */
        DISCONNECTING,
        /** Session ended. */
        DISCONNECTED
    }

    private final String sessionId;
    private volatile State state;
    private volatile String negotiatedVersion;
    private volatile String serverName;
    private volatile String login;

    private final Map<String, String> subscriptions = new ConcurrentHashMap<>();
    private final Set<String> activeTransactions = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingReceipts = ConcurrentHashMap.newKeySet();
    private final AtomicLong messageIdCounter = new AtomicLong(0);

    // Heart-beat negotiation
    private volatile int clientSendInterval;
    private volatile int clientReceiveInterval;
    private volatile int serverSendInterval;
    private volatile int serverReceiveInterval;

    /**
     * Creates a new session with the given identifier.
     *
     * @param sessionId the session identifier
     */
    public StompSession(String sessionId) {
        this.sessionId = sessionId;
        this.state = State.CONNECTING;
    }

    /**
     * Returns the session identifier.
     *
     * @return the session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Returns the current session state.
     *
     * @return the state
     */
    public State getState() {
        return state;
    }

    /**
     * Sets the session state.
     *
     * @param state the new state
     */
    public void setState(State state) {
        this.state = state;
    }

    /**
     * Returns whether the session is currently connected.
     *
     * @return {@code true} if in CONNECTED state
     */
    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    /**
     * Returns the negotiated protocol version.
     *
     * @return the version string (e.g. "1.2")
     */
    public String getNegotiatedVersion() {
        return negotiatedVersion;
    }

    /**
     * Sets the negotiated protocol version.
     *
     * @param version the version string
     */
    public void setNegotiatedVersion(String version) {
        this.negotiatedVersion = version;
    }

    /**
     * Returns the server name from the CONNECTED frame.
     *
     * @return the server name, or null
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Sets the server name.
     *
     * @param serverName the server identification string
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    /**
     * Returns the login used for this session.
     *
     * @return the login, or null
     */
    public String getLogin() {
        return login;
    }

    /**
     * Sets the login.
     *
     * @param login the login
     */
    public void setLogin(String login) {
        this.login = login;
    }

    // --- Subscriptions ---

    /**
     * Records a subscription.
     *
     * @param subscriptionId the subscription identifier
     * @param destination    the destination URI
     */
    public void addSubscription(String subscriptionId, String destination) {
        subscriptions.put(subscriptionId, destination);
    }

    /**
     * Removes a subscription.
     *
     * @param subscriptionId the subscription identifier
     * @return the destination that was subscribed, or null
     */
    public String removeSubscription(String subscriptionId) {
        return subscriptions.remove(subscriptionId);
    }

    /**
     * Returns the destination for a subscription.
     *
     * @param subscriptionId the subscription identifier
     * @return the destination, or null
     */
    public String getSubscriptionDestination(String subscriptionId) {
        return subscriptions.get(subscriptionId);
    }

    /**
     * Returns an unmodifiable view of current subscriptions.
     *
     * @return map of subscription ID to destination
     */
    public Map<String, String> getSubscriptions() {
        return Collections.unmodifiableMap(subscriptions);
    }

    // --- Transactions ---

    /**
     * Registers an active transaction.
     *
     * @param transactionId the transaction identifier
     */
    public void beginTransaction(String transactionId) {
        activeTransactions.add(transactionId);
    }

    /**
     * Removes an active transaction.
     *
     * @param transactionId the transaction identifier
     * @return {@code true} if the transaction was active
     */
    public boolean endTransaction(String transactionId) {
        return activeTransactions.remove(transactionId);
    }

    /**
     * Returns whether a transaction is active.
     *
     * @param transactionId the transaction identifier
     * @return {@code true} if active
     */
    public boolean hasTransaction(String transactionId) {
        return activeTransactions.contains(transactionId);
    }

    /**
     * Returns the set of active transaction IDs.
     *
     * @return unmodifiable set of transaction IDs
     */
    public Set<String> getActiveTransactions() {
        return Collections.unmodifiableSet(activeTransactions);
    }

    // --- Receipts ---

    /**
     * Registers a pending receipt.
     *
     * @param receiptId the receipt identifier
     */
    public void addPendingReceipt(String receiptId) {
        pendingReceipts.add(receiptId);
    }

    /**
     * Confirms a receipt was received.
     *
     * @param receiptId the receipt identifier
     * @return {@code true} if the receipt was pending
     */
    public boolean confirmReceipt(String receiptId) {
        return pendingReceipts.remove(receiptId);
    }

    /**
     * Returns the set of pending receipt IDs.
     *
     * @return unmodifiable set of receipt IDs
     */
    public Set<String> getPendingReceipts() {
        return Collections.unmodifiableSet(pendingReceipts);
    }

    // --- Message ID generation ---

    /**
     * Generates a unique message ID for this session.
     *
     * @return a unique message identifier
     */
    public String nextMessageId() {
        return sessionId + "-" + messageIdCounter.incrementAndGet();
    }

    // --- Heart-beat ---

    /**
     * Sets the client heart-beat values from the CONNECT frame.
     *
     * @param sendInterval    smallest interval (ms) the client can send heart-beats
     * @param receiveInterval smallest interval (ms) the client wants to receive heart-beats
     */
    public void setClientHeartbeat(int sendInterval, int receiveInterval) {
        this.clientSendInterval = sendInterval;
        this.clientReceiveInterval = receiveInterval;
    }

    /**
     * Sets the server heart-beat values from the CONNECTED frame.
     *
     * @param sendInterval    smallest interval (ms) the server can send heart-beats
     * @param receiveInterval smallest interval (ms) the server wants to receive heart-beats
     */
    public void setServerHeartbeat(int sendInterval, int receiveInterval) {
        this.serverSendInterval = sendInterval;
        this.serverReceiveInterval = receiveInterval;
    }

    /**
     * Returns the client's send heart-beat interval in milliseconds.
     *
     * @return interval in ms, 0 means cannot send
     */
    public int getClientSendInterval() {
        return clientSendInterval;
    }

    /**
     * Returns the client's desired receive interval in milliseconds.
     *
     * @return interval in ms, 0 means does not want to receive
     */
    public int getClientReceiveInterval() {
        return clientReceiveInterval;
    }

    /**
     * Returns the server's send heart-beat interval in milliseconds.
     *
     * @return interval in ms, 0 means cannot send
     */
    public int getServerSendInterval() {
        return serverSendInterval;
    }

    /**
     * Returns the server's desired receive interval in milliseconds.
     *
     * @return interval in ms, 0 means does not want to receive
     */
    public int getServerReceiveInterval() {
        return serverReceiveInterval;
    }

    /**
     * Clears all session state on disconnect.
     */
    public void clear() {
        subscriptions.clear();
        activeTransactions.clear();
        pendingReceipts.clear();
        state = State.DISCONNECTED;
    }

    @Override
    public String toString() {
        return "StompSession[id=" + sessionId + ", state=" + state + "]";
    }
}
