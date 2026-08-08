package ssg.legoflow.upnp.controlpoint;

import ssg.legoflow.upnp.dlna.DlnaMediaFormat;
import ssg.legoflow.upnp.mediarenderer.*;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.ContentItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MediaRendererProxy}.
 *
 * @since 0.1.0
 */
class MediaRendererProxyTest {

    private MediaRendererProxy proxy;
    private MediaRendererDevice device;

    @BeforeEach
    void setUp() {
        device = new MediaRendererDevice("Test Renderer");
        device.setHttpPort(8300);
        device.start();
        proxy = new MediaRendererProxy(device);
    }

    @Test
    void testPlayItem() {
        // Given
        ContentItem item = createTrack();

        // When
        proxy.playItem(item);

        // Then
        assertThat(proxy.getTransportState()).isEqualTo(TransportState.PLAYING);
    }

    @Test
    void testPause() {
        // Given
        proxy.playItem(createTrack());

        // When
        proxy.pause();

        // Then
        assertThat(proxy.getTransportState()).isEqualTo(TransportState.PAUSED_PLAYBACK);
    }

    @Test
    void testStop() {
        // Given
        proxy.playItem(createTrack());

        // When
        proxy.stop();

        // Then
        assertThat(proxy.getTransportState()).isEqualTo(TransportState.STOPPED);
    }

    @Test
    void testSeek() {
        // Given
        proxy.playItem(createTrack());

        // When
        proxy.seek(Duration.ofSeconds(60));

        // Then
        PositionInfo pos = proxy.getPosition();
        assertThat(pos.relTime()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void testVolume() {
        // Given
        assertThat(proxy.getVolume()).isEqualTo(50); // default

        // When
        proxy.setVolume(80);

        // Then
        assertThat(proxy.getVolume()).isEqualTo(80);
    }

    @Test
    void testMute() {
        // Given
        assertThat(proxy.getMute()).isFalse();

        // When
        proxy.setMute(true);

        // Then
        assertThat(proxy.getMute()).isTrue();

        // When: unmute
        proxy.setMute(false);
        assertThat(proxy.getMute()).isFalse();
    }

    @Test
    void testTransportState() {
        // Given: initial state
        assertThat(proxy.getTransportState()).isEqualTo(TransportState.NO_MEDIA_PRESENT);

        // When: play
        proxy.playItem(createTrack());
        assertThat(proxy.getTransportState()).isEqualTo(TransportState.PLAYING);

        // When: pause
        proxy.pause();
        assertThat(proxy.getTransportState()).isEqualTo(TransportState.PAUSED_PLAYBACK);

        // When: stop
        proxy.stop();
        assertThat(proxy.getTransportState()).isEqualTo(TransportState.STOPPED);
    }

    @Test
    void testEvents() {
        // Given
        List<PlaybackEvent> events = new ArrayList<>();
        proxy.subscribeTransportEvents(events::add);

        // When
        proxy.playItem(createTrack());
        proxy.pause();
        proxy.stop();

        // Then
        assertThat(events).hasSizeGreaterThanOrEqualTo(3);
        assertThat(events.getFirst()).isInstanceOf(PlaybackEvent.PlayStarted.class);
    }

    private ContentItem createTrack() {
        var item = new ContentItem("100", "10", "Track1", ContentItemType.AUDIO_ITEM);
        item.setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo());
        try {
            item.setResourceUrl(URI.create("http://127.0.0.1:8200/content/track1.mp3").toURL());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return item;
    }
}
