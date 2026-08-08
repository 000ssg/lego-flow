package ssg.legoflow.http.demo.server;

import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.server.HttpServer;
import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketHandshake;
import ssg.legoflow.http.websocket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket server demo that handles the upgrade handshake and echoes text frames.
 *
 * <p>Uses {@link WebSocketHandshake} for protocol upgrade validation and
 * {@link WebSocketSession} for session lifecycle management.
 *
 * @since 0.1
 */
public class WebSocketServer {

    private final HttpServer server;
    private final WebSocketHandshake handshake;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public WebSocketServer() {
        this(8083);
    }

    public WebSocketServer(int port) {
        var config = new ServerConfig(StandardProfiles.serverFull());
        config.setPort(port);
        this.server = new HttpServer("websocket-server", config);
        this.handshake = new WebSocketHandshake();

        var router = server.getRouter();

        router.get("/", (ctx, req) -> HttpResponse.of(HttpStatus.OK, "WebSocket Server"));

        router.get("/ws", (ctx, req) -> {
            if (!handshake.isWebSocketUpgrade(req)) {
                return HttpResponse.of(HttpStatus.BAD_REQUEST, "WebSocket upgrade required");
            }
            var response = handshake.createHandshakeResponse(req);
            var session = new WebSocketSession("ws-" + sessions.size());
            session.onMessage(frame -> {
                // Echo: respond with same text
            });
            sessions.put(session.getId(), session);
            return response;
        });
    }

    /**
     * Processes a WebSocket frame on a given session, echoing text frames back.
     *
     * @param sessionId the session identifier
     * @param frame     the incoming frame
     * @return the echo frame, or null if session not found
     */
    public WebSocketFrame echo(String sessionId, WebSocketFrame frame) {
        var session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) return null;
        session.handleFrame(frame);
        return WebSocketFrame.text(frame.getPayloadText());
    }

    /**
     * Returns the underlying HttpServer instance.
     *
     * @return the server
     */
    public HttpServer getServer() {
        return server;
    }

    /**
     * Returns the WebSocket handshake handler.
     *
     * @return the handshake handler
     */
    public WebSocketHandshake getHandshake() {
        return handshake;
    }

    /**
     * Returns the active sessions map.
     *
     * @return sessions keyed by id
     */
    public Map<String, WebSocketSession> getSessions() {
        return sessions;
    }
}
