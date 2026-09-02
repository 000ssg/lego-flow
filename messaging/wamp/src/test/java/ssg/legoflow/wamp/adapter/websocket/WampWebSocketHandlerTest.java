package ssg.legoflow.wamp.adapter.websocket;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class WampWebSocketHandlerTest {

    private final WampWebSocketHandler handler = new WampWebSocketHandler();

    @Test
    void testValidWampUpgradeRequest() {
        var request = createWampUpgradeRequest();

        assertThat(handler.isWampUpgrade(request)).isTrue();
    }

    @Test
    void testRejectsNonWebSocketRequest() {
        var request = HttpRequest.of(HttpMethod.GET, "/ws");

        assertThat(handler.isWampUpgrade(request)).isFalse();
    }

    @Test
    void testRejectsWebSocketWithoutWampSubprotocol() {
        var request = createWebSocketUpgradeRequest(null);

        assertThat(handler.isWampUpgrade(request)).isFalse();
    }

    @Test
    void testRejectsWebSocketWithWrongSubprotocol() {
        var request = createWebSocketUpgradeRequest("graphql-ws");

        assertThat(handler.isWampUpgrade(request)).isFalse();
    }

    @Test
    void testAcceptsWampAmongMultipleSubprotocols() {
        var request = createWebSocketUpgradeRequest("graphql-ws, wamp.2.json");

        assertThat(handler.isWampUpgrade(request)).isTrue();
    }

    @Test
    void testCreateUpgradeResponseIncludesSubprotocol() {
        var request = createWampUpgradeRequest();

        var response = handler.createUpgradeResponse(request);

        assertThat(response.getHeaders().get("sec-websocket-protocol"))
                .isEqualTo(WampWebSocketHandler.WAMP_SUBPROTOCOL);
        assertThat(response.getHeaders().get(HttpHeaders.UPGRADE)).isEqualTo("websocket");
        assertThat(response.getHeaders().get(HttpHeaders.CONNECTION)).isEqualTo("Upgrade");
    }

    @Test
    void testCreateUpgradeResponseIncludesAcceptKey() {
        var request = createWampUpgradeRequest();

        var response = handler.createUpgradeResponse(request);

        assertThat(response.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_ACCEPT)).isNotNull();
    }

    @Test
    void testRejectsPostRequest() {
        var request = HttpRequest.of(HttpMethod.POST, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_VERSION, "13");
        request.getHeaders().set("sec-websocket-protocol", WampWebSocketHandler.WAMP_SUBPROTOCOL);

        assertThat(handler.isWampUpgrade(request)).isFalse();
    }

    @Test
    void testGetHandshakeReturnsNonNull() {
        assertThat(handler.getHandshake()).isNotNull();
    }

    private HttpRequest createWampUpgradeRequest() {
        return createWebSocketUpgradeRequest(WampWebSocketHandler.WAMP_SUBPROTOCOL);
    }

    private HttpRequest createWebSocketUpgradeRequest(String subprotocol) {
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_VERSION, "13");
        if (subprotocol != null) {
            request.getHeaders().set("sec-websocket-protocol", subprotocol);
        }
        return request;
    }
}
