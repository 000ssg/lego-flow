package ssg.legoflow.upnp.dlna;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DlnaProtocolInfo}.
 *
 * @since 0.1.0
 */
class DlnaProtocolInfoTest {

    @Test
    void testParse() {
        // Given
        String input = "http-get:*:audio/mpeg:DLNA.ORG_PN=MP3";

        // When
        DlnaProtocolInfo info = DlnaProtocolInfo.parse(input);

        // Then
        assertThat(info.protocol()).isEqualTo("http-get");
        assertThat(info.network()).isEqualTo("*");
        assertThat(info.contentFormat()).isEqualTo("audio/mpeg");
        assertThat(info.additionalInfo()).isEqualTo("DLNA.ORG_PN=MP3");
    }

    @Test
    void testSerialize() {
        // Given
        var info = new DlnaProtocolInfo("http-get", "*", "audio/mpeg", "DLNA.ORG_PN=MP3");

        // When
        String result = info.toString();

        // Then
        assertThat(result).isEqualTo("http-get:*:audio/mpeg:DLNA.ORG_PN=MP3");
    }

    @Test
    void testMp3Profile() {
        // Given/When
        DlnaProtocolInfo info = DlnaMediaFormat.MP3.toProtocolInfo();

        // Then
        assertThat(info.protocol()).isEqualTo("http-get");
        assertThat(info.contentFormat()).isEqualTo("audio/mpeg");
        assertThat(info.additionalInfo()).isEqualTo("DLNA.ORG_PN=MP3");
    }

    @Test
    void testVideoProfile() {
        // Given/When
        DlnaProtocolInfo info = DlnaMediaFormat.AVC_MP4_MP_SD.toProtocolInfo();

        // Then
        assertThat(info.protocol()).isEqualTo("http-get");
        assertThat(info.contentFormat()).isEqualTo("video/mp4");
        assertThat(info.additionalInfo()).isEqualTo("DLNA.ORG_PN=AVC_MP4_MP_SD");
    }

    @Test
    void testCompatibility() {
        // Given
        DlnaProtocolInfo server = DlnaProtocolInfo.httpGet("audio/mpeg", "MP3");
        DlnaProtocolInfo renderer = DlnaProtocolInfo.httpGet("audio/mpeg", "MP3");
        DlnaProtocolInfo incompatible = DlnaProtocolInfo.httpGet("video/mp4", "AVC_MP4_MP_SD");

        // When/Then
        assertThat(server.isCompatibleWith(renderer)).isTrue();
        assertThat(server.isCompatibleWith(incompatible)).isFalse();
        assertThat(server.isCompatibleWith(null)).isFalse();
    }

    @Test
    void testFlags() {
        // Given
        String flags = DlnaFlags.streamingFlags();
        DlnaProtocolInfo info = DlnaProtocolInfo.httpGetWithFlags("audio/mpeg", "MP3", flags);

        // When
        String result = info.toString();

        // Then
        assertThat(result).contains("DLNA.ORG_PN=MP3");
        assertThat(result).contains("DLNA.ORG_FLAGS=" + flags);
        assertThat(info.additionalInfo()).contains("DLNA.ORG_FLAGS=");
    }
}
