package ssg.legoflow.upnp.mediarenderer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link AvTransport}.
 *
 * @since 0.1.0
 */
class AvTransportTest {

    private AvTransport avTransport;
    private List<PlaybackEvent> events;

    @BeforeEach
    void setUp() {
        avTransport = new AvTransport();
        events = new ArrayList<>();
        avTransport.addPlaybackListener(events::add);
    }

    @Test
    void testSetUri() {
        // Given
        String uri = "http://example.com/track.mp3";

        // When
        avTransport.setAVTransportURI(0, uri, "metadata");

        // Then
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.STOPPED);
        MediaInfo info = avTransport.getMediaInfo(0);
        assertThat(info.currentUri()).isEqualTo(uri);
        assertThat(info.nrTracks()).isEqualTo(1);
    }

    @Test
    void testPlay() {
        // Given
        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "");

        // When
        avTransport.play(0, "1");

        // Then
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.PLAYING);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(PlaybackEvent.PlayStarted.class);
    }

    @Test
    void testPause() {
        // Given
        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "");
        avTransport.play(0, "1");
        events.clear();

        // When
        avTransport.pause(0);

        // Then
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.PAUSED_PLAYBACK);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(PlaybackEvent.PlayPaused.class);
    }

    @Test
    void testStop() {
        // Given
        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "");
        avTransport.play(0, "1");
        events.clear();

        // When
        avTransport.stop(0);

        // Then
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.STOPPED);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(PlaybackEvent.PlayStopped.class);
    }

    @Test
    void testSeek() {
        // Given
        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "");
        avTransport.play(0, "1");
        events.clear();

        // When
        avTransport.seek(0, AvTransport.SeekMode.REL_TIME, "0:01:30");

        // Then
        PositionInfo pos = avTransport.getPositionInfo(0);
        assertThat(pos.relTime()).isEqualTo(Duration.ofSeconds(90));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(PlaybackEvent.PositionChanged.class);
    }

    @Test
    void testTransportInfo() {
        // Given
        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "");
        avTransport.play(0, "1");

        // When
        TransportInfo info = avTransport.getTransportInfo(0);

        // Then
        assertThat(info.currentTransportState()).isEqualTo(TransportState.PLAYING);
        assertThat(info.currentTransportStatus()).isEqualTo(TransportStatus.OK);
        assertThat(info.currentSpeed()).isEqualTo("1");
    }

    @Test
    void testPositionInfo() {
        // Given
        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "meta");
        avTransport.updatePosition(Duration.ofSeconds(30), Duration.ofMinutes(3));

        // When
        PositionInfo pos = avTransport.getPositionInfo(0);

        // Then
        assertThat(pos.track()).isEqualTo(1);
        assertThat(pos.relTime()).isEqualTo(Duration.ofSeconds(30));
        assertThat(pos.trackDuration()).isEqualTo(Duration.ofMinutes(3));
        assertThat(pos.trackUri()).isEqualTo("http://example.com/track.mp3");
    }

    @Test
    void testMediaInfo() {
        // Given
        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "metadata");

        // When
        MediaInfo info = avTransport.getMediaInfo(0);

        // Then
        assertThat(info.nrTracks()).isEqualTo(1);
        assertThat(info.currentUri()).isEqualTo("http://example.com/track.mp3");
        assertThat(info.playMedium()).isEqualTo("NETWORK");
    }

    @Test
    void testStateTransitions() {
        // Given: initial state
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.NO_MEDIA_PRESENT);

        // When: load media
        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "");
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.STOPPED);

        // When: play
        avTransport.play(0, "1");
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.PLAYING);

        // When: pause
        avTransport.pause(0);
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.PAUSED_PLAYBACK);

        // When: play again (resume)
        avTransport.play(0, "1");
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.PLAYING);

        // When: stop
        avTransport.stop(0);
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.STOPPED);
    }

    @Test
    void testInvalidState() {
        // Given: no media loaded

        // When/Then: play without media throws
        assertThatThrownBy(() -> avTransport.play(0, "1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No media present");

        // When/Then: pause without playing throws
        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "");
        assertThatThrownBy(() -> avTransport.pause(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not currently playing");
    }
}
