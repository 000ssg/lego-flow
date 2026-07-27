package ssg.legoflow.http3;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class Http3SettingsTest {

    @Test
    void testDefaultValues() {
        // Given/When
        var settings = new Http3Settings();

        // Then
        assertThat(settings.maxFieldSectionSize()).isEqualTo(8192);
        assertThat(settings.qpackMaxTableCapacity()).isEqualTo(4096);
        assertThat(settings.qpackBlockedStreams()).isEqualTo(100);
    }

    @Test
    void testSetAndGet() {
        // Given
        var settings = new Http3Settings();

        // When
        settings.set(Http3Settings.SETTINGS_MAX_FIELD_SECTION_SIZE, 16384);

        // Then
        assertThat(settings.maxFieldSectionSize()).isEqualTo(16384);
    }

    @Test
    void testBuilder() {
        // Given/When
        var settings = Http3Settings.builder()
                .maxFieldSectionSize(32768)
                .qpackMaxTableCapacity(8192)
                .qpackBlockedStreams(200)
                .build();

        // Then
        assertThat(settings.maxFieldSectionSize()).isEqualTo(32768);
        assertThat(settings.qpackMaxTableCapacity()).isEqualTo(8192);
        assertThat(settings.qpackBlockedStreams()).isEqualTo(200);
    }

    @Test
    void testEncodeDecodeRoundtrip() {
        // Given
        var original = Http3Settings.builder()
                .maxFieldSectionSize(16384)
                .qpackMaxTableCapacity(2048)
                .qpackBlockedStreams(50)
                .build();

        // When
        var encoded = original.encode();
        var decoded = Http3Settings.decode(encoded);

        // Then
        assertThat(decoded.maxFieldSectionSize()).isEqualTo(16384);
        assertThat(decoded.qpackMaxTableCapacity()).isEqualTo(2048);
        assertThat(decoded.qpackBlockedStreams()).isEqualTo(50);
    }

    @Test
    void testApplyFrom() {
        // Given
        var settings = new Http3Settings();
        var override = Http3Settings.builder()
                .maxFieldSectionSize(32768)
                .build();

        // When
        settings.applyFrom(override);

        // Then
        assertThat(settings.maxFieldSectionSize()).isEqualTo(32768);
    }

    @Test
    void testToMap() {
        // Given
        var settings = new Http3Settings();

        // When
        var map = settings.toMap();

        // Then
        assertThat(map).isNotEmpty();
        assertThat(map).containsKey(Http3Settings.SETTINGS_MAX_FIELD_SECTION_SIZE);
    }

    @Test
    void testEncodeDecodeDefaults() {
        // Given
        var original = new Http3Settings();

        // When
        var encoded = original.encode();
        var decoded = Http3Settings.decode(encoded);

        // Then
        assertThat(decoded.maxFieldSectionSize()).isEqualTo(original.maxFieldSectionSize());
        assertThat(decoded.qpackMaxTableCapacity()).isEqualTo(original.qpackMaxTableCapacity());
        assertThat(decoded.qpackBlockedStreams()).isEqualTo(original.qpackBlockedStreams());
    }
}
