package ssg.legoflow.http.websocket;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class WebSocketSubprotocolTest {

    private final WebSocketSubprotocol handler = new WebSocketSubprotocol();

    @Test
    void testParseRequestedProtocols() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_PROTOCOL, "chat, superchat");

        // When
        List<String> protocols = handler.parseRequestedProtocols(request);

        // Then
        assertThat(protocols).containsExactly("chat", "superchat");
    }

    @Test
    void testParseProtocolHeaderSingle() {
        assertThat(handler.parseProtocolHeader("chat")).containsExactly("chat");
    }

    @Test
    void testParseProtocolHeaderNull() {
        assertThat(handler.parseProtocolHeader(null)).isEmpty();
        assertThat(handler.parseProtocolHeader("")).isEmpty();
    }

    @Test
    void testNegotiateFirstMatch() {
        // Given
        List<String> requested = List.of("chat", "superchat", "mqtt");
        Set<String> supported = Set.of("mqtt", "superchat");

        // When — returns first client-requested that server supports
        String selected = handler.negotiate(requested, supported);

        // Then
        assertThat(selected).isEqualTo("superchat");
    }

    @Test
    void testNegotiateNoMatch() {
        List<String> requested = List.of("chat", "superchat");
        Set<String> supported = Set.of("mqtt");

        assertThat(handler.negotiate(requested, supported)).isNull();
    }

    @Test
    void testNegotiateNullInputs() {
        assertThat(handler.negotiate(null, Set.of("chat"))).isNull();
        assertThat(handler.negotiate(List.of("chat"), null)).isNull();
    }

    @Test
    void testSetNegotiatedProtocol() {
        // Given
        var response = HttpResponse.of(HttpStatus.SWITCHING_PROTOCOLS);

        // When
        handler.setNegotiatedProtocol(response, "chat");

        // Then
        assertThat(response.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_PROTOCOL)).isEqualTo("chat");
    }

    @Test
    void testSetNegotiatedProtocolNull() {
        // Given
        var response = HttpResponse.of(HttpStatus.SWITCHING_PROTOCOLS);

        // When
        handler.setNegotiatedProtocol(response, null);

        // Then — no header added
        assertThat(response.getHeaders().contains(HttpHeaders.SEC_WEBSOCKET_PROTOCOL)).isFalse();
    }

    @Test
    void testGetSelectedProtocol() {
        // Given
        var response = HttpResponse.of(HttpStatus.SWITCHING_PROTOCOLS);
        response.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_PROTOCOL, "mqtt");

        // When
        String selected = handler.getSelectedProtocol(response);

        // Then
        assertThat(selected).isEqualTo("mqtt");
    }
}
