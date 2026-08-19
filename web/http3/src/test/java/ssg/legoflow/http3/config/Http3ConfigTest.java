package ssg.legoflow.http3.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class Http3ConfigTest {

    @Test
    void testDefaults() {
        // Given/When
        var config = Http3Config.defaults();

        // Then
        assertThat(config.maxFieldSectionSize()).isEqualTo(8192);
        assertThat(config.qpackMaxTableCapacity()).isEqualTo(4096);
        assertThat(config.qpackBlockedStreams()).isEqualTo(100);
        assertThat(config.maxIdleTimeout()).isEqualTo(30_000);
        assertThat(config.maxConcurrentStreams()).isEqualTo(100);
        assertThat(config.initialMaxData()).isEqualTo(1_048_576);
        assertThat(config.port()).isEqualTo(443);
        assertThat(config.host()).isEqualTo("0.0.0.0");
        assertThat(config.enablePush()).isFalse();
        assertThat(config.enable0Rtt()).isFalse();
    }

    @Test
    void testHighPerformance() {
        // Given/When
        var config = Http3Config.highPerformance();

        // Then
        assertThat(config.maxFieldSectionSize()).isEqualTo(32768);
        assertThat(config.qpackMaxTableCapacity()).isEqualTo(16384);
        assertThat(config.maxConcurrentStreams()).isEqualTo(256);
        assertThat(config.initialMaxData()).isEqualTo(4_194_304);
        assertThat(config.maxIdleTimeout()).isEqualTo(60_000);
    }

    @Test
    void testLowLatency() {
        // Given/When
        var config = Http3Config.lowLatency();

        // Then
        assertThat(config.maxFieldSectionSize()).isEqualTo(4096);
        assertThat(config.qpackMaxTableCapacity()).isEqualTo(2048);
        assertThat(config.maxConcurrentStreams()).isEqualTo(50);
        assertThat(config.maxIdleTimeout()).isEqualTo(10_000);
        assertThat(config.enable0Rtt()).isTrue();
    }

    @Test
    void testFluentBuilder() {
        // Given/When
        var config = new Http3Config()
                .maxFieldSectionSize(16384)
                .qpackMaxTableCapacity(8192)
                .qpackBlockedStreams(200)
                .maxIdleTimeout(60_000)
                .maxConcurrentStreams(200)
                .initialMaxData(2_097_152)
                .port(8443)
                .host("127.0.0.1")
                .enablePush(true)
                .enable0Rtt(true);

        // Then
        assertThat(config.maxFieldSectionSize()).isEqualTo(16384);
        assertThat(config.qpackMaxTableCapacity()).isEqualTo(8192);
        assertThat(config.qpackBlockedStreams()).isEqualTo(200);
        assertThat(config.maxIdleTimeout()).isEqualTo(60_000);
        assertThat(config.maxConcurrentStreams()).isEqualTo(200);
        assertThat(config.initialMaxData()).isEqualTo(2_097_152);
        assertThat(config.port()).isEqualTo(8443);
        assertThat(config.host()).isEqualTo("127.0.0.1");
        assertThat(config.enablePush()).isTrue();
        assertThat(config.enable0Rtt()).isTrue();
    }

    @Test
    void testChainingReturnsThis() {
        // Given
        var config = new Http3Config();

        // When/Then: each setter should return the same instance
        assertThat(config.port(443)).isSameAs(config);
        assertThat(config.host("localhost")).isSameAs(config);
        assertThat(config.enablePush(true)).isSameAs(config);
    }
}
