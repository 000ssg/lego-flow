package ssg.legoflow.http.websocket;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class WebSocketHandshake {

    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public boolean isWebSocketUpgrade(HttpRequest request) {
        if (request.getMethod() != HttpMethod.GET) return false;
        var upgrade = request.getHeaders().get(HttpHeaders.UPGRADE);
        var connection = request.getHeaders().get(HttpHeaders.CONNECTION);
        var key = request.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_KEY);
        var version = request.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_VERSION);
        return "websocket".equalsIgnoreCase(upgrade)
                && connection != null && connection.toLowerCase().contains("upgrade")
                && key != null
                && "13".equals(version);
    }

    public HttpResponse createHandshakeResponse(HttpRequest request) {
        var key = request.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_KEY);
        var acceptKey = generateAcceptKey(key);
        var response = HttpResponse.of(HttpStatus.SWITCHING_PROTOCOLS);
        response.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        response.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        response.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_ACCEPT, acceptKey);
        return response;
    }

    public String generateAcceptKey(String clientKey) {
        try {
            var digest = MessageDigest.getInstance("SHA-1");
            var hash = digest.digest((clientKey + WEBSOCKET_GUID).getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    public boolean validateHandshakeResponse(HttpResponse response, String clientKey) {
        if (response.getStatus() != HttpStatus.SWITCHING_PROTOCOLS) return false;
        var acceptKey = response.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_ACCEPT);
        return acceptKey != null && acceptKey.equals(generateAcceptKey(clientKey));
    }
}
