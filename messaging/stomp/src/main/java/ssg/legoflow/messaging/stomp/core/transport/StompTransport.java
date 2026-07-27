package ssg.legoflow.messaging.stomp.core.transport;

import ssg.legoflow.messaging.stomp.core.StompFrame;

/**
 * Service Provider Interface for STOMP frame transport.
 *
 * <p>Implementations provide the actual mechanism for sending and receiving STOMP frames
 * over a specific transport layer (e.g., raw TCP, WebSocket, in-memory queues).
 *
 * <p>The transport is responsible for frame boundary detection (NULL byte for TCP,
 * message boundaries for WebSocket) and encoding/decoding frames to/from their
 * wire format.
 *
 * @since 1.0.0
 */
public interface StompTransport {

    /**
     * Sends a STOMP frame through this transport.
     *
     * @param frame the frame to send
     */
    void send(StompFrame frame);

    /**
     * Receives the next STOMP frame from this transport, blocking until one is available.
     *
     * @return the received frame
     */
    StompFrame receive();

    /**
     * Closes this transport and releases associated resources.
     */
    void close();

    /**
     * Returns whether this transport is currently open and usable.
     *
     * @return {@code true} if the transport is open
     */
    boolean isOpen();
}
