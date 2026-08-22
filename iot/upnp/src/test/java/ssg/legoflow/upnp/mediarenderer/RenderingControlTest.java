package ssg.legoflow.upnp.mediarenderer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RenderingControl}.
 *
 * @since 0.1.0
 */
class RenderingControlTest {

    private RenderingControl renderingControl;
    private List<PlaybackEvent> events;

    @BeforeEach
    void setUp() {
        renderingControl = new RenderingControl();
        events = new ArrayList<>();
        renderingControl.addPlaybackListener(events::add);
    }

    @Test
    void testGetVolume() {
        // Given: default volume is 50

        // When
        int volume = renderingControl.getVolume(0, RenderingControl.CHANNEL_MASTER);

        // Then
        assertThat(volume).isEqualTo(50);
    }

    @Test
    void testSetVolume() {
        // When
        renderingControl.setVolume(0, RenderingControl.CHANNEL_MASTER, 75);

        // Then
        assertThat(renderingControl.getVolume(0, RenderingControl.CHANNEL_MASTER)).isEqualTo(75);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(PlaybackEvent.VolumeChanged.class);
        PlaybackEvent.VolumeChanged event = (PlaybackEvent.VolumeChanged) events.getFirst();
        assertThat(event.volume()).isEqualTo(75);
    }

    @Test
    void testGetMute() {
        // Given: default is not muted

        // When
        boolean muted = renderingControl.getMute(0, RenderingControl.CHANNEL_MASTER);

        // Then
        assertThat(muted).isFalse();
    }

    @Test
    void testSetMute() {
        // When
        renderingControl.setMute(0, RenderingControl.CHANNEL_MASTER, true);

        // Then
        assertThat(renderingControl.getMute(0, RenderingControl.CHANNEL_MASTER)).isTrue();
        assertThat(events).hasSize(1);
        PlaybackEvent.VolumeChanged event = (PlaybackEvent.VolumeChanged) events.getFirst();
        assertThat(event.muted()).isTrue();
    }

    @Test
    void testVolumeRange() {
        // When/Then: out-of-range volume throws
        assertThatThrownBy(() ->
                renderingControl.setVolume(0, RenderingControl.CHANNEL_MASTER, -1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                renderingControl.setVolume(0, RenderingControl.CHANNEL_MASTER, 101))
                .isInstanceOf(IllegalArgumentException.class);

        // Valid boundary values succeed
        assertThatCode(() ->
                renderingControl.setVolume(0, RenderingControl.CHANNEL_MASTER, 0))
                .doesNotThrowAnyException();

        assertThatCode(() ->
                renderingControl.setVolume(0, RenderingControl.CHANNEL_MASTER, 100))
                .doesNotThrowAnyException();
    }

    @Test
    void testChannels() {
        // When
        renderingControl.setVolume(0, RenderingControl.CHANNEL_MASTER, 80);
        renderingControl.setVolume(0, RenderingControl.CHANNEL_LF, 60);
        renderingControl.setVolume(0, RenderingControl.CHANNEL_RF, 70);

        // Then
        assertThat(renderingControl.getVolume(0, RenderingControl.CHANNEL_MASTER)).isEqualTo(80);
        assertThat(renderingControl.getVolume(0, RenderingControl.CHANNEL_LF)).isEqualTo(60);
        assertThat(renderingControl.getVolume(0, RenderingControl.CHANNEL_RF)).isEqualTo(70);
    }
}
