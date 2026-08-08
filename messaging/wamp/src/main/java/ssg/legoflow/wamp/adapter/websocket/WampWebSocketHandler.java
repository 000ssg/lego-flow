package ssg.legoflow.wamp.adapter.websocket;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.websocket.WebSocketHandshake;

/**
 * HTTP upgrade handler for WAMP WebSocket connections.
 * Validates that the WebSocket handshake request specifies the {@code wamp.2.json} subprotocol
 * and produces the appropriate handshake response with the subprotocol header set.
 *
 * @since 0.1.0
 */
public class WampWebSocketHandler {

    /** The WAMP v2 JSON subprotocol identifier. */
    public static final String WAMP_SUBPROTOCOL = "wamp.2.json";

    private final WebSocketHandshake handshake = new WebSocketHandshake();

    /**
     * Checks whether the given HTTP request is a valid WAMP WebSocket upgrade request.
     *
     * @param request the HTTP request to check
     * @return {@code true} if this is a valid WAMP WebSocket upgrade request
     */
    public boolean isWampUpgrade(HttpRequest request) {
        if (!handshake.isWebSocketUpgrade(request)) {
            return false;
        }
        var subprotocol = request.getHeaders().get("sec-websocket-protocol");
        return subprotocol != null && subprotocol.contains(WAMP_SUBPROTOCOL);
    }

    /**
     * Creates the WebSocket handshake response with the WAMP subprotocol header.
     *
     * @param request the original upgrade request
     * @return the handshake response
     */
    public HttpResponse createUpgradeResponse(HttpRequest request) {
        var response = handshake.createHandshakeResponse(request);
        response.getHeaders().set("sec-websocket-protocol", WAMP_SUBPROTOCOL);
        return response;
    }

    /**
     * Returns the underlying WebSocket handshake handler.
     *
     * @return the handshake handler
     */
    public WebSocketHandshake getHandshake() {
        return handshake;
    }
}
