package ssg.legoflow.upnp.mediaserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ContentItemType} UPnP class mapping with real-world server compatibility.
 *
 * @since 0.1.0
 */
class ContentItemTypeTest {

    // ── Container types ──────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "object.container",
            "object.container.storageFolder",
            "object.container.album",
            "object.container.album.musicAlbum",
            "object.container.album.photoAlbum",
            "object.container.genre",
            "object.container.genre.musicGenre",
            "object.container.genre.movieGenre",
            "object.container.person",
            "object.container.person.musicArtist",
            "object.container.playlistContainer",
            "object.container.channelGroup",
            "object.container.channelGroup.audioChannelGroup",
            "object.container.channelGroup.videoChannelGroup",
            "object.container.epgContainer",
            "object.container.storageSystem",
            "object.container.storageVolume"
    })
    void shouldMapContainerTypes(String upnpClass) {
        assertThat(ContentItemType.fromUpnpClass(upnpClass)).isEqualTo(ContentItemType.CONTAINER);
    }

    // ── Audio types ──────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "object.item.audioItem",
            "object.item.audioItem.musicTrack",
            "object.item.audioItem.audioBroadcast",
            "object.item.audioItem.audioBook"
    })
    void shouldMapAudioTypes(String upnpClass) {
        assertThat(ContentItemType.fromUpnpClass(upnpClass)).isEqualTo(ContentItemType.AUDIO_ITEM);
    }

    // ── Video types ──────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "object.item.videoItem",
            "object.item.videoItem.movie",
            "object.item.videoItem.videoBroadcast",
            "object.item.videoItem.musicVideoClip"
    })
    void shouldMapVideoTypes(String upnpClass) {
        assertThat(ContentItemType.fromUpnpClass(upnpClass)).isEqualTo(ContentItemType.VIDEO_ITEM);
    }

    // ── Image types ──────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "object.item.imageItem",
            "object.item.imageItem.photo"
    })
    void shouldMapImageTypes(String upnpClass) {
        assertThat(ContentItemType.fromUpnpClass(upnpClass)).isEqualTo(ContentItemType.IMAGE_ITEM);
    }

    // ── Text types ───────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "object.item.textItem",
            "object.item.textItem.bookmarkItem"
    })
    void shouldMapTextTypes(String upnpClass) {
        assertThat(ContentItemType.fromUpnpClass(upnpClass)).isEqualTo(ContentItemType.TEXT_ITEM);
    }

    // ── Playlist types ───────────────────────────────────────────────────

    @Test
    void shouldMapPlaylistType() {
        assertThat(ContentItemType.fromUpnpClass("object.item.playlistItem"))
                .isEqualTo(ContentItemType.PLAYLIST_ITEM);
    }

    // ── EPG types ────────────────────────────────────────────────────────

    @Test
    void shouldMapEpgAudioProgram() {
        assertThat(ContentItemType.fromUpnpClass("object.item.epgItem.audioProgram"))
                .isEqualTo(ContentItemType.AUDIO_ITEM);
    }

    @Test
    void shouldMapEpgVideoProgram() {
        assertThat(ContentItemType.fromUpnpClass("object.item.epgItem.videoProgram"))
                .isEqualTo(ContentItemType.VIDEO_ITEM);
    }

    @Test
    void shouldMapBareEpgToVideo() {
        assertThat(ContentItemType.fromUpnpClass("object.item.epgItem"))
                .isEqualTo(ContentItemType.VIDEO_ITEM);
    }

    // ── Fallback behavior ────────────────────────────────────────────────

    @Test
    void shouldReturnGenericItemForBareObjectItem() {
        assertThat(ContentItemType.fromUpnpClass("object.item"))
                .isEqualTo(ContentItemType.GENERIC_ITEM);
    }

    @Test
    void shouldNeverThrowForNull() {
        assertThat(ContentItemType.fromUpnpClass(null))
                .isEqualTo(ContentItemType.GENERIC_ITEM);
    }

    @Test
    void shouldNeverThrowForBlank() {
        assertThat(ContentItemType.fromUpnpClass(""))
                .isEqualTo(ContentItemType.GENERIC_ITEM);
        assertThat(ContentItemType.fromUpnpClass("   "))
                .isEqualTo(ContentItemType.GENERIC_ITEM);
    }

    @Test
    void shouldNeverThrowForUnknownClass() {
        assertThat(ContentItemType.fromUpnpClass("vendor.custom.type"))
                .isEqualTo(ContentItemType.GENERIC_ITEM);
        assertThat(ContentItemType.fromUpnpClass("completely.bogus"))
                .isEqualTo(ContentItemType.GENERIC_ITEM);
    }

    // ── upnpClass() round-trip ───────────────────────────────────────────

    @Test
    void shouldRoundtripStandardClasses() {
        for (ContentItemType type : ContentItemType.values()) {
            assertThat(ContentItemType.fromUpnpClass(type.upnpClass())).isEqualTo(type);
        }
    }
}
