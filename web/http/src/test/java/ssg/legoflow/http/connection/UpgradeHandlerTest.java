package ssg.legoflow.http.connection;

import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UpgradeHandlerTest {

    private UpgradeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpgradeHandler();
    }

    @Test
    void testIsUpgradeRequestWithValidHeaders() {
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");

        assertThat(handler.isUpgradeRequest(request)).isTrue();
    }

    @Test
    void testIsUpgradeRequestWithoutUpgradeHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");

        assertThat(handler.isUpgradeRequest(request)).isFalse();
    }

    @Test
    void testIsUpgradeRequestWithoutConnectionHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");

        assertThat(handler.isUpgradeRequest(request)).isFalse();
    }

    @Test
    void testIsUpgradeRequestWithKeepAliveConnection() {
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "keep-alive");

        assertThat(handler.isUpgradeRequest(request)).isFalse();
    }

    @Test
    void testGetUpgradeProtocol() {
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");

        assertThat(handler.getUpgradeProtocol(request)).isEqualTo("websocket");
    }

    @Test
    void testGetUpgradeProtocolWhenMissing() {
        var request = HttpRequest.of(HttpMethod.GET, "/ws");

        assertThat(handler.getUpgradeProtocol(request)).isNull();
    }

    @Test
    void testCreateUpgradeResponse() {
        var response = handler.createUpgradeResponse("websocket");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SWITCHING_PROTOCOLS);
        assertThat(response.getHeaders().get(HttpHeaders.UPGRADE)).isEqualTo("websocket");
        assertThat(response.getHeaders().get(HttpHeaders.CONNECTION)).isEqualTo("Upgrade");
    }

    @Test
    void testCreateUpgradeResponseForH2c() {
        var response = handler.createUpgradeResponse("h2c");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SWITCHING_PROTOCOLS);
        assertThat(response.getHeaders().get(HttpHeaders.UPGRADE)).isEqualTo("h2c");
    }

    @Test
    void testConnectionHeaderContainsUpgradeCaseInsensitive() {
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "keep-alive, Upgrade");

        assertThat(handler.isUpgradeRequest(request)).isTrue();
    }
}
