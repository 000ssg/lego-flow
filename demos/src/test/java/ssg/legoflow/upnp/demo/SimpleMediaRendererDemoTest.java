package ssg.legoflow.upnp.demo;

import ssg.legoflow.upnp.mediarenderer.PlaybackEvent;
import ssg.legoflow.upnp.mediarenderer.TransportState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SimpleMediaRendererDemo}.
 *
 * @since 0.1.0
 */
class SimpleMediaRendererDemoTest {

    private SimpleMediaRendererDemo demo;

    @BeforeEach
    void setUp() {
        demo = new SimpleMediaRendererDemo();
        demo.start();
    }

    @AfterEach
    void tearDown() {
        demo.stop();
    }

    @Test
    void testRendererAdvertises() {
        // Given/When
        var renderer = demo.getRenderer();

        // Then
        assertThat(renderer.isRunning()).isTrue();
        assertThat(renderer.getFriendlyName()).isEqualTo("Lego Flow Demo Renderer");
        assertThat(renderer.getUdn()).startsWith("uuid:");
        assertThat(renderer.generateDeviceDescription()).contains("MediaRenderer:1");
    }

    @Test
    void testPlayPauseStop() {
        // Given
        var avTransport = demo.getRenderer().getAvTransport();
        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "");

        // When: play
        avTransport.play(0, "1");
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.PLAYING);

        // When: pause
        avTransport.pause(0);
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.PAUSED_PLAYBACK);

        // When: stop
        avTransport.stop(0);
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.STOPPED);

        // Then: events were logged
        var events = demo.getLoggingListener().getEvents();
        assertThat(events).hasSizeGreaterThanOrEqualTo(3);
        assertThat(events.get(0)).isInstanceOf(PlaybackEvent.PlayStarted.class);
        assertThat(events.get(1)).isInstanceOf(PlaybackEvent.PlayPaused.class);
        assertThat(events.get(2)).isInstanceOf(PlaybackEvent.PlayStopped.class);
    }

    @Test
    void testSeekPosition() {
        // Given
        var avTransport = demo.getRenderer().getAvTransport();
        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "");
        avTransport.play(0, "1");

        // When
        avTransport.seek(0, ssg.legoflow.upnp.mediarenderer.AvTransport.SeekMode.REL_TIME, "0:01:30");

        // Then
        var pos = avTransport.getPositionInfo(0);
        assertThat(pos.relTime()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void testVolumeControl() {
        // Given
        var rendering = demo.getRenderer().getRenderingControl();

        // When
        rendering.setVolume(0, "Master", 75);

        // Then
        assertThat(rendering.getVolume(0, "Master")).isEqualTo(75);

        // Check event was logged
        var events = demo.getLoggingListener().getEvents();
        assertThat(events).anyMatch(e -> e instanceof PlaybackEvent.VolumeChanged);
    }

    @Test
    void testTransportStateChanges() {
        // Given
        var avTransport = demo.getRenderer().getAvTransport();
        demo.getLoggingListener().clear();

        // When: full lifecycle
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.NO_MEDIA_PRESENT);

        avTransport.setAVTransportURI(0, "http://example.com/track.mp3", "");
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.STOPPED);

        avTransport.play(0, "1");
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.PLAYING);

        avTransport.pause(0);
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.PAUSED_PLAYBACK);

        avTransport.play(0, "1");
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.PLAYING);

        avTransport.stop(0);
        assertThat(avTransport.getTransportState()).isEqualTo(TransportState.STOPPED);

        // Then
        var events = demo.getLoggingListener().getEvents();
        assertThat(events).hasSizeGreaterThanOrEqualTo(4);
    }
}
