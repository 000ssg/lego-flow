package ssg.legoflow.http2.connection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class Http2SettingsTest {

    @Test
    void testDefaultValues() {
        var settings = new Http2Settings();

        assertThat(settings.headerTableSize()).isEqualTo(4096);
        assertThat(settings.enablePush()).isTrue();
        assertThat(settings.maxConcurrentStreams()).isEqualTo(100);
        assertThat(settings.initialWindowSize()).isEqualTo(65535);
        assertThat(settings.maxFrameSize()).isEqualTo(16384);
        assertThat(settings.maxHeaderListSize()).isEqualTo(8192);
    }

    @Test
    void testSetValues() {
        var settings = new Http2Settings();
        settings.set(Http2Settings.MAX_CONCURRENT_STREAMS, 200);
        settings.set(Http2Settings.INITIAL_WINDOW_SIZE, 1048576);

        assertThat(settings.maxConcurrentStreams()).isEqualTo(200);
        assertThat(settings.initialWindowSize()).isEqualTo(1048576);
    }

    @Test
    void testEnablePushValidation() {
        var settings = new Http2Settings();

        assertThatThrownBy(() -> settings.set(Http2Settings.ENABLE_PUSH, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMaxFrameSizeValidation() {
        var settings = new Http2Settings();

        assertThatThrownBy(() -> settings.set(Http2Settings.MAX_FRAME_SIZE, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings.set(Http2Settings.MAX_FRAME_SIZE, 20000000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEncodeDecodeRoundTrip() {
        var original = new Http2Settings();
        original.set(Http2Settings.MAX_CONCURRENT_STREAMS, 256);
        original.set(Http2Settings.INITIAL_WINDOW_SIZE, 1048576);
        original.set(Http2Settings.MAX_FRAME_SIZE, 32768);

        var encoded = original.encode();
        var decoded = Http2Settings.decode(encoded);

        assertThat(decoded.maxConcurrentStreams()).isEqualTo(256);
        assertThat(decoded.initialWindowSize()).isEqualTo(1048576);
        assertThat(decoded.maxFrameSize()).isEqualTo(32768);
    }

    @Test
    void testApplyFrom() {
        var base = new Http2Settings();
        var override = new Http2Settings();
        override.set(Http2Settings.MAX_CONCURRENT_STREAMS, 50);

        base.applyFrom(override);

        assertThat(base.maxConcurrentStreams()).isEqualTo(50);
    }

    @Test
    void testToMap() {
        var settings = new Http2Settings();
        var map = settings.toMap();

        assertThat(map).containsKey(Http2Settings.HEADER_TABLE_SIZE);
        assertThat(map).containsKey(Http2Settings.MAX_CONCURRENT_STREAMS);
        assertThat(map.get(Http2Settings.INITIAL_WINDOW_SIZE)).isEqualTo(65535);
    }

    @Test
    void testDisablePush() {
        var settings = new Http2Settings();
        settings.set(Http2Settings.ENABLE_PUSH, 0);

        assertThat(settings.enablePush()).isFalse();
    }

    @Test
    void testGetUnknownSetting() {
        var settings = new Http2Settings();
        assertThat(settings.get(0xFF)).isEqualTo(0);
    }
}
