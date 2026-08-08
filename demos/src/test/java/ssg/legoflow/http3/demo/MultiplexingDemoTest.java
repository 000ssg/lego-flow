package ssg.legoflow.http3.demo;

import ssg.legoflow.http3.quic.QuicConnection;
import ssg.legoflow.http3.quic.QuicSettings;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.*;

class MultiplexingDemoTest {

    @Test
    void testConcurrentStreamRequests() {
        // Given
        var demo = new MultiplexingDemo();
        var quicSettings = QuicSettings.builder()
                .initialMaxStreamsUni(10)
                .initialMaxStreamsBidi(10)
                .build();
        var quicConn = new QuicConnection(1L, false, quicSettings);
        quicConn.connect(new InetSocketAddress("localhost", 443));

        var h3Conn = new ssg.legoflow.http3.Http3Connection(quicConn);
        h3Conn.initialize();

        // When: send concurrent requests on separate streams
        var streams = demo.sendConcurrentRequests(h3Conn,
                "/resource/1", "/resource/2", "/resource/3");

        // Then: each request got its own stream
        assertThat(streams).hasSize(3);
        var streamIds = streams.stream().map(s -> s.streamId()).toList();
        assertThat(streamIds).doesNotHaveDuplicates();
    }

    @Test
    void testServerHasRoutes() {
        // Given/When
        var demo = new MultiplexingDemo();

        // Then
        assertThat(demo.server().router().getRegisteredPaths())
                .contains("/resource/1", "/resource/2", "/resource/3");
    }

    @Test
    void testConfigHasMaxConcurrentStreams() {
        // Given/When
        var demo = new MultiplexingDemo();

        // Then
        assertThat(demo.config().maxConcurrentStreams()).isEqualTo(10);
    }
}
