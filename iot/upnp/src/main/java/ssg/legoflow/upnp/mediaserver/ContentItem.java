package ssg.legoflow.upnp.mediaserver;

import ssg.legoflow.upnp.dlna.DlnaProtocolInfo;

import java.net.URL;
import java.time.Duration;
import java.util.Objects;

/**
 * Represents a DLNA content item in a media library.
 *
 * <p>Content items correspond to the {@code <item>} elements in DIDL-Lite XML.
 * Each item has metadata (title, creator, genre, etc.), a resource URL for
 * streaming, and DLNA protocol information for format negotiation.
 *
 * @since 1.0.0
 */
public class ContentItem {

    private final String id;
    private final String parentId;
    private final String title;
    private final ContentItemType type;

    private String creator;
    private String albumArtUri;
    private String genre;
    private String date;
    private long size;
    private Duration duration;
    private String resolution;
    private DlnaProtocolInfo protocolInfo;
    private URL resourceUrl;

    /**
     * Creates a new content item with the required fields.
     *
     * @param id       the unique object ID
     * @param parentId the ID of the parent container
     * @param title    the display title
     * @param type     the content item type
     * @since 1.0.0
     */
    public ContentItem(String id, String parentId, String title, ContentItemType type) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.parentId = Objects.requireNonNull(parentId, "parentId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    /**
     * Returns the unique object ID.
     *
     * @return the object ID
     * @since 1.0.0
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the parent container ID.
     *
     * @return the parent ID
     * @since 1.0.0
     */
    public String getParentId() {
        return parentId;
    }

    /**
     * Returns the display title.
     *
     * @return the title
     * @since 1.0.0
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the content item type.
     *
     * @return the type
     * @since 1.0.0
     */
    public ContentItemType getType() {
        return type;
    }

    /**
     * Returns the creator/artist name.
     *
     * @return the creator, or null if not set
     * @since 1.0.0
     */
    public String getCreator() {
        return creator;
    }

    /**
     * Sets the creator/artist name.
     *
     * @param creator the creator name
     * @return this item for chaining
     * @since 1.0.0
     */
    public ContentItem setCreator(String creator) {
        this.creator = creator;
        return this;
    }

    /**
     * Returns the album art URI.
     *
     * @return the album art URI, or null if not set
     * @since 1.0.0
     */
    public String getAlbumArtUri() {
        return albumArtUri;
    }

    /**
     * Sets the album art URI.
     *
     * @param albumArtUri the album art URI
     * @return this item for chaining
     * @since 1.0.0
     */
    public ContentItem setAlbumArtUri(String albumArtUri) {
        this.albumArtUri = albumArtUri;
        return this;
    }

    /**
     * Returns the genre.
     *
     * @return the genre, or null if not set
     * @since 1.0.0
     */
    public String getGenre() {
        return genre;
    }

    /**
     * Sets the genre.
     *
     * @param genre the genre
     * @return this item for chaining
     * @since 1.0.0
     */
    public ContentItem setGenre(String genre) {
        this.genre = genre;
        return this;
    }

    /**
     * Returns the date string (ISO 8601 or similar).
     *
     * @return the date, or null if not set
     * @since 1.0.0
     */
    public String getDate() {
        return date;
    }

    /**
     * Sets the date string.
     *
     * @param date the date
     * @return this item for chaining
     * @since 1.0.0
     */
    public ContentItem setDate(String date) {
        this.date = date;
        return this;
    }

    /**
     * Returns the content size in bytes.
     *
     * @return the size
     * @since 1.0.0
     */
    public long getSize() {
        return size;
    }

    /**
     * Sets the content size in bytes.
     *
     * @param size the size
     * @return this item for chaining
     * @since 1.0.0
     */
    public ContentItem setSize(long size) {
        this.size = size;
        return this;
    }

    /**
     * Returns the playback duration.
     *
     * @return the duration, or null for non-temporal items
     * @since 1.0.0
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * Sets the playback duration.
     *
     * @param duration the duration
     * @return this item for chaining
     * @since 1.0.0
     */
    public ContentItem setDuration(Duration duration) {
        this.duration = duration;
        return this;
    }

    /**
     * Returns the resolution string (e.g. "1920x1080").
     *
     * @return the resolution, or null if not set
     * @since 1.0.0
     */
    public String getResolution() {
        return resolution;
    }

    /**
     * Sets the resolution string.
     *
     * @param resolution the resolution (e.g. "1920x1080")
     * @return this item for chaining
     * @since 1.0.0
     */
    public ContentItem setResolution(String resolution) {
        this.resolution = resolution;
        return this;
    }

    /**
     * Returns the DLNA protocol info.
     *
     * @return the protocol info, or null if not set
     * @since 1.0.0
     */
    public DlnaProtocolInfo getProtocolInfo() {
        return protocolInfo;
    }

    /**
     * Sets the DLNA protocol info.
     *
     * @param protocolInfo the protocol info
     * @return this item for chaining
     * @since 1.0.0
     */
    public ContentItem setProtocolInfo(DlnaProtocolInfo protocolInfo) {
        this.protocolInfo = protocolInfo;
        return this;
    }

    /**
     * Returns the resource URL for streaming.
     *
     * @return the resource URL, or null if not set
     * @since 1.0.0
     */
    public URL getResourceUrl() {
        return resourceUrl;
    }

    /**
     * Sets the resource URL for streaming.
     *
     * @param resourceUrl the resource URL
     * @return this item for chaining
     * @since 1.0.0
     */
    public ContentItem setResourceUrl(URL resourceUrl) {
        this.resourceUrl = resourceUrl;
        return this;
    }

    /**
     * Formats a {@link Duration} as UPnP time string "H:MM:SS" or "H:MM:SS.mmm".
     *
     * @param d the duration
     * @return the formatted time string
     * @since 1.0.0
     */
    public static String formatDuration(Duration d) {
        if (d == null) {
            return "0:00:00";
        }
        long totalSeconds = d.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Parses a UPnP time string "H:MM:SS" or "H:MM:SS.mmm" into a {@link Duration}.
     *
     * @param timeString the time string
     * @return the duration
     * @since 1.0.0
     */
    public static Duration parseDuration(String timeString) {
        if (timeString == null || timeString.isEmpty() || "NOT_IMPLEMENTED".equals(timeString)) {
            return Duration.ZERO;
        }
        String[] parts = timeString.split(":");
        if (parts.length != 3) {
            return Duration.ZERO;
        }
        long hours = Long.parseLong(parts[0]);
        long minutes = Long.parseLong(parts[1]);
        double secondsWithFraction = Double.parseDouble(parts[2]);
        long totalSeconds = hours * 3600 + minutes * 60 + (long) secondsWithFraction;
        long millis = (long) ((secondsWithFraction - (long) secondsWithFraction) * 1000);
        return Duration.ofSeconds(totalSeconds).plusMillis(millis);
    }

    @Override
    public String toString() {
        return "ContentItem{id='" + id + "', title='" + title + "', type=" + type + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentItem that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
