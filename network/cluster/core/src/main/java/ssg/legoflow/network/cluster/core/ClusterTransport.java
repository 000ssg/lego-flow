package ssg.legoflow.network.cluster.core;

import java.util.concurrent.CompletableFuture;

/**
 * Transport-agnostic SPI for sending messages to cluster nodes.
 *
 * Implementations wrap specific transport mechanisms (TCP, UDP, gRPC, NATS, etc.)
 * to deliver serialized payloads to target nodes.
 */
public interface ClusterTransport extends AutoCloseable {

    /**
     * Sends a message to a specific node.
     *
     * @param target  the destination node
     * @param payload the message payload
     * @return a future completed when the message is delivered
     */
    CompletableFuture<Void> send(ClusterNode target, byte[] payload);

    /**
     * Broadcasts a message to all active members except the sender.
     *
     * @param sender  the node broadcasting
     * @param payload the message payload
     * @return a future completed when the broadcast is complete
     */
    CompletableFuture<Void> broadcast(ClusterNode sender, byte[] payload);

    /**
     * Returns whether this transport is currently active and able to send.
     */
    boolean isAvailable();

    @Override
    void close();
}
