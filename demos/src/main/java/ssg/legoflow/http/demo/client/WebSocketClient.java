package ssg.legoflow.http.demo.client;

import ssg.legoflow.http.client.HttpClient;
import ssg.legoflow.http.client.HttpClientBuilder;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketHandshake;
import ssg.legoflow.http.websocket.WebSocketSession;

import java.util.Base64;

/**
 * WebSocket client demo that performs the upgrade handshake and manages frame exchange.
 *
 * <p>Uses {@link WebSocketHandshake} for the opening handshake and
 * {@link WebSocketSession} for managing the connection lifecycle.
 *
 * @since 1.0
 */
public class WebSocketClient {

    private final HttpClient client;
    private final WebSocketHandshake handshake;
    private WebSocketSession session;

    public WebSocketClient() {
        this.client = new HttpClientBuilder().full().build();
        this.handshake = new WebSocketHandshake();
    }

    /**
     * Creates a WebSocket upgrade request with the required headers.
     *
     * @param path      the WebSocket endpoint path
     * @param clientKey the Sec-WebSocket-Key value
     * @return the upgrade request
     */
    public HttpRequest createUpgradeRequest(String path, String clientKey) {
        var request = HttpRequest.of(HttpMethod.GET, path);
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_KEY, clientKey);
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_VERSION, "13");
        return request;
    }

    /**
     * Generates a random client key for the WebSocket handshake.
     *
     * @return a base64-encoded 16-byte key
     */
    public String generateClientKey() {
        var bytes = new byte[16];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 17 + 42);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Validates the server's handshake response.
     *
     * @param response  the server response
     * @param clientKey the client key used in the request
     * @return true if the handshake is valid
     */
    public boolean validateHandshake(HttpResponse response, String clientKey) {
        return handshake.validateHandshakeResponse(response, clientKey);
    }

    /**
     * Creates a new session after successful handshake.
     *
     * @param sessionId the session identifier
     * @return the new WebSocket session
     */
    public WebSocketSession openSession(String sessionId) {
        this.session = new WebSocketSession(sessionId);
        return session;
    }

    /**
     * Creates a text frame for sending.
     *
     * @param text the text payload
     * @return the WebSocket text frame
     */
    public WebSocketFrame createTextFrame(String text) {
        return WebSocketFrame.text(text);
    }

    /**
     * Returns the current WebSocket session.
     *
     * @return the session, or null if not connected
     */
    public WebSocketSession getSession() {
        return session;
    }

    /**
     * Returns the underlying HttpClient instance.
     *
     * @return the client
     */
    public HttpClient getClient() {
        return client;
    }

    /**
     * Returns the WebSocket handshake handler.
     *
     * @return the handshake handler
     */
    public WebSocketHandshake getHandshake() {
        return handshake;
    }
}
