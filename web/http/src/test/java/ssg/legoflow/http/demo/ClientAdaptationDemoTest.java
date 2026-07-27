package ssg.legoflow.http.demo;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.demo.client.AdaptiveClient;
import ssg.legoflow.http.demo.client.RangeClient;
import ssg.legoflow.http.demo.client.SecureClient;
import ssg.legoflow.http.demo.client.WebSocketClient;
import ssg.legoflow.http.demo.multi.ClientServerPairDemo;
import ssg.legoflow.http.server.HttpServer;
import ssg.legoflow.http.websocket.WebSocketHandshake;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ClientAdaptationDemoTest {

    @Test
    void testAdaptiveClientSetsNegotiationHeaders() {
        var client = new AdaptiveClient();
        var request = client.createAdaptiveGetRequest("/api/data");

        assertThat(request.getHeaders().get(HttpHeaders.ACCEPT)).contains("application/json");
        assertThat(request.getHeaders().get(HttpHeaders.ACCEPT_ENCODING)).contains("gzip");
        assertThat(request.getHeaders().get(HttpHeaders.ACCEPT_CHARSET)).contains("utf-8");
    }

    @Test
    void testAdaptiveClientTypedRequest() {
        var client = new AdaptiveClient();
        var request = client.createTypedGetRequest("/api/data", "text/xml");

        assertThat(request.getHeaders().get(HttpHeaders.ACCEPT)).isEqualTo("text/xml");
    }

    @Test
    void testAdaptiveClientCustomAcceptTypes() {
        var client = new AdaptiveClient();
        client.setAcceptTypes("text/plain");
        var request = client.createAdaptiveGetRequest("/test");

        assertThat(request.getHeaders().get(HttpHeaders.ACCEPT)).isEqualTo("text/plain");
    }

    @Test
    void testAdaptiveClientWithCompressionServer() {
        var server = new HttpServer(new ServerConfig(StandardProfiles.serverStandard()));
        server.getRouter().get("/data", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.OK, "response ".repeat(100)));
        var ctx = new DefaultContext();
        var client = new AdaptiveClient();

        var request = client.createAdaptiveGetRequest("/data");
        var response = server.handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isEqualTo("gzip");
    }

    @Test
    void testRangeClientRequest() {
        var client = new RangeClient();
        var request = client.createRangeRequest("/file", 0, 99);

        assertThat(request.getHeaders().get(HttpHeaders.RANGE)).isEqualTo("bytes=0-99");
    }

    @Test
    void testRangeClientSuffixRequest() {
        var client = new RangeClient();
        var request = client.createSuffixRangeRequest("/file", 50);

        assertThat(request.getHeaders().get(HttpHeaders.RANGE)).isEqualTo("bytes=-50");
    }

    @Test
    void testRangeClientOpenRequest() {
        var client = new RangeClient();
        var request = client.createOpenRangeRequest("/file", 100);

        assertThat(request.getHeaders().get(HttpHeaders.RANGE)).isEqualTo("bytes=100-");
    }

    @Test
    void testSecureClientCreation() {
        var client = new SecureClient();

        assertThat(client.getClient()).isNotNull();
        assertThat(client.getSslConfig()).isNotNull();
        assertThat(client.getSslConfig().getTruststorePath()).isNotNull();
    }

    @Test
    void testSecureClientRequestCreation() {
        var client = new SecureClient();
        var get = client.createSecureGetRequest("/secure");
        var post = client.createSecurePostRequest("/secure", "data");

        assertThat(get.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(post.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(post.getBodyAsString()).isEqualTo("data");
    }

    @Test
    void testWebSocketClientHandshake() {
        var wsClient = new WebSocketClient();
        var clientKey = wsClient.generateClientKey();
        var request = wsClient.createUpgradeRequest("/ws", clientKey);

        assertThat(request.getHeaders().get(HttpHeaders.UPGRADE)).isEqualTo("websocket");
        assertThat(request.getHeaders().get(HttpHeaders.CONNECTION)).isEqualTo("Upgrade");
        assertThat(request.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_VERSION)).isEqualTo("13");
        assertThat(request.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_KEY)).isEqualTo(clientKey);
    }

    @Test
    void testWebSocketClientValidatesHandshake() {
        var wsClient = new WebSocketClient();
        var handshake = new WebSocketHandshake();
        var clientKey = wsClient.generateClientKey();
        var request = wsClient.createUpgradeRequest("/ws", clientKey);
        var response = handshake.createHandshakeResponse(request);

        assertThat(wsClient.validateHandshake(response, clientKey)).isTrue();
    }

    @Test
    void testClientServerPairPingPong() {
        var pair = new ClientServerPairDemo();
        var request = HttpRequest.of(HttpMethod.GET, "/ping");

        var response = pair.exchange(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("pong");
    }

    @Test
    void testClientServerPairEcho() {
        var pair = new ClientServerPairDemo();
        var request = HttpRequest.of(HttpMethod.POST, "/echo");
        request.setBody(java.nio.ByteBuffer.wrap("Hello Echo".getBytes()));
        request.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain");

        var response = pair.exchange(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("Hello Echo");
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/plain");
    }
}
