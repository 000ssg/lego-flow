package ssg.legoflow.upnp.mediaserver;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SearchCriteria} — UPnP ContentDirectory search query language.
 *
 * @since 0.1.0
 */
class SearchCriteriaTest {

    @Test
    void testWildcardMatchesAll() {
        // Given
        var predicate = SearchCriteria.parse("*");
        var item = new ContentItem("1", "0", "Any Title", ContentItemType.AUDIO_ITEM);

        // Then
        assertThat(predicate.test(item)).isTrue();
    }

    @Test
    void testEmptyMatchesAll() {
        // Given
        var predicate = SearchCriteria.parse("");
        var item = new ContentItem("1", "0", "Any Title", ContentItemType.AUDIO_ITEM);

        // Then
        assertThat(predicate.test(item)).isTrue();
    }

    @Test
    void testTitleContains() {
        // Given
        var predicate = SearchCriteria.parse("dc:title contains \"love\"");
        var match = new ContentItem("1", "0", "I Love Rock", ContentItemType.AUDIO_ITEM);
        var noMatch = new ContentItem("2", "0", "Hate Everything", ContentItemType.AUDIO_ITEM);

        // Then
        assertThat(predicate.test(match)).isTrue();
        assertThat(predicate.test(noMatch)).isFalse();
    }

    @Test
    void testTitleContainsCaseInsensitive() {
        // Given
        var predicate = SearchCriteria.parse("dc:title contains \"ROCK\"");
        var item = new ContentItem("1", "0", "Classic Rock Hits", ContentItemType.AUDIO_ITEM);

        // Then
        assertThat(predicate.test(item)).isTrue();
    }

    @Test
    void testTitleDoesNotContain() {
        // Given
        var predicate = SearchCriteria.parse("dc:title doesNotContain \"jazz\"");
        var match = new ContentItem("1", "0", "Rock Music", ContentItemType.AUDIO_ITEM);
        var noMatch = new ContentItem("2", "0", "Jazz Standards", ContentItemType.AUDIO_ITEM);

        // Then
        assertThat(predicate.test(match)).isTrue();
        assertThat(predicate.test(noMatch)).isFalse();
    }

    @Test
    void testCreatorEquals() {
        // Given
        var predicate = SearchCriteria.parse("dc:creator = \"Beatles\"");
        var match = new ContentItem("1", "0", "Hey Jude", ContentItemType.AUDIO_ITEM);
        match.setCreator("Beatles");
        var noMatch = new ContentItem("2", "0", "Stairway", ContentItemType.AUDIO_ITEM);
        noMatch.setCreator("Led Zeppelin");

        // Then
        assertThat(predicate.test(match)).isTrue();
        assertThat(predicate.test(noMatch)).isFalse();
    }

    @Test
    void testClassDerivedFrom() {
        // Given
        var predicate = SearchCriteria.parse("upnp:class derivedfrom \"object.item.audioItem\"");
        var audioItem = new ContentItem("1", "0", "Song", ContentItemType.AUDIO_ITEM);
        var videoItem = new ContentItem("2", "0", "Movie", ContentItemType.VIDEO_ITEM);

        // Then
        assertThat(predicate.test(audioItem)).isTrue();
        assertThat(predicate.test(videoItem)).isFalse();
    }

    @Test
    void testClassDerivedFromAudioItem() {
        // Given: "derivedfrom" matches the class itself and any sub-class
        var predicate = SearchCriteria.parse("upnp:class derivedfrom \"object.item.audioItem\"");
        var audioItem = new ContentItem("1", "0", "Song", ContentItemType.AUDIO_ITEM);
        var imageItem = new ContentItem("2", "0", "Photo", ContentItemType.IMAGE_ITEM);

        // Then: audioItem's class starts with "object.item.audioItem" → match
        assertThat(predicate.test(audioItem)).isTrue();
        // imageItem's class starts with "object.item.imageItem" → no match
        assertThat(predicate.test(imageItem)).isFalse();
    }

    @Test
    void testCreatorExists() {
        // Given
        var predicate = SearchCriteria.parse("dc:creator exists true");
        var withCreator = new ContentItem("1", "0", "Song", ContentItemType.AUDIO_ITEM);
        withCreator.setCreator("Artist");
        var withoutCreator = new ContentItem("2", "0", "Unknown", ContentItemType.AUDIO_ITEM);

        // Then
        assertThat(predicate.test(withCreator)).isTrue();
        assertThat(predicate.test(withoutCreator)).isFalse();
    }

    @Test
    void testNotEquals() {
        // Given
        var predicate = SearchCriteria.parse("dc:creator != \"Unknown\"");
        var match = new ContentItem("1", "0", "Song", ContentItemType.AUDIO_ITEM);
        match.setCreator("Beatles");
        var noMatch = new ContentItem("2", "0", "Other", ContentItemType.AUDIO_ITEM);
        noMatch.setCreator("Unknown");

        // Then
        assertThat(predicate.test(match)).isTrue();
        assertThat(predicate.test(noMatch)).isFalse();
    }

    @Test
    void testAndCombinator() {
        // Given: title contains "rock" AND creator = "Beatles"
        var predicate = SearchCriteria.parse("dc:title contains \"rock\" and dc:creator = \"Beatles\"");
        var match = new ContentItem("1", "0", "Rock Song", ContentItemType.AUDIO_ITEM);
        match.setCreator("Beatles");
        var titleMatch = new ContentItem("2", "0", "Rock Anthem", ContentItemType.AUDIO_ITEM);
        titleMatch.setCreator("Other");
        var creatorMatch = new ContentItem("3", "0", "Pop Song", ContentItemType.AUDIO_ITEM);
        creatorMatch.setCreator("Beatles");

        // Then
        assertThat(predicate.test(match)).isTrue();
        assertThat(predicate.test(titleMatch)).isFalse();
        assertThat(predicate.test(creatorMatch)).isFalse();
    }

    @Test
    void testOrCombinator() {
        // Given: title contains "rock" OR title contains "jazz"
        var predicate = SearchCriteria.parse("dc:title contains \"rock\" or dc:title contains \"jazz\"");
        var rock = new ContentItem("1", "0", "Rock Song", ContentItemType.AUDIO_ITEM);
        var jazz = new ContentItem("2", "0", "Jazz Standards", ContentItemType.AUDIO_ITEM);
        var pop = new ContentItem("3", "0", "Pop Hit", ContentItemType.AUDIO_ITEM);

        // Then
        assertThat(predicate.test(rock)).isTrue();
        assertThat(predicate.test(jazz)).isTrue();
        assertThat(predicate.test(pop)).isFalse();
    }

    @Test
    void testGenreSearch() {
        // Given
        var predicate = SearchCriteria.parse("upnp:genre = \"Rock\"");
        var match = new ContentItem("1", "0", "Song", ContentItemType.AUDIO_ITEM);
        match.setGenre("Rock");
        var noMatch = new ContentItem("2", "0", "Other", ContentItemType.AUDIO_ITEM);
        noMatch.setGenre("Jazz");

        // Then
        assertThat(predicate.test(match)).isTrue();
        assertThat(predicate.test(noMatch)).isFalse();
    }

    @Test
    void testGetPropertyValue() {
        // Given
        var item = new ContentItem("1", "0", "My Song", ContentItemType.AUDIO_ITEM);
        item.setCreator("Artist");
        item.setGenre("Rock");
        item.setDate("2024-01-01");
        item.setAlbumArtUri("http://example.com/art.jpg");

        // Then
        assertThat(SearchCriteria.getPropertyValue(item, "dc:title")).isEqualTo("My Song");
        assertThat(SearchCriteria.getPropertyValue(item, "dc:creator")).isEqualTo("Artist");
        assertThat(SearchCriteria.getPropertyValue(item, "upnp:class")).isEqualTo("object.item.audioItem.musicTrack");
        assertThat(SearchCriteria.getPropertyValue(item, "upnp:genre")).isEqualTo("Rock");
        assertThat(SearchCriteria.getPropertyValue(item, "dc:date")).isEqualTo("2024-01-01");
        assertThat(SearchCriteria.getPropertyValue(item, "upnp:albumArtURI")).isEqualTo("http://example.com/art.jpg");
        assertThat(SearchCriteria.getPropertyValue(item, "unknown:prop")).isNull();
    }

    @Test
    void testNullCriteriaThrows() {
        assertThatThrownBy(() -> SearchCriteria.parse(null))
                .isInstanceOf(NullPointerException.class);
    }
}
