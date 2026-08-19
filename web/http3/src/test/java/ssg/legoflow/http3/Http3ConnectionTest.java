package ssg.legoflow.http3;

import ssg.legoflow.http3.quic.QuicConnection;
import ssg.legoflow.http3.quic.QuicSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
class Http3ConnectionTest {

    private QuicConnection quicConnection;
    private Http3Connection h3Connection;

    @BeforeEach
    void setUp() {
        var settings = QuicSettings.builder()
                .initialMaxStreamsUni(10)
                .initialMaxStreamsBidi(10)
                .build();
        quicConnection = new QuicConnection(1L, false, settings);
        quicConnection.connect(new InetSocketAddress("localhost", 443));
        h3Connection = new Http3Connection(quicConnection);
    }

    @Test
    void testInitialize() {
        // Given/When
        h3Connection.initialize();

        // Then
        assertThat(h3Connection.controlStream()).isNotNull();
        assertThat(h3Connection.qpackEncoderStream()).isNotNull();
        assertThat(h3Connection.qpackDecoderStream()).isNotNull();
    }

    @Test
    void testIsConnected() {
        // Given/When
        h3Connection.initialize();

        // Then
        assertThat(h3Connection.isConnected()).isTrue();
    }

    @Test
    void testLocalSettings() {
        // Given/When
        var settings = h3Connection.localSettings();

        // Then
        assertThat(settings).isNotNull();
        assertThat(settings.maxFieldSectionSize()).isEqualTo(Http3Settings.DEFAULT_MAX_FIELD_SECTION_SIZE);
    }

    @Test
    void testRemoteSettings() {
        // Given
        var remote = Http3Settings.builder().maxFieldSectionSize(32768).build();

        // When
        h3Connection.setRemoteSettings(remote);

        // Then
        assertThat(h3Connection.remoteSettings().maxFieldSectionSize()).isEqualTo(32768);
    }

    @Test
    void testSendRequest() {
        // Given
        h3Connection.initialize();
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET"),
                new AbstractMap.SimpleEntry<>(":path", "/"),
                new AbstractMap.SimpleEntry<>(":scheme", "https")
        );

        // When
        var stream = h3Connection.sendRequest(headers, null);

        // Then
        assertThat(stream).isNotNull();
        assertThat(h3Connection.requestStreams()).isNotEmpty();
    }

    @Test
    void testFrameListener() {
        // Given
        h3Connection.initialize();
        var frameCount = new AtomicInteger(0);
        h3Connection.addFrameListener(frame -> frameCount.incrementAndGet());

        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET"),
                new AbstractMap.SimpleEntry<>(":path", "/")
        );

        // When
        h3Connection.sendRequest(headers, null);

        // Then: at least HEADERS frame should be sent
        assertThat(frameCount.get()).isGreaterThan(0);
    }

    @Test
    void testSendGoaway() {
        // Given
        h3Connection.initialize();

        // When
        h3Connection.sendGoaway(0);

        // Then
        assertThat(h3Connection.isGoawaySent()).isTrue();
    }

    @Test
    void testGoawayReceived() {
        // Given/When
        h3Connection.markGoawayReceived();

        // Then
        assertThat(h3Connection.isGoawayReceived()).isTrue();
    }

    @Test
    void testClose() {
        // Given
        h3Connection.initialize();

        // When
        h3Connection.close();

        // Then
        assertThat(h3Connection.isGoawaySent()).isTrue();
    }

    @Test
    void testEncoderAndDecoder() {
        // Given/When/Then
        assertThat(h3Connection.encoder()).isNotNull();
        assertThat(h3Connection.decoder()).isNotNull();
        assertThat(h3Connection.frameCodec()).isNotNull();
    }

    @Test
    void testQuicConnection() {
        // Given/When/Then
        assertThat(h3Connection.quicConnection()).isSameAs(quicConnection);
    }
}
