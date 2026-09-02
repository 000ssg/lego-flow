package ssg.legoflow.http3.demo;

import ssg.legoflow.http3.quic.QuicConnection;
import ssg.legoflow.http3.quic.QuicSettings;
import org.junit.jupiter.api.Test;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class SimpleHttp3DemoTest {

    @Test
    void testSimpleGetRequest() {
        // Given
        var demo = new SimpleHttp3Server();
        var quicSettings = QuicSettings.builder()
                .initialMaxStreamsUni(10)
                .initialMaxStreamsBidi(10)
                .build();
        var quicConn = new QuicConnection(1L, true, quicSettings);
        quicConn.accept();
        var h3Conn = demo.acceptConnection(quicConn);

        var stream = quicConn.createStream(true);
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET"),
                new AbstractMap.SimpleEntry<>(":path", "/hello"),
                new AbstractMap.SimpleEntry<>(":scheme", "https"),
                new AbstractMap.SimpleEntry<>(":authority", "localhost")
        );

        // When: handle request — should not throw
        demo.handleRequest(h3Conn, stream, headers, null);

        // Then
        assertThat(demo.server()).isNotNull();
        assertThat(demo.server().getActiveConnections()).hasSize(1);
    }

    @Test
    void testNotFoundRoute() {
        // Given
        var demo = new SimpleHttp3Server();
        var quicSettings = QuicSettings.builder()
                .initialMaxStreamsUni(10)
                .initialMaxStreamsBidi(10)
                .build();
        var quicConn = new QuicConnection(1L, true, quicSettings);
        quicConn.accept();
        var h3Conn = demo.acceptConnection(quicConn);

        var stream = quicConn.createStream(true);
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET"),
                new AbstractMap.SimpleEntry<>(":path", "/unknown"),
                new AbstractMap.SimpleEntry<>(":scheme", "https")
        );

        // When: handle request for unknown route — should not throw
        demo.handleRequest(h3Conn, stream, headers, null);

        // Then
        assertThat(demo.server().getActiveConnections()).hasSize(1);
    }

    @Test
    void testServerHasRouter() {
        // Given/When
        var demo = new SimpleHttp3Server();

        // Then
        assertThat(demo.server().router()).isNotNull();
        assertThat(demo.server().router().getRegisteredPaths()).contains("/hello");
    }
}
