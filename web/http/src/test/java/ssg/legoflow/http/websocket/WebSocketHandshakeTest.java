package ssg.legoflow.http.websocket;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class WebSocketHandshakeTest {

    private final WebSocketHandshake handshake = new WebSocketHandshake();

    @Test
    void testIsWebSocketUpgradeValid() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_VERSION, "13");

        // Then
        assertThat(handshake.isWebSocketUpgrade(request)).isTrue();
    }

    @Test
    void testIsWebSocketUpgradeWrongMethod() {
        // Given
        var request = HttpRequest.of(HttpMethod.POST, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_VERSION, "13");

        // Then
        assertThat(handshake.isWebSocketUpgrade(request)).isFalse();
    }

    @Test
    void testIsWebSocketUpgradeMissingHeaders() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/ws");

        // Then
        assertThat(handshake.isWebSocketUpgrade(request)).isFalse();
    }

    @Test
    void testGenerateAcceptKeyKnownValue() {
        // RFC 6455 example key
        String clientKey = "dGhlIHNhbXBsZSBub25jZQ==";

        // When
        String acceptKey = handshake.generateAcceptKey(clientKey);

        // Then - known expected value from RFC 6455
        assertThat(acceptKey).isEqualTo("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=");
    }

    @Test
    void testCreateHandshakeResponse() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");

        // When
        var response = handshake.createHandshakeResponse(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SWITCHING_PROTOCOLS);
        assertThat(response.getHeaders().get(HttpHeaders.UPGRADE)).isEqualTo("websocket");
        assertThat(response.getHeaders().get(HttpHeaders.CONNECTION)).isEqualTo("Upgrade");
        assertThat(response.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_ACCEPT)).isNotNull();
    }

    @Test
    void testValidateHandshakeResponseValid() {
        // Given
        String clientKey = "dGhlIHNhbXBsZSBub25jZQ==";
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_KEY, clientKey);
        var response = handshake.createHandshakeResponse(request);

        // Then
        assertThat(handshake.validateHandshakeResponse(response, clientKey)).isTrue();
    }

    @Test
    void testValidateHandshakeResponseWrongStatus() {
        // Given
        var response = ssg.legoflow.http.core.HttpResponse.of(HttpStatus.OK);

        // Then
        assertThat(handshake.validateHandshakeResponse(response, "key")).isFalse();
    }

    @Test
    void testIsWebSocketUpgradeWrongVersion() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_KEY, "key");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_VERSION, "8");

        // Then
        assertThat(handshake.isWebSocketUpgrade(request)).isFalse();
    }
}
