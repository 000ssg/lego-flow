package ssg.legoflow.wamp.core.transport;

import ssg.legoflow.wamp.core.WampMessage;

/**
 * Service Provider Interface for WAMP message transport.
 * Implementations provide the actual mechanism for sending and receiving WAMP messages
 * (e.g. WebSocket, in-memory queues, raw TCP).
 *
 * @since 0.1.0
 */
public interface WampTransport {

    /**
     * Sends a WAMP message through this transport.
     *
     * @param msg the message to send
     */
    void send(WampMessage msg);

    /**
     * Receives the next WAMP message from this transport, blocking until one is available.
     *
     * @return the received message
     */
    WampMessage receive();

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
