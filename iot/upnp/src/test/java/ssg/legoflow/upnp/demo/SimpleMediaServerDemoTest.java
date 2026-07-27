package ssg.legoflow.upnp.demo;

import ssg.legoflow.upnp.mediaserver.BrowseResult;
import ssg.legoflow.upnp.mediaserver.ContentDirectory;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.DidlLiteParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SimpleMediaServerDemo}.
 *
 * @since 1.0.0
 */
class SimpleMediaServerDemoTest {

    private SimpleMediaServerDemo demo;
    private DidlLiteParser parser;

    @BeforeEach
    void setUp() {
        demo = new SimpleMediaServerDemo();
        demo.start();
        parser = new DidlLiteParser();
    }

    @AfterEach
    void tearDown() {
        demo.stop();
    }

    @Test
    void testServerAdvertises() {
        // Given/When
        var server = demo.getServer();

        // Then
        assertThat(server.isRunning()).isTrue();
        assertThat(server.getFriendlyName()).isEqualTo("Lego Flow Demo Media Server");
        assertThat(server.getUdn()).startsWith("uuid:");
        assertThat(server.generateDeviceDescription()).contains("MediaServer:1");
    }

    @Test
    void testBrowseRoot() {
        // Given
        var cd = demo.getServer().getContentDirectory();

        // When
        BrowseResult result = cd.browse("0",
                ContentDirectory.BrowseFlag.BROWSE_DIRECT_CHILDREN,
                "*", 0, 0, "");

        // Then
        assertThat(result.numberReturned()).isEqualTo(3); // Music, Video, Photos
        List<ContentItem> items = parser.parse(result.didlXml());
        assertThat(items).extracting(ContentItem::getTitle)
                .containsExactlyInAnyOrder("Music", "Video", "Photos");
    }

    @Test
    void testBrowseMusic() {
        // Given
        var cd = demo.getServer().getContentDirectory();

        // When
        BrowseResult result = cd.browse("1",
                ContentDirectory.BrowseFlag.BROWSE_DIRECT_CHILDREN,
                "*", 0, 0, "");

        // Then
        List<ContentItem> items = parser.parse(result.didlXml());
        assertThat(items).extracting(ContentItem::getTitle).contains("Album1");
    }

    @Test
    void testBrowseAlbum() {
        // Given
        var cd = demo.getServer().getContentDirectory();

        // When
        BrowseResult result = cd.browse("10",
                ContentDirectory.BrowseFlag.BROWSE_DIRECT_CHILDREN,
                "*", 0, 0, "");

        // Then
        List<ContentItem> items = parser.parse(result.didlXml());
        assertThat(items).hasSize(2);
        assertThat(items).extracting(ContentItem::getTitle)
                .containsExactlyInAnyOrder("Track1.mp3", "Track2.mp3");
    }

    @Test
    void testSearchByTitle() {
        // Given
        var cd = demo.getServer().getContentDirectory();

        // When
        BrowseResult result = cd.search("0",
                "dc:title contains \"Track1\"",
                "*", 0, 0, "");

        // Then
        assertThat(result.numberReturned()).isGreaterThanOrEqualTo(1);
        List<ContentItem> items = parser.parse(result.didlXml());
        assertThat(items).extracting(ContentItem::getTitle)
                .anyMatch(t -> t.contains("Track1"));
    }
}
