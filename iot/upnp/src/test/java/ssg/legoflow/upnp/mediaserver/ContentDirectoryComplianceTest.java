package ssg.legoflow.upnp.mediaserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Compliance tests for ContentDirectory:1 features: SystemUpdateID,
 * ContainerUpdateIDs, Search action, and search capabilities.
 *
 * @since 1.0.0
 */
class ContentDirectoryComplianceTest {

    private ContentDirectory cd;

    @BeforeEach
    void setUp() {
        cd = new ContentDirectory();
    }

    // --- SystemUpdateID ---

    @Test
    void testSystemUpdateIdInitiallyZero() {
        assertThat(cd.getSystemUpdateId()).isEqualTo(0);
    }

    @Test
    void testSystemUpdateIdIncrementsOnAddContent() {
        cd.addContent(new ContentItem("1", "0", "Track 1", ContentItemType.AUDIO_ITEM));
        assertThat(cd.getSystemUpdateId()).isEqualTo(1);

        cd.addContent(new ContentItem("2", "0", "Track 2", ContentItemType.AUDIO_ITEM));
        assertThat(cd.getSystemUpdateId()).isEqualTo(2);
    }

    @Test
    void testSystemUpdateIdIncrementsOnRemoveContent() {
        cd.addContent(new ContentItem("1", "0", "Track 1", ContentItemType.AUDIO_ITEM));
        long afterAdd = cd.getSystemUpdateId();

        cd.removeContent("1");
        assertThat(cd.getSystemUpdateId()).isGreaterThan(afterAdd);
    }

    @Test
    void testSystemUpdateIdIncrementsOnAddContainer() {
        cd.addContainer(new ContentContainer("music", "0", "Music", false));
        assertThat(cd.getSystemUpdateId()).isGreaterThan(0);
    }

    @Test
    void testSystemUpdateIdDoesNotChangeOnRemoveNonExistent() {
        cd.addContent(new ContentItem("1", "0", "Track 1", ContentItemType.AUDIO_ITEM));
        long before = cd.getSystemUpdateId();
        cd.removeContent("nonexistent");
        assertThat(cd.getSystemUpdateId()).isEqualTo(before);
    }

    // --- ContainerUpdateIDs ---

    @Test
    void testContainerUpdateIdsInitiallyEmpty() {
        assertThat(cd.getContainerUpdateIds()).isEmpty();
    }

    @Test
    void testContainerUpdateIdsTracksChanges() {
        cd.addContent(new ContentItem("1", "0", "Track 1", ContentItemType.AUDIO_ITEM));
        assertThat(cd.getContainerUpdateCount("0")).isEqualTo(1);
    }

    @Test
    void testContainerUpdateIdsMultipleAdds() {
        cd.addContent(new ContentItem("1", "0", "Track 1", ContentItemType.AUDIO_ITEM));
        cd.addContent(new ContentItem("2", "0", "Track 2", ContentItemType.AUDIO_ITEM));
        cd.addContent(new ContentItem("3", "0", "Track 3", ContentItemType.AUDIO_ITEM));
        assertThat(cd.getContainerUpdateCount("0")).isEqualTo(3);
    }

    @Test
    void testContainerUpdateIdsFormatString() {
        cd.addContent(new ContentItem("1", "0", "Track", ContentItemType.AUDIO_ITEM));
        String ids = cd.getContainerUpdateIds();
        assertThat(ids).contains("0");
        assertThat(ids).contains(",");
    }

    @Test
    void testContainerUpdateCountForUnknownContainer() {
        assertThat(cd.getContainerUpdateCount("nonexistent")).isEqualTo(0);
    }

    // --- Search ---

    @Test
    void testSearchByTitleContains() {
        cd.addContent(new ContentItem("1", "0", "Love Song", ContentItemType.AUDIO_ITEM));
        cd.addContent(new ContentItem("2", "0", "Rock Anthem", ContentItemType.AUDIO_ITEM));

        var result = cd.search("0", "dc:title contains \"Love\"", "*", 0, 0, "");
        assertThat(result.numberReturned()).isEqualTo(1);
        assertThat(result.totalMatches()).isEqualTo(1);
        assertThat(result.didlXml()).contains("Love Song");
    }

    @Test
    void testSearchWildcardMatchesAll() {
        cd.addContent(new ContentItem("1", "0", "Track 1", ContentItemType.AUDIO_ITEM));
        cd.addContent(new ContentItem("2", "0", "Track 2", ContentItemType.AUDIO_ITEM));

        var result = cd.search("0", "*", "*", 0, 0, "");
        assertThat(result.totalMatches()).isEqualTo(2);
    }

    @Test
    void testSearchInSpecificContainer() {
        var music = new ContentContainer("music", "0", "Music", false);
        cd.addContainer(music);
        var item1 = new ContentItem("1", "music", "Song A", ContentItemType.AUDIO_ITEM);
        cd.addContent(item1);
        var item2 = new ContentItem("2", "0", "Song B", ContentItemType.AUDIO_ITEM);
        cd.addContent(item2);

        var result = cd.search("music", "*", "*", 0, 0, "");
        assertThat(result.totalMatches()).isEqualTo(1);
    }

    @Test
    void testSearchCapabilities() {
        assertThat(cd.getSearchCapabilities()).contains("dc:title");
        assertThat(cd.getSearchCapabilities()).contains("upnp:class");
    }

    @Test
    void testSortCapabilities() {
        assertThat(cd.getSortCapabilities()).contains("dc:title");
    }

    @Test
    void testSearchReturnsUpdateId() {
        cd.addContent(new ContentItem("1", "0", "Track", ContentItemType.AUDIO_ITEM));
        var result = cd.search("0", "*", "*", 0, 0, "");
        assertThat(result.updateId()).isGreaterThan(0);
    }

    @Test
    void testSearchPagination() {
        for (int i = 1; i <= 10; i++) {
            cd.addContent(new ContentItem(String.valueOf(i), "0", "Track " + i, ContentItemType.AUDIO_ITEM));
        }

        var result = cd.search("0", "*", "*", 0, 3, "");
        assertThat(result.numberReturned()).isEqualTo(3);
        assertThat(result.totalMatches()).isEqualTo(10);
    }

    // --- SCPD ---

    @Test
    void testScpdContainsSearchAction() {
        var scpd = cd.generateScpd();
        assertThat(scpd).contains("<name>Search</name>");
    }

    @Test
    void testScpdContainsSystemUpdateIdVariable() {
        var scpd = cd.generateScpd();
        assertThat(scpd).contains("<name>SystemUpdateID</name>");
    }

    @Test
    void testScpdContainsContainerUpdateIdsVariable() {
        var scpd = cd.generateScpd();
        assertThat(scpd).contains("<name>ContainerUpdateIDs</name>");
    }

    @Test
    void testScpdContainsGetSystemUpdateIdAction() {
        var scpd = cd.generateScpd();
        assertThat(scpd).contains("<name>GetSystemUpdateID</name>");
    }
}
