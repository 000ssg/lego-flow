package ssg.legoflow.http.websocket;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
/**
 * WebSocket subprotocol negotiation per RFC 6455 §11.5.
 *
 * <p>The client sends a list of requested subprotocols in the
 * {@code Sec-WebSocket-Protocol} header. The server selects one (or none)
 * and returns it in the response.
 *
 * @since 0.1.0
 */
public class WebSocketSubprotocol {

    /**
     * Parses the requested subprotocols from the client handshake request.
     *
     * @param request the WebSocket upgrade request
     * @return the list of requested subprotocols, in order of preference
     */
    public List<String> parseRequestedProtocols(HttpRequest request) {
        String header = request.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_PROTOCOL);
        return parseProtocolHeader(header);
    }

    /**
     * Parses a Sec-WebSocket-Protocol header value into a list of protocols.
     *
     * @param headerValue the header value (comma-separated list)
     * @return the parsed list of protocol names
     */
    public List<String> parseProtocolHeader(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return List.of();
        }
        List<String> protocols = new ArrayList<>();
        for (String part : headerValue.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                protocols.add(trimmed);
            }
        }
        return Collections.unmodifiableList(protocols);
    }

    /**
     * Negotiates a subprotocol from the client's requested list and the server's supported set.
     *
     * <p>Returns the first client-requested protocol that the server supports,
     * or null if no common protocol exists.
     *
     * @param requestedProtocols the client's requested protocols (in preference order)
     * @param supportedProtocols the server's supported protocols
     * @return the selected protocol, or null if no match
     */
    public String negotiate(List<String> requestedProtocols, Set<String> supportedProtocols) {
        if (requestedProtocols == null || supportedProtocols == null) {
            return null;
        }
        for (String requested : requestedProtocols) {
            if (supportedProtocols.contains(requested)) {
                return requested;
            }
        }
        return null;
    }

    /**
     * Sets the negotiated subprotocol on the handshake response.
     *
     * @param response the WebSocket handshake response
     * @param protocol the negotiated protocol name
     */
    public void setNegotiatedProtocol(HttpResponse response, String protocol) {
        if (protocol != null && !protocol.isEmpty()) {
            response.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_PROTOCOL, protocol);
        }
    }

    /**
     * Extracts the selected subprotocol from the server's handshake response.
     *
     * @param response the WebSocket handshake response
     * @return the selected protocol, or null if none was negotiated
     */
    public String getSelectedProtocol(HttpResponse response) {
        return response.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_PROTOCOL);
    }
}
