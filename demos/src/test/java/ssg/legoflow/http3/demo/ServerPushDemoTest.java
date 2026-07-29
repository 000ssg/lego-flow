package ssg.legoflow.http3.demo;

import ssg.legoflow.http3.quic.QuicConnection;
import ssg.legoflow.http3.quic.QuicSettings;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ServerPushDemoTest {

    @Test
    void testHandleRequestWithPush() {
        // Given
        var demo = new ServerPushDemo();
        var quicSettings = QuicSettings.builder()
                .initialMaxStreamsUni(10)
                .initialMaxStreamsBidi(10)
                .build();
        var quicConn = new QuicConnection(1L, true, quicSettings);
        quicConn.accept();
        var h3Conn = demo.server().acceptConnection(quicConn);

        var requestStream = quicConn.createStream(true);
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET"),
                new AbstractMap.SimpleEntry<>(":path", "/page"),
                new AbstractMap.SimpleEntry<>(":scheme", "https"),
                new AbstractMap.SimpleEntry<>(":authority", "localhost")
        );

        // When: handle request with push — should not throw
        demo.handleRequestWithPush(h3Conn, requestStream, headers, null);

        // Then: push was handled successfully
        assertThat(demo.server().getActiveConnections()).hasSize(1);
    }

    @Test
    void testServerHasRoutes() {
        // Given/When
        var demo = new ServerPushDemo();

        // Then
        assertThat(demo.server().router().getRegisteredPaths())
                .contains("/page", "/style.css");
    }

    @Test
    void testServerPushEnabled() {
        // Given/When
        var demo = new ServerPushDemo();

        // Then
        assertThat(demo.server().config().enablePush()).isTrue();
    }
}
