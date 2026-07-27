package ssg.legoflow.upnp.controlpoint;

import ssg.legoflow.upnp.dlna.DlnaMediaFormat;
import ssg.legoflow.upnp.dlna.DlnaProtocolInfo;
import ssg.legoflow.upnp.mediaserver.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MediaServerProxy}.
 *
 * @since 1.0.0
 */
class MediaServerProxyTest {

    private MediaServerProxy proxy;
    private MediaServerDevice device;

    @BeforeEach
    void setUp() {
        device = new MediaServerDevice("Test Server");
        device.setHttpPort(8200);
        buildLibrary();
        device.start();
        proxy = new MediaServerProxy(device);
    }

    @Test
    void testBrowse() {
        // When
        List<ContentItem> items = proxy.browse("10");

        // Then
        assertThat(items).hasSize(2);
        assertThat(items).extracting(ContentItem::getTitle)
                .containsExactlyInAnyOrder("Track1", "Track2");
    }

    @Test
    void testBrowseRoot() {
        // When
        List<ContentItem> items = proxy.browseRoot();

        // Then
        assertThat(items).isNotEmpty();
        assertThat(items).extracting(ContentItem::getTitle).contains("Music");
    }

    @Test
    void testSearch() {
        // When
        List<ContentItem> items = proxy.search("Track1");

        // Then
        assertThat(items).hasSizeGreaterThanOrEqualTo(1);
        assertThat(items).extracting(ContentItem::getTitle)
                .anyMatch(t -> t.contains("Track1"));
    }

    @Test
    void testGetContent() {
        // When
        ContentItem item = proxy.getContent("100");

        // Then
        assertThat(item).isNotNull();
        assertThat(item.getTitle()).isEqualTo("Track1");
        assertThat(item.getType()).isEqualTo(ContentItemType.AUDIO_ITEM);
    }

    @Test
    void testProtocolInfo() {
        // When
        List<DlnaProtocolInfo> protocols = proxy.getProtocolInfo();

        // Then
        assertThat(protocols).isNotEmpty();
        assertThat(protocols).anyMatch(p ->
                p.contentFormat().equals("audio/mpeg"));
    }

    @Test
    void testPagination() {
        // When: request page of 1 item
        BrowseResult result = proxy.browseChildren("10", 0, 1);

        // Then
        assertThat(result.numberReturned()).isEqualTo(1);
        assertThat(result.totalMatches()).isEqualTo(2);

        // When: request second page
        BrowseResult page2 = proxy.browseChildren("10", 1, 1);
        assertThat(page2.numberReturned()).isEqualTo(1);
    }

    private void buildLibrary() {
        var music = new ContentContainer("1", "0", "Music", true);
        device.addContainer(music);

        var album = new ContentContainer("10", "1", "Album1", true);
        device.addContainer(album);

        var track1 = new ContentItem("100", "10", "Track1", ContentItemType.AUDIO_ITEM);
        track1.setCreator("Artist")
                .setDuration(Duration.ofMinutes(3))
                .setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo());
        try {
            track1.setResourceUrl(URI.create("http://127.0.0.1:8200/content/track1.mp3").toURL());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        device.addContent(track1);

        var track2 = new ContentItem("101", "10", "Track2", ContentItemType.AUDIO_ITEM);
        track2.setCreator("Artist")
                .setDuration(Duration.ofMinutes(4));
        device.addContent(track2);
    }
}
