package ssg.legoflow.media.sip.transport;

import ssg.legoflow.media.sip.protocol.SipMessage;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * SIP transport abstraction per RFC 3261 section 18.
 *
 * <p>Defines the contract for sending and receiving SIP messages over
 * different transport protocols (UDP, TCP, TLS).
 *
 * @since 0.1.0
 */
public interface SipTransport extends AutoCloseable {

    /**
     * Sends a SIP message to the specified destination.
     *
     * @param message     the message to send
     * @param destination the destination address
     * @throws IOException if sending fails
     * @since 0.1.0
     */
    void send(SipMessage message, InetSocketAddress destination) throws IOException;

    /**
     * Returns the local address this transport is bound to.
     *
     * @return the local address
     * @since 0.1.0
     */
    InetSocketAddress localAddress();

    /**
     * Returns the transport protocol name (UDP, TCP, TLS).
     *
     * @return the protocol name
     * @since 0.1.0
     */
    String protocol();

    /**
     * Returns true if this transport uses a reliable protocol (TCP, TLS).
     *
     * @return true if reliable
     * @since 0.1.0
     */
    boolean isReliable();

    /**
     * Starts listening for incoming messages.
     *
     * @param listener the listener for received messages
     * @throws IOException if starting fails
     * @since 0.1.0
     */
    void start(SipTransportListener listener) throws IOException;

    /**
     * Listener for incoming SIP messages.
     *
     * @since 0.1.0
     */
    @FunctionalInterface
    interface SipTransportListener {

        /**
         * Called when a SIP message is received.
         *
         * @param message the received message
         * @param source  the source address
         * @since 0.1.0
         */
        void onMessage(SipMessage message, InetSocketAddress source);
    }
}
