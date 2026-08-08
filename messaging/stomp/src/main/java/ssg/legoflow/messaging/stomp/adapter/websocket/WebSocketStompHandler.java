package ssg.legoflow.messaging.stomp.adapter.websocket;

import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.messaging.stomp.core.StompBroker;

/**
 * HTTP upgrade handler for STOMP-over-WebSocket connections.
 *
 * <p>Creates a {@link WebSocketStompTransport} for each new WebSocket session
 * and registers it with the {@link StompBroker} for frame processing.
 *
 * <p>The STOMP-over-WebSocket subprotocol is typically identified as
 * {@code v12.stomp} in the WebSocket handshake.
 *
 * @since 0.1.0
 */
public class WebSocketStompHandler {

    /** Standard STOMP WebSocket subprotocol identifier. */
    public static final String STOMP_SUBPROTOCOL = "v12.stomp";

    private final StompBroker broker;

    /**
     * Creates a new WebSocket STOMP handler.
     *
     * @param broker the STOMP broker to handle connections
     */
    public WebSocketStompHandler(StompBroker broker) {
        this.broker = broker;
    }

    /**
     * Handles a new WebSocket session for STOMP communication.
     *
     * <p>Creates a {@link WebSocketStompTransport} and registers it with the broker.
     *
     * @param session the WebSocket session
     * @return the created transport
     */
    public WebSocketStompTransport handleSession(WebSocketSession session) {
        var transport = new WebSocketStompTransport(session);
        broker.accept(transport);
        return transport;
    }

    /**
     * Returns the STOMP broker used by this handler.
     *
     * @return the broker
     */
    public StompBroker getBroker() {
        return broker;
    }

    /**
     * Returns the WebSocket subprotocol identifier for STOMP 1.2.
     *
     * @return the subprotocol string
     */
    public String getSubprotocol() {
        return STOMP_SUBPROTOCOL;
    }
}
