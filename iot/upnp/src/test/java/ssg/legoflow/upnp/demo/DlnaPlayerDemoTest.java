package ssg.legoflow.upnp.demo;

import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.dlna.DlnaProtocolInfo;
import ssg.legoflow.upnp.mediarenderer.PlaybackEvent;
import ssg.legoflow.upnp.mediarenderer.TransportState;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.ContentItemType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DlnaPlayerDemo}.
 *
 * @since 1.0.0
 */
class DlnaPlayerDemoTest {

    private DlnaPlayerDemo demo;

    @BeforeEach
    void setUp() {
        demo = new DlnaPlayerDemo();
        demo.start();
    }

    @AfterEach
    void tearDown() {
        demo.stop();
    }

    @Test
    void testFullPlaybackLifecycle() {
        // Given
        var server = demo.getControlPoint().discoverMediaServers().getFirst();
        var renderer = demo.getControlPoint().discoverMediaRenderers().getFirst();

        // When: discover
        assertThat(server).isNotNull();
        assertThat(renderer).isNotNull();

        // Browse root
        List<ContentItem> rootItems = server.browseRoot();
        assertThat(rootItems).isNotEmpty();

        // Browse to a track
        ContentItem track = server.getContent("100");
        assertThat(track).isNotNull();

        // Play
        renderer.playItem(track);
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PLAYING);

        // Pause
        renderer.pause();
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PAUSED_PLAYBACK);

        // Seek
        renderer.seek(Duration.ofSeconds(30));

        // Resume
        renderer.play();
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PLAYING);

        // Stop
        renderer.stop();
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.STOPPED);
    }

    @Test
    void testEventSubscription() {
        // Given
        demo.subscribeToEvents();
        var renderer = demo.getControlPoint().discoverMediaRenderers().getFirst();
        ContentItem track = demo.getControlPoint().discoverMediaServers().getFirst().getContent("100");

        // When
        renderer.playItem(track);
        renderer.pause();
        renderer.stop();

        // Then
        List<PlaybackEvent> events = demo.getReceivedEvents();
        assertThat(events).hasSizeGreaterThanOrEqualTo(3);
        assertThat(events).anyMatch(e -> e instanceof PlaybackEvent.PlayStarted);
        assertThat(events).anyMatch(e -> e instanceof PlaybackEvent.PlayPaused);
        assertThat(events).anyMatch(e -> e instanceof PlaybackEvent.PlayStopped);
    }

    @Test
    void testPositionTracking() {
        // Given
        var renderer = demo.getControlPoint().discoverMediaRenderers().getFirst();
        ContentItem track = demo.getControlPoint().discoverMediaServers().getFirst().getContent("100");
        renderer.playItem(track);

        // When
        demo.getRendererDemo().getRenderer().getAvTransport()
                .updatePosition(Duration.ofSeconds(45), Duration.ofMinutes(3));

        // Then
        var position = renderer.getPosition();
        assertThat(position.relTime()).isEqualTo(Duration.ofSeconds(45));
        assertThat(position.trackDuration()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void testVolumeControl() {
        // Given
        var renderer = demo.getControlPoint().discoverMediaRenderers().getFirst();

        // When: set volume
        renderer.setVolume(75);
        assertThat(renderer.getVolume()).isEqualTo(75);

        // When: mute
        renderer.setMute(true);
        assertThat(renderer.getMute()).isTrue();

        // When: unmute
        renderer.setMute(false);
        assertThat(renderer.getMute()).isFalse();
    }

    @Test
    void testNextPrevious() {
        // Given
        var renderer = demo.getControlPoint().discoverMediaRenderers().getFirst();
        ContentItem track = demo.getControlPoint().discoverMediaServers().getFirst().getContent("100");
        renderer.playItem(track);

        // When
        renderer.next();
        renderer.previous();

        // Then - no exceptions, state is still valid
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PLAYING);
    }

    @Test
    void testMultipleItems() {
        // Given
        var server = demo.getControlPoint().discoverMediaServers().getFirst();
        var renderer = demo.getControlPoint().discoverMediaRenderers().getFirst();

        // When: play first track
        ContentItem track1 = server.getContent("100");
        renderer.playItem(track1);
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PLAYING);
        renderer.stop();

        // When: play second track
        ContentItem track2 = server.getContent("101");
        assertThat(track2).isNotNull();
        renderer.playItem(track2);
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PLAYING);
        renderer.stop();
    }

    @Test
    void testProtocolInfoNegotiation() {
        // Given
        var server = demo.getControlPoint().discoverMediaServers().getFirst();
        var rendererDevice = demo.getRendererDemo().getRenderer();

        // When
        List<DlnaProtocolInfo> serverProtocols = server.getProtocolInfo();
        List<DlnaProtocolInfo> rendererProtocols =
                rendererDevice.getConnectionManager().getSinkProtocols();

        // Then - both support common formats
        assertThat(serverProtocols).isNotEmpty();
        assertThat(rendererProtocols).isNotEmpty();

        // Check MP3 compatibility
        boolean compatible = serverProtocols.stream()
                .anyMatch(sp -> rendererProtocols.stream()
                        .anyMatch(rp -> sp.isCompatibleWith(rp)));
        assertThat(compatible).isTrue();
    }

    @Test
    void testGracefulShutdown() {
        // Given
        var renderer = demo.getControlPoint().discoverMediaRenderers().getFirst();
        ContentItem track = demo.getControlPoint().discoverMediaServers().getFirst().getContent("100");
        renderer.playItem(track);
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PLAYING);

        // When
        demo.stop();

        // Then
        assertThat(demo.getControlPoint().isRunning()).isFalse();
        assertThat(demo.getServerDemo().getServer().isRunning()).isFalse();
        assertThat(demo.getRendererDemo().getRenderer().isRunning()).isFalse();
    }
}
