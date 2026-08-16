package ssg.legoflow.network.cluster.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * In-memory transport for testing cluster communication.
 *
 * Routes messages between registered ClusterNode instances without network I/O.
 * Supports point-to-point sends and broadcasts.
 */
public class InMemoryClusterTransport implements ClusterTransport {

    private final Map<String, BiConsumer<String, byte[]>> receivers = new ConcurrentHashMap<>();
    private final Map<String, List<byte[]>> messageLog = new ConcurrentHashMap<>();
    private volatile boolean available = true;

    /**
     * Registers a message handler for a node.
     *
     * @param nodeId  the node ID
     * @param handler receives (senderId, payload) pairs
     */
    void registerReceiver(String nodeId, BiConsumer<String, byte[]> handler) {
        receivers.put(nodeId, handler);
    }

    /**
     * Removes a node's receiver.
     */
    void unregisterReceiver(String nodeId) {
        receivers.remove(nodeId);
    }

    /**
     * Returns messages received by the given node.
     */
    List<byte[]> getMessages(String nodeId) {
        return messageLog.computeIfAbsent(nodeId, k -> new ArrayList<>());
    }

    /**
     * Clears the message log for a node.
     */
    void clearLog(String nodeId) {
        messageLog.remove(nodeId);
    }

    /**
     * Clears all message logs.
     */
    void clearAllLogs() {
        messageLog.clear();
    }

    /**
     * Simulates a network failure for a node (drops all messages to it).
     */
    void setNodeUnavailable(String nodeId) {
        receivers.remove(nodeId);
    }

    /**
     * Restores a node's connectivity.
     */
    void restoreNode(String nodeId, BiConsumer<String, byte[]> handler) {
        receivers.put(nodeId, handler);
    }

    @Override
    public CompletableFuture<Void> send(ClusterNode target, byte[] payload) {
        if (!available) {
            return CompletableFuture.failedFuture(new IllegalStateException("Transport unavailable"));
        }
        BiConsumer<String, byte[]> handler = receivers.get(target.id());
        if (handler == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "No receiver for node: " + target.id()));
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            handler.accept(localNodeId(), payload);
            messageLog.computeIfAbsent(target.id(), k -> new java.util.ArrayList<>()).add(payload.clone());
            future.complete(null);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public CompletableFuture<Void> broadcast(ClusterNode sender, byte[] payload) {
        if (!available) {
            return CompletableFuture.failedFuture(new IllegalStateException("Transport unavailable"));
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<String, BiConsumer<String, byte[]>> entry : receivers.entrySet()) {
            if (!entry.getKey().equals(sender.id())) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                try {
                    entry.getValue().accept(sender.id(), payload);
                    messageLog.computeIfAbsent(entry.getKey(), k -> new java.util.ArrayList<>()).add(payload.clone());
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
                futures.add(future);
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * Simulates transport failure.
     */
    void fail() {
        this.available = false;
    }

    /**
     * Restores transport availability.
     */
    void recover() {
        this.available = true;
    }

    @Override
    public void close() {
        this.available = false;
        receivers.clear();
        messageLog.clear();
    }

    private String localNodeId() {
        return "test";
    }
}
