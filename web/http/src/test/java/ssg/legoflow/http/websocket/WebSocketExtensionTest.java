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

class WebSocketExtensionTest {

    private final WebSocketExtension handler = new WebSocketExtension();

    @Test
    void testParseExtensionHeaderSimple() {
        // When
        var extensions = handler.parseExtensionHeader("permessage-deflate");

        // Then
        assertThat(extensions).hasSize(1);
        assertThat(extensions.getFirst().name()).isEqualTo("permessage-deflate");
        assertThat(extensions.getFirst().parameters()).isEmpty();
    }

    @Test
    void testParseExtensionHeaderWithParams() {
        // When
        var extensions = handler.parseExtensionHeader(
                "permessage-deflate; server_no_context_takeover; client_max_window_bits=15");

        // Then
        assertThat(extensions).hasSize(1);
        var ext = extensions.getFirst();
        assertThat(ext.name()).isEqualTo("permessage-deflate");
        assertThat(ext.hasParameter("server_no_context_takeover")).isTrue();
        assertThat(ext.parameters().get("client_max_window_bits")).isEqualTo("15");
    }

    @Test
    void testParseExtensionHeaderMultiple() {
        // When
        var extensions = handler.parseExtensionHeader(
                "permessage-deflate, x-webkit-deflate-frame");

        // Then
        assertThat(extensions).hasSize(2);
        assertThat(extensions.get(0).name()).isEqualTo("permessage-deflate");
        assertThat(extensions.get(1).name()).isEqualTo("x-webkit-deflate-frame");
    }

    @Test
    void testParseExtensionHeaderNull() {
        assertThat(handler.parseExtensionHeader(null)).isEmpty();
        assertThat(handler.parseExtensionHeader("")).isEmpty();
    }

    @Test
    void testParseRequestedExtensions() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_EXTENSIONS, "permessage-deflate");

        // When
        var extensions = handler.parseRequestedExtensions(request);

        // Then
        assertThat(extensions).hasSize(1);
        assertThat(extensions.getFirst().name()).isEqualTo("permessage-deflate");
    }

    @Test
    void testNegotiateExtensions() {
        // Given
        var offers = List.of(
                new WebSocketExtension.ExtensionOffer("permessage-deflate", null),
                new WebSocketExtension.ExtensionOffer("x-custom", null)
        );
        var supported = Set.of("permessage-deflate");

        // When
        var accepted = handler.negotiate(offers, supported);

        // Then
        assertThat(accepted).hasSize(1);
        assertThat(accepted.getFirst().name()).isEqualTo("permessage-deflate");
    }

    @Test
    void testNegotiateNoMatch() {
        var offers = List.of(new WebSocketExtension.ExtensionOffer("x-custom", null));
        var supported = Set.of("permessage-deflate");

        assertThat(handler.negotiate(offers, supported)).isEmpty();
    }

    @Test
    void testSetNegotiatedExtensions() {
        // Given
        var response = HttpResponse.of(HttpStatus.SWITCHING_PROTOCOLS);
        var extensions = List.of(
                new WebSocketExtension.ExtensionOffer("permessage-deflate", null));

        // When
        handler.setNegotiatedExtensions(response, extensions);

        // Then
        String header = response.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_EXTENSIONS);
        assertThat(header).isEqualTo("permessage-deflate");
    }

    @Test
    void testGetAcceptedExtensions() {
        // Given
        var response = HttpResponse.of(HttpStatus.SWITCHING_PROTOCOLS);
        response.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_EXTENSIONS,
                "permessage-deflate; server_no_context_takeover");

        // When
        var accepted = handler.getAcceptedExtensions(response);

        // Then
        assertThat(accepted).hasSize(1);
        assertThat(accepted.getFirst().hasParameter("server_no_context_takeover")).isTrue();
    }

    @Test
    void testPermessageDeflateFactory() {
        var offer = WebSocketExtension.permessageDeflate();
        assertThat(offer.name()).isEqualTo("permessage-deflate");
        assertThat(offer.parameters()).isEmpty();
    }

    @Test
    void testPermessageDeflateNoContextTakeover() {
        var offer = WebSocketExtension.permessageDeflateNoContextTakeover();
        assertThat(offer.name()).isEqualTo("permessage-deflate");
        assertThat(offer.hasParameter("server_no_context_takeover")).isTrue();
        assertThat(offer.hasParameter("client_no_context_takeover")).isTrue();
    }

    @Test
    void testExtensionOfferToString() {
        var offer = new WebSocketExtension.ExtensionOffer("permessage-deflate",
                java.util.Map.of("server_no_context_takeover", "", "client_max_window_bits", "15"));
        String str = offer.toString();
        assertThat(str).startsWith("permessage-deflate");
        assertThat(str).contains("client_max_window_bits=15");
    }
}
