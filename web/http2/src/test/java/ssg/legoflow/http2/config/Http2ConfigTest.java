package ssg.legoflow.http2.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.http2.connection.Http2Settings;

class Http2ConfigTest {

    @Test void testDefaultValues() {
        var config = new Http2Config();
        assertThat(config.initialWindowSize()).isEqualTo(Http2Settings.DEFAULT_INITIAL_WINDOW_SIZE);
        assertThat(config.maxConcurrentStreams()).isEqualTo(Http2Settings.DEFAULT_MAX_CONCURRENT_STREAMS);
        assertThat(config.maxFrameSize()).isEqualTo(Http2Settings.DEFAULT_MAX_FRAME_SIZE);
        assertThat(config.maxHeaderListSize()).isEqualTo(Http2Settings.DEFAULT_MAX_HEADER_LIST_SIZE);
        assertThat(config.headerTableSize()).isEqualTo(Http2Settings.DEFAULT_HEADER_TABLE_SIZE);
    }

    @Test void testSetInitialWindowSize() {
        var config = new Http2Config();
        config.initialWindowSize(65536);
        assertThat(config.initialWindowSize()).isEqualTo(65536);
    }

    @Test void testSetMaxConcurrentStreams() {
        var config = new Http2Config();
        config.maxConcurrentStreams(100);
        assertThat(config.maxConcurrentStreams()).isEqualTo(100);
    }

    @Test void testSetMaxFrameSizeValid() {
        var config = new Http2Config();
        config.maxFrameSize(32768);
        assertThat(config.maxFrameSize()).isEqualTo(32768);
    }

    @Test void testSetMaxFrameSizeBelowMinimumThrows() {
        var config = new Http2Config();
        assertThatThrownBy(() -> config.maxFrameSize(1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MAX_FRAME_SIZE");
    }

    @Test void testSetMaxFrameSizeAboveMaximumThrows() {
        var config = new Http2Config();
        assertThatThrownBy(() -> config.maxFrameSize(20000000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void testSetMaxFrameSizeAtMinimumBoundary() {
        var config = new Http2Config();
        config.maxFrameSize(16384); // minimum valid value
        assertThat(config.maxFrameSize()).isEqualTo(16384);
    }

    @Test void testSetMaxFrameSizeAtMaximumBoundary() {
        var config = new Http2Config();
        config.maxFrameSize(16777215); // maximum valid value
        assertThat(config.maxFrameSize()).isEqualTo(16777215);
    }

    @Test void testSetMaxHeaderListSize() {
        var config = new Http2Config();
        config.maxHeaderListSize(8192);
        assertThat(config.maxHeaderListSize()).isEqualTo(8192);
    }

    @Test void testEnablePushDefaultTrue() {
        var config = new Http2Config();
        assertThat(config.enablePush()).isTrue();
    }

    @Test void testSetEnablePushFalse() {
        var config = new Http2Config();
        config.enablePush(false);
        assertThat(config.enablePush()).isFalse();
    }

    @Test void testDefaultPort() {
        var config = new Http2Config();
        assertThat(config.port()).isEqualTo(8443);
    }

    @Test void testSetPort() {
        var config = new Http2Config();
        config.port(443);
        assertThat(config.port()).isEqualTo(443);
    }

    @Test void testDefaultHost() {
        var config = new Http2Config();
        assertThat(config.host()).isEqualTo("0.0.0.0");
    }

    @Test void testSetHost() {
        var config = new Http2Config();
        config.host("localhost");
        assertThat(config.host()).isEqualTo("localhost");
    }

    @Test void testBuilderStyleChaining() {
        var config = new Http2Config()
                .initialWindowSize(32768)
                .maxConcurrentStreams(50)
                .maxFrameSize(16384)
                .maxHeaderListSize(4096)
                .enablePush(false)
                .port(443)
                .host("127.0.0.1");
        
        assertThat(config.initialWindowSize()).isEqualTo(32768);
        assertThat(config.maxConcurrentStreams()).isEqualTo(50);
        assertThat(config.maxFrameSize()).isEqualTo(16384);
        assertThat(config.maxHeaderListSize()).isEqualTo(4096);
        assertThat(config.enablePush()).isFalse();
        assertThat(config.port()).isEqualTo(443);
        assertThat(config.host()).isEqualTo("127.0.0.1");
    }

    @Test void testHeaderTableSize() {
        var config = new Http2Config();
        config.headerTableSize(4096);
        assertThat(config.headerTableSize()).isEqualTo(4096);
    }

    @Test void testMultipleConfigsIndependent() {
        var c1 = new Http2Config().initialWindowSize(1000).maxConcurrentStreams(10);
        var c2 = new Http2Config().initialWindowSize(2000).maxConcurrentStreams(20);
        
        assertThat(c1.initialWindowSize()).isEqualTo(1000);
        assertThat(c2.initialWindowSize()).isEqualTo(2000);
    }

    @Test void testMaxFrameSizeValidationEdgeCases() {
        var config = new Http2Config();
        // Just below minimum should throw
        assertThatThrownBy(() -> config.maxFrameSize(16383))
                .isInstanceOf(IllegalArgumentException.class);
        // Just above maximum should throw
        assertThatThrownBy(() -> config.maxFrameSize(16777216))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void testNegativeWindowSize() {
        var config = new Http2Config();
        config.initialWindowSize(-1); // No validation, just stores negative value
        assertThat(config.initialWindowSize()).isEqualTo(-1);
    }

    @Test void testZeroMaxConcurrentStreams() {
        var config = new Http2Config();
        config.maxConcurrentStreams(0); // No validation for zero
        assertThat(config.maxConcurrentStreams()).isEqualTo(0);
    }
}
