package ssg.legoflow.upnp.mediarenderer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Compliance tests for RenderingControl: brightness/contrast/color controls,
 * multi-channel audio, and volume range enforcement.
 *
 * @since 0.1.0
 */
class RenderingControlComplianceTest {

    private RenderingControl rc;

    @BeforeEach
    void setUp() {
        rc = new RenderingControl();
    }

    // --- Brightness ---

    @Test
    void testGetBrightnessDefault() {
        assertThat(rc.getBrightness(0)).isEqualTo(50);
    }

    @Test
    void testSetBrightness() {
        rc.setBrightness(0, 80);
        assertThat(rc.getBrightness(0)).isEqualTo(80);
    }

    @Test
    void testSetBrightnessBoundaryMin() {
        rc.setBrightness(0, 0);
        assertThat(rc.getBrightness(0)).isEqualTo(0);
    }

    @Test
    void testSetBrightnessBoundaryMax() {
        rc.setBrightness(0, 100);
        assertThat(rc.getBrightness(0)).isEqualTo(100);
    }

    @Test
    void testSetBrightnessTooLow() {
        assertThatThrownBy(() -> rc.setBrightness(0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSetBrightnessTooHigh() {
        assertThatThrownBy(() -> rc.setBrightness(0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Contrast ---

    @Test
    void testGetContrastDefault() {
        assertThat(rc.getContrast(0)).isEqualTo(50);
    }

    @Test
    void testSetContrast() {
        rc.setContrast(0, 70);
        assertThat(rc.getContrast(0)).isEqualTo(70);
    }

    @Test
    void testSetContrastOutOfRange() {
        assertThatThrownBy(() -> rc.setContrast(0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rc.setContrast(0, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Color ---

    @Test
    void testGetColorDefault() {
        assertThat(rc.getColor(0)).isEqualTo(50);
    }

    @Test
    void testSetColor() {
        rc.setColor(0, 30);
        assertThat(rc.getColor(0)).isEqualTo(30);
    }

    @Test
    void testSetColorOutOfRange() {
        assertThatThrownBy(() -> rc.setColor(0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rc.setColor(0, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Multi-channel audio ---

    @Test
    void testAllChannelsInitialized() {
        for (String channel : RenderingControl.ALL_CHANNELS) {
            assertThat(rc.getVolume(0, channel)).isEqualTo(50);
            assertThat(rc.getMute(0, channel)).isFalse();
        }
    }

    @Test
    void testPerChannelVolume() {
        rc.setVolume(0, RenderingControl.CHANNEL_MASTER, 80);
        rc.setVolume(0, RenderingControl.CHANNEL_LF, 60);
        rc.setVolume(0, RenderingControl.CHANNEL_RF, 70);
        rc.setVolume(0, RenderingControl.CHANNEL_CF, 65);
        rc.setVolume(0, RenderingControl.CHANNEL_LFE, 90);
        rc.setVolume(0, RenderingControl.CHANNEL_LS, 55);
        rc.setVolume(0, RenderingControl.CHANNEL_RS, 55);

        assertThat(rc.getVolume(0, RenderingControl.CHANNEL_MASTER)).isEqualTo(80);
        assertThat(rc.getVolume(0, RenderingControl.CHANNEL_LF)).isEqualTo(60);
        assertThat(rc.getVolume(0, RenderingControl.CHANNEL_RF)).isEqualTo(70);
        assertThat(rc.getVolume(0, RenderingControl.CHANNEL_CF)).isEqualTo(65);
        assertThat(rc.getVolume(0, RenderingControl.CHANNEL_LFE)).isEqualTo(90);
        assertThat(rc.getVolume(0, RenderingControl.CHANNEL_LS)).isEqualTo(55);
        assertThat(rc.getVolume(0, RenderingControl.CHANNEL_RS)).isEqualTo(55);
    }

    @Test
    void testPerChannelMute() {
        rc.setMute(0, RenderingControl.CHANNEL_LF, true);
        rc.setMute(0, RenderingControl.CHANNEL_RF, false);
        rc.setMute(0, RenderingControl.CHANNEL_CF, true);

        assertThat(rc.getMute(0, RenderingControl.CHANNEL_LF)).isTrue();
        assertThat(rc.getMute(0, RenderingControl.CHANNEL_RF)).isFalse();
        assertThat(rc.getMute(0, RenderingControl.CHANNEL_CF)).isTrue();
    }

    @Test
    void testGetSupportedChannels() {
        var channels = rc.getSupportedChannels();
        assertThat(channels).containsExactly("Master", "LF", "RF", "CF", "LFE", "LS", "RS");
    }

    @Test
    void testChannelConstants() {
        assertThat(RenderingControl.CHANNEL_MASTER).isEqualTo("Master");
        assertThat(RenderingControl.CHANNEL_LF).isEqualTo("LF");
        assertThat(RenderingControl.CHANNEL_RF).isEqualTo("RF");
        assertThat(RenderingControl.CHANNEL_CF).isEqualTo("CF");
        assertThat(RenderingControl.CHANNEL_LFE).isEqualTo("LFE");
        assertThat(RenderingControl.CHANNEL_LS).isEqualTo("LS");
        assertThat(RenderingControl.CHANNEL_RS).isEqualTo("RS");
    }

    // --- Volume range enforcement ---

    @Test
    void testVolumeRangeConstants() {
        assertThat(RenderingControl.MIN_VOLUME).isEqualTo(0);
        assertThat(RenderingControl.MAX_VOLUME).isEqualTo(100);
    }

    @Test
    void testVolumeRangeEnforcedOnAllChannels() {
        for (String channel : RenderingControl.ALL_CHANNELS) {
            assertThatCode(() -> rc.setVolume(0, channel, 0)).doesNotThrowAnyException();
            assertThatCode(() -> rc.setVolume(0, channel, 100)).doesNotThrowAnyException();
            assertThatThrownBy(() -> rc.setVolume(0, channel, -1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> rc.setVolume(0, channel, 101))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void testScpdContainsNewActions() {
        var scpd = rc.generateScpd();
        assertThat(scpd).contains("<name>GetBrightness</name>");
        assertThat(scpd).contains("<name>SetBrightness</name>");
        assertThat(scpd).contains("<name>GetContrast</name>");
        assertThat(scpd).contains("<name>SetContrast</name>");
        assertThat(scpd).contains("<name>GetColor</name>");
        assertThat(scpd).contains("<name>SetColor</name>");
        assertThat(scpd).contains("<allowedValue>CF</allowedValue>");
        assertThat(scpd).contains("<allowedValue>LFE</allowedValue>");
        assertThat(scpd).contains("<allowedValue>LS</allowedValue>");
        assertThat(scpd).contains("<allowedValue>RS</allowedValue>");
    }
}
