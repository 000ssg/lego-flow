package ssg.legoflow.upnp.dlna;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DlnaDeviceClass} — DLNA device certification headers.
 *
 * @since 1.0.0
 */
class DlnaDeviceClassTest {

    @Test
    void testMediaServerClass() {
        var dclass = DlnaDeviceClass.mediaServer();
        assertThat(dclass.profile()).isEqualTo(DlnaProfile.DIGITAL_MEDIA_SERVER);
        assertThat(dclass.dlnaVersion()).isEqualTo("1.50");
        assertThat(dclass.toDeviceClassString()).isEqualTo("DMS-1.50");
    }

    @Test
    void testMediaRendererClass() {
        var dclass = DlnaDeviceClass.mediaRenderer();
        assertThat(dclass.profile()).isEqualTo(DlnaProfile.DIGITAL_MEDIA_RENDERER);
        assertThat(dclass.toDeviceClassString()).isEqualTo("DMR-1.50");
    }

    @Test
    void testMediaPlayerClass() {
        var dclass = DlnaDeviceClass.mediaPlayer();
        assertThat(dclass.profile()).isEqualTo(DlnaProfile.DIGITAL_MEDIA_PLAYER);
        assertThat(dclass.toDeviceClassString()).isEqualTo("DMP-1.50");
    }

    @Test
    void testMediaControllerClass() {
        var dclass = DlnaDeviceClass.mediaController();
        assertThat(dclass.profile()).isEqualTo(DlnaProfile.DIGITAL_MEDIA_CONTROLLER);
        assertThat(dclass.toDeviceClassString()).isEqualTo("DMC-1.50");
    }

    @Test
    void testCustomVersion() {
        var dclass = new DlnaDeviceClass(DlnaProfile.DIGITAL_MEDIA_SERVER, "3.0");
        assertThat(dclass.toDeviceClassString()).isEqualTo("DMS-3.0");
        assertThat(dclass.dlnaVersion()).isEqualTo("3.0");
    }

    @Test
    void testToXmlElement() {
        var dclass = DlnaDeviceClass.mediaServer();
        var xml = dclass.toXmlElement();
        assertThat(xml).contains("dlna:X_DLNADOC");
        assertThat(xml).contains("urn:schemas-dlna-org:device-1-0");
        assertThat(xml).contains("DMS-1.50");
    }

    @Test
    void testToCapabilityHeader() {
        var dclass = DlnaDeviceClass.mediaRenderer();
        assertThat(dclass.toCapabilityHeader()).isEqualTo("DMR");
    }

    @Test
    void testNamespace() {
        assertThat(DlnaDeviceClass.DLNA_DEVICE_NS)
                .isEqualTo("urn:schemas-dlna-org:device-1-0");
    }

    @Test
    void testToString() {
        var dclass = DlnaDeviceClass.mediaServer();
        assertThat(dclass.toString()).isEqualTo("DMS-1.50");
    }

    @Test
    void testNullProfileThrows() {
        assertThatThrownBy(() -> new DlnaDeviceClass(null))
                .isInstanceOf(NullPointerException.class);
    }
}
