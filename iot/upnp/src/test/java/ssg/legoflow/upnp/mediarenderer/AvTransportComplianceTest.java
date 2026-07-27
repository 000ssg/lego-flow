package ssg.legoflow.upnp.mediarenderer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Compliance tests for AVTransport new features: SetNextAVTransportURI,
 * GetDeviceCapabilities, GetTransportSettings, and gapless playback.
 *
 * @since 1.0.0
 */
class AvTransportComplianceTest {

    private AvTransport avTransport;
    private List<PlaybackEvent> events;

    @BeforeEach
    void setUp() {
        avTransport = new AvTransport();
        events = new ArrayList<>();
        avTransport.addPlaybackListener(events::add);
    }

    @Test
    void testSetNextAVTransportURI() {
        // Given
        avTransport.setAVTransportURI(0, "http://example.com/track1.mp3", "");

        // When
        avTransport.setNextAVTransportURI(0, "http://example.com/track2.mp3", "metadata2");

        // Then
        var mediaInfo = avTransport.getMediaInfo(0);
        assertThat(mediaInfo.nextUri()).isEqualTo("http://example.com/track2.mp3");
        assertThat(mediaInfo.nextUriMetadata()).isEqualTo("metadata2");
    }

    @Test
    void testGaplessPlayback() {
        // Given: playing track1 with track2 queued
        avTransport.setAVTransportURI(0, "http://example.com/track1.mp3", "meta1");
        avTransport.play(0, "1");
        avTransport.setNextAVTransportURI(0, "http://example.com/track2.mp3", "meta2");

        // When: track1 completes
        avTransport.playbackCompleted();

        // Then: should auto-transition to track2 without stopping
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.PLAYING);
        var posInfo = avTransport.getPositionInfo(0);
        assertThat(posInfo.trackUri()).isEqualTo("http://example.com/track2.mp3");

        // And: next URI should be cleared
        var mediaInfo = avTransport.getMediaInfo(0);
        assertThat(mediaInfo.nextUri()).isEmpty();
    }

    @Test
    void testPlaybackCompletedWithoutNextUri() {
        // Given: playing without a next URI
        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "");
        avTransport.play(0, "1");

        // When
        avTransport.playbackCompleted();

        // Then: should stop normally
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.STOPPED);
        assertThat(events).anySatisfy(e -> assertThat(e).isInstanceOf(PlaybackEvent.PlayCompleted.class));
    }

    @Test
    void testSetNextAVTransportURINullThrows() {
        assertThatThrownBy(() -> avTransport.setNextAVTransportURI(0, null, ""))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGetDeviceCapabilities() {
        // When
        var caps = avTransport.getDeviceCapabilities(0);

        // Then
        assertThat(caps).isNotNull();
        assertThat(caps.playMedia()).contains("NETWORK");
        assertThat(caps.recMedia()).isEqualTo("NOT_IMPLEMENTED");
        assertThat(caps.recQualityModes()).isEqualTo("NOT_IMPLEMENTED");
    }

    @Test
    void testGetTransportSettings() {
        // When
        var settings = avTransport.getTransportSettings(0);

        // Then
        assertThat(settings).isNotNull();
        assertThat(settings.playMode()).isEqualTo(AvTransport.PlayMode.NORMAL);
        assertThat(settings.recQualityMode()).isEqualTo("NOT_IMPLEMENTED");
    }

    @Test
    void testSetPlayMode() {
        // When
        avTransport.setPlayMode(0, AvTransport.PlayMode.SHUFFLE);

        // Then
        var settings = avTransport.getTransportSettings(0);
        assertThat(settings.playMode()).isEqualTo(AvTransport.PlayMode.SHUFFLE);
    }

    @Test
    void testPlayModeValues() {
        assertThat(AvTransport.PlayMode.NORMAL.value()).isEqualTo("NORMAL");
        assertThat(AvTransport.PlayMode.SHUFFLE.value()).isEqualTo("SHUFFLE");
        assertThat(AvTransport.PlayMode.REPEAT_ONE.value()).isEqualTo("REPEAT_ONE");
        assertThat(AvTransport.PlayMode.REPEAT_ALL.value()).isEqualTo("REPEAT_ALL");
        assertThat(AvTransport.PlayMode.RANDOM.value()).isEqualTo("RANDOM");
    }

    @Test
    void testPlayModeFromValue() {
        assertThat(AvTransport.PlayMode.fromValue("NORMAL")).isEqualTo(AvTransport.PlayMode.NORMAL);
        assertThat(AvTransport.PlayMode.fromValue("SHUFFLE")).isEqualTo(AvTransport.PlayMode.SHUFFLE);
        assertThatThrownBy(() -> AvTransport.PlayMode.fromValue("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSetPlayModeNullThrows() {
        assertThatThrownBy(() -> avTransport.setPlayMode(0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGaplessPlaybackFiresPlayStartedEvent() {
        // Given
        avTransport.setAVTransportURI(0, "http://example.com/track1.mp3", "");
        avTransport.play(0, "1");
        avTransport.setNextAVTransportURI(0, "http://example.com/track2.mp3", "meta2");
        events.clear();

        // When
        avTransport.playbackCompleted();

        // Then
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(PlaybackEvent.PlayStarted.class);
        var started = (PlaybackEvent.PlayStarted) events.getFirst();
        assertThat(started.uri()).isEqualTo("http://example.com/track2.mp3");
    }

    @Test
    void testScpdContainsNewActions() {
        var scpd = avTransport.generateScpd();
        assertThat(scpd).contains("<name>SetNextAVTransportURI</name>");
        assertThat(scpd).contains("<name>GetDeviceCapabilities</name>");
        assertThat(scpd).contains("<name>GetTransportSettings</name>");
        assertThat(scpd).contains("<name>CurrentPlayMode</name>");
    }

    @Test
    void testDeviceCapabilitiesRecord() {
        var caps = new AvTransport.DeviceCapabilities("NETWORK", "NONE", "2:HIGH");
        assertThat(caps.playMedia()).isEqualTo("NETWORK");
        assertThat(caps.recMedia()).isEqualTo("NONE");
        assertThat(caps.recQualityModes()).isEqualTo("2:HIGH");
    }

    @Test
    void testTransportSettingsRecord() {
        var settings = new AvTransport.TransportSettings(AvTransport.PlayMode.REPEAT_ALL, "2:HIGH");
        assertThat(settings.playMode()).isEqualTo(AvTransport.PlayMode.REPEAT_ALL);
        assertThat(settings.recQualityMode()).isEqualTo("2:HIGH");
    }
}
