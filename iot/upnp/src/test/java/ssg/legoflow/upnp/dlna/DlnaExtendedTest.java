package ssg.legoflow.upnp.dlna;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DlnaExtendedTest {

    @Test void dlnaProtocolInfoConstruction() {
        var pi = new DlnaProtocolInfo("http-get", "*", "audio/mp3", "DLNA.ORG_PN=MP3");
        assertThat(pi.protocol()).isEqualTo("http-get");
        assertThat(pi.network()).isEqualTo("*");
    }

    @Test void dlnaDeviceClassConstants() {
        assertThat(DlnaDeviceClass.DLNA_DEVICE_NS).isEqualTo("urn:schemas-dlna-org:device-1-0");
    }

    @Test void dlnaMediaFormatValues() {
        for (var mf : DlnaMediaFormat.values()) {
            assertThat(mf.mimeType()).isNotBlank();
        }
    }

    @Test void dlnaHeadersConstants() {
        assertThat(DlnaHeaders.TRANSFER_MODE).isEqualTo("transferMode.dlna.org");
    }
}
