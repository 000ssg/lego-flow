package ssg.legoflow.upnp.dlna;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DlnaHeaders} — DLNA HTTP headers.
 *
 * @since 1.0.0
 */
class DlnaHeadersTest {

    @Test
    void testHeaderConstants() {
        assertThat(DlnaHeaders.CONTENT_FEATURES).isEqualTo("getcontentFeatures.dlna.org");
        assertThat(DlnaHeaders.TRANSFER_MODE).isEqualTo("transferMode.dlna.org");
        assertThat(DlnaHeaders.TIME_SEEK_RANGE).isEqualTo("TimeSeekRange.dlna.org");
    }

    @Test
    void testBuildContentFeatures() {
        // Given
        var flags = EnumSet.of(DlnaFlags.STREAMING_TRANSFER, DlnaFlags.DLNA_V15);

        // When
        var result = DlnaHeaders.buildContentFeatures("MP3", flags);

        // Then
        assertThat(result).startsWith("DLNA.ORG_PN=MP3");
        assertThat(result).contains("DLNA.ORG_OP=");
        assertThat(result).contains("DLNA.ORG_FLAGS=");
    }

    @Test
    void testBuildContentFeaturesWithTimeSeek() {
        // Given
        var flags = EnumSet.of(DlnaFlags.STREAMING_TRANSFER, DlnaFlags.LSOP_TIME_BASED_SEEK,
                DlnaFlags.DLNA_V15);

        // When
        var result = DlnaHeaders.buildContentFeatures("MP3", flags);

        // Then
        assertThat(result).contains("DLNA.ORG_OP=10"); // time seek enabled, byte seek not
    }

    @Test
    void testBuildContentFeaturesWithByteSeek() {
        // Given
        var flags = EnumSet.of(DlnaFlags.STREAMING_TRANSFER, DlnaFlags.LSOP_BYTE_BASED_SEEK,
                DlnaFlags.DLNA_V15);

        // When
        var result = DlnaHeaders.buildContentFeatures("JPEG_SM", flags);

        // Then
        assertThat(result).contains("DLNA.ORG_OP=01"); // byte seek enabled, time seek not
    }

    @Test
    void testBuildContentFeaturesFromProtocolInfo() {
        // Given
        var proto = DlnaProtocolInfo.httpGetStreaming("audio/mpeg", "MP3");

        // When
        var result = DlnaHeaders.buildContentFeatures(proto);

        // Then
        assertThat(result).contains("DLNA.ORG_PN=MP3");
        assertThat(result).contains("DLNA.ORG_OP=01");
    }

    @Test
    void testTransferModeValues() {
        assertThat(DlnaHeaders.TransferMode.STREAMING.value()).isEqualTo("Streaming");
        assertThat(DlnaHeaders.TransferMode.INTERACTIVE.value()).isEqualTo("Interactive");
        assertThat(DlnaHeaders.TransferMode.BACKGROUND.value()).isEqualTo("Background");
    }

    @Test
    void testTransferModeFromValue() {
        assertThat(DlnaHeaders.TransferMode.fromValue("Streaming"))
                .isEqualTo(DlnaHeaders.TransferMode.STREAMING);
        assertThat(DlnaHeaders.TransferMode.fromValue("Interactive"))
                .isEqualTo(DlnaHeaders.TransferMode.INTERACTIVE);
        assertThat(DlnaHeaders.TransferMode.fromValue("background"))
                .isEqualTo(DlnaHeaders.TransferMode.BACKGROUND);
    }

    @Test
    void testTransferModeFromValueUnknownThrows() {
        assertThatThrownBy(() -> DlnaHeaders.TransferMode.fromValue("Invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNegotiateTransferModeStreaming() {
        var mode = DlnaHeaders.negotiateTransferMode("Streaming", "audio/mpeg");
        assertThat(mode).isEqualTo(DlnaHeaders.TransferMode.STREAMING);
    }

    @Test
    void testNegotiateTransferModeDefaultForAudio() {
        var mode = DlnaHeaders.negotiateTransferMode(null, "audio/mpeg");
        assertThat(mode).isEqualTo(DlnaHeaders.TransferMode.STREAMING);
    }

    @Test
    void testNegotiateTransferModeDefaultForVideo() {
        var mode = DlnaHeaders.negotiateTransferMode(null, "video/mp4");
        assertThat(mode).isEqualTo(DlnaHeaders.TransferMode.STREAMING);
    }

    @Test
    void testNegotiateTransferModeDefaultForImage() {
        var mode = DlnaHeaders.negotiateTransferMode(null, "image/jpeg");
        assertThat(mode).isEqualTo(DlnaHeaders.TransferMode.INTERACTIVE);
    }

    @Test
    void testNegotiateTransferModeInvalidFallback() {
        var mode = DlnaHeaders.negotiateTransferMode("Invalid", "audio/mpeg");
        assertThat(mode).isEqualTo(DlnaHeaders.TransferMode.STREAMING);
    }

    @Test
    void testBuildTimeSeekRange() {
        var range = DlnaHeaders.buildTimeSeekRange(10.5, 60.0, 180.0);
        assertThat(range).startsWith("npt=");
        assertThat(range).contains("-");
        assertThat(range).contains("/");
    }

    @Test
    void testParseTimeSeekRange() {
        var result = DlnaHeaders.parseTimeSeekRange("npt=0:00:10-0:01:00/0:03:00");
        assertThat(result).isNotNull();
        assertThat(result[0]).isCloseTo(10.0, within(0.01));
        assertThat(result[1]).isCloseTo(60.0, within(0.01));
    }

    @Test
    void testParseTimeSeekRangeSimpleFormat() {
        var result = DlnaHeaders.parseTimeSeekRange("npt=10.5-60.0");
        assertThat(result).isNotNull();
        assertThat(result[0]).isCloseTo(10.5, within(0.01));
        assertThat(result[1]).isCloseTo(60.0, within(0.01));
    }

    @Test
    void testParseTimeSeekRangeNull() {
        assertThat(DlnaHeaders.parseTimeSeekRange(null)).isNull();
        assertThat(DlnaHeaders.parseTimeSeekRange("")).isNull();
    }

    @Test
    void testBuildResponseHeaders() {
        // Given
        var proto = DlnaProtocolInfo.httpGetStreaming("audio/mpeg", "MP3");
        var mode = DlnaHeaders.TransferMode.STREAMING;

        // When
        var headers = DlnaHeaders.buildResponseHeaders(proto, mode);

        // Then
        assertThat(headers).containsKey(DlnaHeaders.CONTENT_FEATURES);
        assertThat(headers).containsKey(DlnaHeaders.TRANSFER_MODE);
        assertThat(headers.get(DlnaHeaders.TRANSFER_MODE)).isEqualTo("Streaming");
    }

    @Test
    void testBuildResponseHeadersWildcardSkipsContentFeatures() {
        // Given
        var proto = DlnaProtocolInfo.httpGetSimple("audio/mpeg");
        var mode = DlnaHeaders.TransferMode.STREAMING;

        // When
        var headers = DlnaHeaders.buildResponseHeaders(proto, mode);

        // Then
        assertThat(headers).doesNotContainKey(DlnaHeaders.CONTENT_FEATURES);
        assertThat(headers).containsKey(DlnaHeaders.TRANSFER_MODE);
    }

    @Test
    void testFormatNptTime() {
        assertThat(DlnaHeaders.formatNptTime(0)).isEqualTo("0:00:00");
        assertThat(DlnaHeaders.formatNptTime(3661)).isEqualTo("1:01:01");
        assertThat(DlnaHeaders.formatNptTime(90.5)).startsWith("0:01:30");
    }

    @Test
    void testParseNptTime() {
        assertThat(DlnaHeaders.parseNptTime("0:00:00")).isEqualTo(0.0);
        assertThat(DlnaHeaders.parseNptTime("1:01:01")).isCloseTo(3661.0, within(0.01));
        assertThat(DlnaHeaders.parseNptTime("90.5")).isCloseTo(90.5, within(0.01));
    }
}
