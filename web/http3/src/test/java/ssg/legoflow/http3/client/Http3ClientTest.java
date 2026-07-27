package ssg.legoflow.http3.client;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http3.config.Http3Config;
import ssg.legoflow.http3.quic.QuicConnection;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;

class Http3ClientTest {

    @Test
    void testConnect() {
        // Given
        var client = new Http3Client(Http3Config.defaults());

        // When
        var connection = client.connect();

        // Then
        assertThat(connection).isNotNull();
        assertThat(connection.isConnected()).isTrue();
        assertThat(client.connection()).isSameAs(connection);
    }

    @Test
    void testSendRequest() {
        // Given
        var client = new Http3Client(Http3Config.defaults());
        client.connect();

        var request = HttpRequest.of(HttpMethod.GET, "/hello");
        request.getHeaders().set(HttpHeaders.HOST, "localhost");

        // When
        var response = client.send(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testSendRequestNotConnected() {
        // Given
        var client = new Http3Client(Http3Config.defaults());

        // When/Then
        assertThatThrownBy(() -> client.send(HttpRequest.of(HttpMethod.GET, "/")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not connected");
    }

    @Test
    void testSendAsync() throws ExecutionException, InterruptedException {
        // Given
        var client = new Http3Client(Http3Config.defaults());
        client.connect();

        var request = HttpRequest.of(HttpMethod.GET, "/data");
        request.getHeaders().set(HttpHeaders.HOST, "localhost");

        // When
        var future = client.sendAsync(request);
        var response = future.get();

        // Then
        assertThat(response).isNotNull();
    }

    @Test
    void testSendAsyncNotConnected() {
        // Given
        var client = new Http3Client(Http3Config.defaults());

        // When/Then
        assertThatThrownBy(() -> client.sendAsync(HttpRequest.of(HttpMethod.GET, "/")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testMultiplexedRequests() {
        // Given
        var client = new Http3Client(Http3Config.defaults().maxConcurrentStreams(10));
        client.connect();

        // When: send multiple requests
        for (int i = 0; i < 5; i++) {
            var request = HttpRequest.of(HttpMethod.GET, "/resource/" + i);
            request.getHeaders().set(HttpHeaders.HOST, "localhost");
            var response = client.send(request);
            assertThat(response).isNotNull();
        }

        // Then: all requests used separate streams
        assertThat(client.connection().requestStreams()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void testClose() {
        // Given
        var client = new Http3Client(Http3Config.defaults());
        client.connect();
        assertThat(client.connection()).isNotNull();

        // When
        client.close();

        // Then
        assertThat(client.connection()).isNull();
    }

    @Test
    void testZeroRttFlag() {
        // Given
        var config = Http3Config.defaults().enable0Rtt(true);
        var client = new Http3Client(config);

        // When/Then
        assertThat(client.isZeroRttEnabled()).isTrue();

        client.setZeroRttEnabled(false);
        assertThat(client.isZeroRttEnabled()).isFalse();
    }

    @Test
    void testConfig() {
        // Given
        var config = Http3Config.defaults();
        var client = new Http3Client(config);

        // When/Then
        assertThat(client.config()).isSameAs(config);
    }

    // ==================== Real Request/Response Tests ====================

    @Test
    void testSendCreatesQpackEncodedHeadersFrame() {
        // Given: client connected, QPACK encoding active
        var client = new Http3Client(Http3Config.defaults());
        client.connect();

        var request = HttpRequest.of(HttpMethod.POST, "/api/submit");
        request.getHeaders().set(HttpHeaders.HOST, "example.com");
        request.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");

        // When
        var response = client.send(request);

        // Then: response is returned (uses simulated transport)
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);

        // Verify stream was created for the request
        assertThat(client.connection().requestStreams()).isNotEmpty();
    }

    @Test
    void testSendWithRequestBody() {
        // Given
        var client = new Http3Client(Http3Config.defaults());
        client.connect();

        var request = HttpRequest.of(HttpMethod.POST, "/api/data");
        request.getHeaders().set(HttpHeaders.HOST, "localhost");
        request.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        request.setBody(java.nio.ByteBuffer.wrap("{\"key\":\"value\"}".getBytes()));

        // When
        var response = client.send(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testNegotiatedAlpn() {
        // Given
        var client = new Http3Client(Http3Config.defaults());
        client.connect();

        // When/Then: ALPN should be "h3" after connection
        assertThat(client.negotiatedAlpn()).isEqualTo("h3");
    }

    @Test
    void testNegotiatedCipherSuite() {
        // Given
        var client = new Http3Client(Http3Config.defaults());
        client.connect();

        // When/Then: cipher suite available
        assertThat(client.negotiatedCipherSuite()).isNotNull();
        assertThat(client.negotiatedCipherSuite()).contains("TLS_AES");
    }

    @Test
    void testHandshakePhase() {
        // Given
        var client = new Http3Client(Http3Config.defaults());
        client.connect();

        // When/Then
        assertThat(client.handshakePhase()).isEqualTo(QuicConnection.HandshakePhase.ESTABLISHED);
    }

    @Test
    void testNegotiatedAlpnBeforeConnect() {
        // Given
        var client = new Http3Client(Http3Config.defaults());

        // When/Then: null before connection
        assertThat(client.negotiatedAlpn()).isNull();
        assertThat(client.negotiatedCipherSuite()).isNull();
        assertThat(client.handshakePhase()).isNull();
    }

    @Test
    void testResponseVersionIsHttp3() {
        // Given
        var client = new Http3Client(Http3Config.defaults());
        client.connect();

        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.HOST, "localhost");

        // When
        var response = client.send(request);

        // Then
        assertThat(response.getVersion()).isEqualTo(HttpVersion.HTTP_3);
    }
}
