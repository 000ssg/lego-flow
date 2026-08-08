package ssg.legoflow.upnp.mediaserver;

/**
 * Enumeration of content item types in a DLNA media library.
 *
 * <p>Maps to UPnP content directory class values used in DIDL-Lite XML.
 * Supports all standard UPnP AV ContentDirectory:1/2/3/4 class hierarchies
 * as well as vendor-specific extensions used by real-world servers (MiniDLNA,
 * Plex, Jellyfin, Windows Media Player, Kodi, Asset UPnP, Twonky, etc.).
 *
 * <p>The {@link #fromUpnpClass(String)} method never throws — it always
 * returns a best-effort match, falling back to the most general type in
 * the same category (audio/video/image/container) or {@link #GENERIC_ITEM}
 * for truly unrecognized classes.
 *
 * @since 0.1.0
 */
public enum ContentItemType {

    /**
     * A container (folder) that holds other items or containers.
     * Matches: object.container, object.container.storageFolder,
     * object.container.album.*, object.container.genre.*,
     * object.container.person.*, object.container.playlistContainer, etc.
     *
     * @since 0.1.0
     */
    CONTAINER("object.container"),

    /**
     * An audio item (music track, podcast, audiobook, audio broadcast, etc.).
     * Matches: object.item.audioItem, object.item.audioItem.musicTrack,
     * object.item.audioItem.audioBroadcast, object.item.audioItem.audioBook, etc.
     *
     * @since 0.1.0
     */
    AUDIO_ITEM("object.item.audioItem.musicTrack"),

    /**
     * A video item (movie, TV episode, music video clip, video broadcast, etc.).
     * Matches: object.item.videoItem, object.item.videoItem.movie,
     * object.item.videoItem.videoBroadcast, object.item.videoItem.musicVideoClip, etc.
     *
     * @since 0.1.0
     */
    VIDEO_ITEM("object.item.videoItem"),

    /**
     * An image item (photo, artwork, etc.).
     * Matches: object.item.imageItem, object.item.imageItem.photo, etc.
     *
     * @since 0.1.0
     */
    IMAGE_ITEM("object.item.imageItem.photo"),

    /**
     * A playlist item referencing other media items.
     * Matches: object.item.playlistItem
     *
     * @since 0.1.0
     */
    PLAYLIST_ITEM("object.item.playlistItem"),

    /**
     * A text item (document, eBook, etc.).
     * Matches: object.item.textItem, object.item.textItem.bookmarkItem, etc.
     *
     * @since 0.1.0
     */
    TEXT_ITEM("object.item.textItem"),

    /**
     * A generic item for any UPnP class that doesn't match a more specific type.
     * Used as a fallback for bare "object.item", vendor-specific classes, or
     * completely unrecognized class values.
     *
     * @since 0.1.0
     */
    GENERIC_ITEM("object.item");

    private final String upnpClass;

    ContentItemType(String upnpClass) {
        this.upnpClass = upnpClass;
    }

    /**
     * Returns the UPnP class string for this item type.
     *
     * @return the UPnP class (e.g. "object.item.audioItem.musicTrack")
     * @since 0.1.0
     */
    public String upnpClass() {
        return upnpClass;
    }

    /**
     * Determines the item type from a UPnP class string.
     *
     * <p>Supports the full UPnP AV ContentDirectory class hierarchy including:
     * <ul>
     *   <li>{@code object.container} and all sub-types (storageFolder, album.musicAlbum,
     *       album.photoAlbum, genre.musicGenre, genre.movieGenre, person.musicArtist,
     *       playlistContainer, channelGroup.*, epgContainer, storageSystem, storageVolume)</li>
     *   <li>{@code object.item.audioItem} and sub-types (musicTrack, audioBroadcast, audioBook)</li>
     *   <li>{@code object.item.videoItem} and sub-types (movie, videoBroadcast, musicVideoClip)</li>
     *   <li>{@code object.item.imageItem} and sub-types (photo)</li>
     *   <li>{@code object.item.playlistItem}</li>
     *   <li>{@code object.item.textItem} and sub-types (bookmarkItem)</li>
     *   <li>{@code object.item.epgItem} and sub-types (audioProgram, videoProgram)</li>
     *   <li>Bare {@code object.item} and vendor-specific extensions</li>
     * </ul>
     *
     * <p>This method <strong>never throws</strong>. For unrecognized classes, it returns
     * the most appropriate fallback based on the class hierarchy prefix.
     *
     * @param upnpClass the UPnP class string (may be null)
     * @return the matching content item type, never null
     * @since 0.1.0
     */
    public static ContentItemType fromUpnpClass(String upnpClass) {
        if (upnpClass == null || upnpClass.isBlank()) {
            return GENERIC_ITEM;
        }

        String trimmed = upnpClass.trim();

        // Container types — any object.container.* variant
        if (trimmed.startsWith("object.container")) {
            return CONTAINER;
        }

        // Audio types — object.item.audioItem and all sub-types
        if (trimmed.startsWith("object.item.audioItem")) {
            return AUDIO_ITEM;
        }

        // Video types — object.item.videoItem and all sub-types
        if (trimmed.startsWith("object.item.videoItem")) {
            return VIDEO_ITEM;
        }

        // Image types — object.item.imageItem and all sub-types
        if (trimmed.startsWith("object.item.imageItem")) {
            return IMAGE_ITEM;
        }

        // Playlist types
        if (trimmed.startsWith("object.item.playlistItem")) {
            return PLAYLIST_ITEM;
        }

        // Text types — object.item.textItem and all sub-types
        if (trimmed.startsWith("object.item.textItem")) {
            return TEXT_ITEM;
        }

        // EPG (Electronic Program Guide) items — map to audio/video based on sub-type
        if (trimmed.startsWith("object.item.epgItem.audioProgram")) {
            return AUDIO_ITEM;
        }
        if (trimmed.startsWith("object.item.epgItem.videoProgram")
                || trimmed.startsWith("object.item.epgItem")) {
            return VIDEO_ITEM;
        }

        // Bare object.item or any other unrecognized item sub-type
        if (trimmed.startsWith("object.item")) {
            return GENERIC_ITEM;
        }

        // Vendor-specific classes that don't follow standard hierarchy
        // Try to guess from keywords in the class name
        if (trimmed.contains("audio") || trimmed.contains("music") || trimmed.contains("song")) {
            return AUDIO_ITEM;
        }
        if (trimmed.contains("video") || trimmed.contains("movie") || trimmed.contains("film")) {
            return VIDEO_ITEM;
        }
        if (trimmed.contains("image") || trimmed.contains("photo") || trimmed.contains("picture")) {
            return IMAGE_ITEM;
        }
        if (trimmed.contains("container") || trimmed.contains("folder") || trimmed.contains("album")) {
            return CONTAINER;
        }

        // Completely unrecognized — return generic item (never throw)
        return GENERIC_ITEM;
    }
}
