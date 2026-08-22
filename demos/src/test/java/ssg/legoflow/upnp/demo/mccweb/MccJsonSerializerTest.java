package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.upnp.controlpoint.DeviceProxy;
import ssg.legoflow.upnp.mediarenderer.PositionInfo;
import ssg.legoflow.upnp.mediarenderer.TransportInfo;
import ssg.legoflow.upnp.mediarenderer.TransportState;
import ssg.legoflow.upnp.mediarenderer.TransportStatus;
import ssg.legoflow.upnp.mediaserver.ContentContainer;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.ContentItemType;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link MccJsonSerializer}.
 *
 * @since 0.1.0
 */
class MccJsonSerializerTest {

    @Test
    void testDeviceToJson() {
        // Given
        URL baseUrl = createUrl("http://localhost:8200");
        DeviceProxy device = new DeviceProxy("uuid:test-1", "Test Server",
                "urn:schemas-upnp-org:device:MediaServer:1", baseUrl, "<xml/>");

        // When
        String json = MccJsonSerializer.deviceToJson(device);

        // Then
        assertThat(json).contains("\"udn\":\"uuid:test-1\"");
        assertThat(json).contains("\"friendlyName\":\"Test Server\"");
        assertThat(json).contains("\"deviceType\":\"urn:schemas-upnp-org:device:MediaServer:1\"");
    }

    @Test
    void testContentItemToJson() {
        // Given: audio item
        var audioItem = new ContentItem("100", "10", "Track1.mp3", ContentItemType.AUDIO_ITEM);
        audioItem.setCreator("Demo Artist")
                .setDuration(Duration.ofMinutes(3).plusSeconds(45))
                .setSize(5_200_000L)
                .setResourceUrl(createUrl("http://localhost:8200/content/track1.mp3"));

        // When
        String json = MccJsonSerializer.contentItemToJson(audioItem);

        // Then
        assertThat(json).contains("\"id\":\"100\"");
        assertThat(json).contains("\"title\":\"Track1.mp3\"");
        assertThat(json).contains("\"type\":\"AUDIO_ITEM\"");
        assertThat(json).contains("\"creator\":\"Demo Artist\"");
        assertThat(json).contains("\"duration\":\"0:03:45\"");
        assertThat(json).contains("\"size\":5200000");

        // Given: video item
        var videoItem = new ContentItem("200", "2", "Movie.mp4", ContentItemType.VIDEO_ITEM);
        videoItem.setResolution("1920x1080")
                .setDuration(Duration.ofHours(1).plusMinutes(30));

        // When
        String videoJson = MccJsonSerializer.contentItemToJson(videoItem);

        // Then
        assertThat(videoJson).contains("\"type\":\"VIDEO_ITEM\"");
        assertThat(videoJson).contains("\"resolution\":\"1920x1080\"");

        // Given: image item
        var imageItem = new ContentItem("300", "3", "Photo.jpg", ContentItemType.IMAGE_ITEM);
        imageItem.setResolution("4000x3000");

        // When
        String imageJson = MccJsonSerializer.contentItemToJson(imageItem);

        // Then
        assertThat(imageJson).contains("\"type\":\"IMAGE_ITEM\"");
    }

    @Test
    void testContainerToJson() {
        // Given
        var container = new ContentContainer("1", "0", "Music", true);
        var child = new ContentItem("100", "1", "Track.mp3", ContentItemType.AUDIO_ITEM);
        container.addChild(child);

        // When
        String json = MccJsonSerializer.contentContainerToJson(container);

        // Then
        assertThat(json).contains("\"id\":\"1\"");
        assertThat(json).contains("\"title\":\"Music\"");
        assertThat(json).contains("\"childCount\":1");
        assertThat(json).contains("\"children\":[");
        assertThat(json).contains("\"id\":\"100\"");
    }

    @Test
    void testTransportInfoToJson() {
        // Given
        var transportInfo = new TransportInfo(TransportState.PLAYING, TransportStatus.OK, "1");
        var positionInfo = new PositionInfo(1, Duration.ofMinutes(3).plusSeconds(45),
                "", "http://example.com/track.mp3",
                Duration.ofMinutes(1).plusSeconds(30), Duration.ofMinutes(1).plusSeconds(30),
                0, 0);

        // When
        String json = MccJsonSerializer.transportInfoToJson(transportInfo, positionInfo);

        // Then
        assertThat(json).contains("\"state\":\"PLAYING\"");
        assertThat(json).contains("\"speed\":\"1\"");
        assertThat(json).contains("\"status\":\"OK\"");
        assertThat(json).contains("\"track\":1");
        assertThat(json).contains("\"trackDuration\":\"0:03:45\"");
        assertThat(json).contains("\"relTime\":\"0:01:30\"");
    }

    @Test
    void testVolumeToJson() {
        // When
        String json = MccJsonSerializer.volumeToJson(75, false);

        // Then
        assertThat(json).isEqualTo("{\"volume\":75,\"muted\":false}");

        // When: muted
        String mutedJson = MccJsonSerializer.volumeToJson(0, true);

        // Then
        assertThat(mutedJson).isEqualTo("{\"volume\":0,\"muted\":true}");
    }

    @Test
    void testParsePlayRequest() {
        // Given
        String json = "{\"itemUri\":\"http://example.com/track.mp3\",\"itemMetadata\":\"<meta/>\"}";

        // When
        var request = MccJsonSerializer.parsePlayRequest(json);

        // Then
        assertThat(request.uri()).isEqualTo("http://example.com/track.mp3");
        assertThat(request.metadata()).isEqualTo("<meta/>");

        // Given: empty body for resume
        var resumeRequest = MccJsonSerializer.parsePlayRequest(null);

        // Then
        assertThat(resumeRequest.uri()).isNull();
    }

    @Test
    void testParseSeekRequest() {
        // Given
        String json = "{\"position\":\"0:02:30\"}";

        // When
        Duration position = MccJsonSerializer.parseSeekRequest(json);

        // Then
        assertThat(position).isEqualTo(Duration.ofMinutes(2).plusSeconds(30));
    }

    @Test
    void testParseVolumeRequest() {
        // Given
        String json = "{\"volume\":85}";

        // When
        int volume = MccJsonSerializer.parseVolumeRequest(json);

        // Then
        assertThat(volume).isEqualTo(85);

        // Given: mute request
        String muteJson = "{\"muted\":true}";

        // When
        boolean muted = MccJsonSerializer.parseMuteRequest(muteJson);

        // Then
        assertThat(muted).isTrue();
    }

    private static URL createUrl(String url) {
        try {
            return URI.create(url).toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
