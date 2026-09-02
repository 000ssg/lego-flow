package ssg.legoflow.upnp.mediaserver;

import ssg.legoflow.upnp.dlna.DlnaMediaFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ContentDirectory}.
 *
 * @since 0.1.0
 */
class ContentDirectoryTest {

    private ContentDirectory contentDirectory;
    private DidlLiteParser parser;

    @BeforeEach
    void setUp() {
        contentDirectory = new ContentDirectory();
        parser = new DidlLiteParser();
        buildTestLibrary();
    }

    @Test
    void testBrowse() {
        // Given
        // Root container has children added in setUp

        // When
        BrowseResult result = contentDirectory.browse("0",
                ContentDirectory.BrowseFlag.BROWSE_DIRECT_CHILDREN,
                "*", 0, 0, "");

        // Then
        assertThat(result.numberReturned()).isGreaterThan(0);
        assertThat(result.totalMatches()).isGreaterThan(0);
        List<ContentItem> items = parser.parse(result.didlXml());
        assertThat(items).isNotEmpty();
    }

    @Test
    void testBrowseMetadata() {
        // Given
        String objectId = "100";

        // When
        BrowseResult result = contentDirectory.browse(objectId,
                ContentDirectory.BrowseFlag.BROWSE_METADATA,
                "*", 0, 0, "");

        // Then
        assertThat(result.numberReturned()).isEqualTo(1);
        assertThat(result.totalMatches()).isEqualTo(1);
        List<ContentItem> items = parser.parse(result.didlXml());
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getTitle()).isEqualTo("Track1");
    }

    @Test
    void testSearch() {
        // When
        BrowseResult result = contentDirectory.search("0",
                "dc:title contains \"Track1\"",
                "*", 0, 0, "");

        // Then
        assertThat(result.numberReturned()).isGreaterThanOrEqualTo(1);
        List<ContentItem> items = parser.parse(result.didlXml());
        assertThat(items).extracting(ContentItem::getTitle)
                .anyMatch(t -> t.contains("Track1"));
    }

    @Test
    void testSearchCapabilities() {
        // When
        String caps = contentDirectory.getSearchCapabilities();

        // Then
        assertThat(caps).contains("dc:title");
        assertThat(caps).contains("dc:creator");
    }

    @Test
    void testSystemUpdateId() {
        // Given
        long initialId = contentDirectory.getSystemUpdateId();

        // When
        var item = new ContentItem("999", "0", "New Item", ContentItemType.AUDIO_ITEM);
        contentDirectory.addContent(item);

        // Then
        assertThat(contentDirectory.getSystemUpdateId()).isGreaterThan(initialId);
    }

    @Test
    void testDidlLiteParsing() {
        // Given
        BrowseResult result = contentDirectory.browse("10",
                ContentDirectory.BrowseFlag.BROWSE_DIRECT_CHILDREN,
                "*", 0, 0, "");

        // When
        List<ContentItem> items = parser.parse(result.didlXml());

        // Then
        assertThat(items).isNotEmpty();
        for (ContentItem item : items) {
            assertThat(item.getId()).isNotEmpty();
            assertThat(item.getTitle()).isNotEmpty();
        }
    }

    @Test
    void testContentTree() {
        // Given/When - browse root
        BrowseResult rootResult = contentDirectory.browse("0",
                ContentDirectory.BrowseFlag.BROWSE_DIRECT_CHILDREN,
                "*", 0, 0, "");
        List<ContentItem> rootItems = parser.parse(rootResult.didlXml());

        // Then - root has Music container
        assertThat(rootItems).extracting(ContentItem::getTitle)
                .contains("Music");

        // Browse Music container
        BrowseResult musicResult = contentDirectory.browse("1",
                ContentDirectory.BrowseFlag.BROWSE_DIRECT_CHILDREN,
                "*", 0, 0, "");
        List<ContentItem> musicItems = parser.parse(musicResult.didlXml());
        assertThat(musicItems).isNotEmpty();
    }

    @Test
    void testAddRemoveContent() {
        // Given
        long initialUpdateId = contentDirectory.getSystemUpdateId();
        var item = new ContentItem("888", "0", "Temp Track", ContentItemType.AUDIO_ITEM);

        // When - add
        contentDirectory.addContent(item);
        assertThat(contentDirectory.getItem("888")).isNotNull();
        assertThat(contentDirectory.getSystemUpdateId()).isGreaterThan(initialUpdateId);

        // When - remove
        long afterAddUpdateId = contentDirectory.getSystemUpdateId();
        boolean removed = contentDirectory.removeContent("888");

        // Then
        assertThat(removed).isTrue();
        assertThat(contentDirectory.getItem("888")).isNull();
        assertThat(contentDirectory.getSystemUpdateId()).isGreaterThan(afterAddUpdateId);
    }

    private void buildTestLibrary() {
        var music = new ContentContainer("1", "0", "Music", true);
        contentDirectory.addContainer(music);

        var album = new ContentContainer("10", "1", "Album1", true);
        contentDirectory.addContainer(album);

        var track1 = new ContentItem("100", "10", "Track1", ContentItemType.AUDIO_ITEM);
        track1.setCreator("Artist1")
                .setDuration(Duration.ofMinutes(3))
                .setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo());
        contentDirectory.addContent(track1);

        var track2 = new ContentItem("101", "10", "Track2", ContentItemType.AUDIO_ITEM);
        track2.setCreator("Artist1")
                .setDuration(Duration.ofMinutes(4));
        contentDirectory.addContent(track2);
    }
}
