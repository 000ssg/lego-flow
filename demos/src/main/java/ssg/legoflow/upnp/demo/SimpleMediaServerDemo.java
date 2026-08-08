package ssg.legoflow.upnp.demo;

import ssg.legoflow.upnp.dlna.DlnaMediaFormat;
import ssg.legoflow.upnp.mediaserver.ContentContainer;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.ContentItemType;
import ssg.legoflow.upnp.mediaserver.MediaServerDevice;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

/**
 * Demo application: UPnP Media Server hosting a sample content library.
 *
 * <p>Creates a media server with a hierarchical content tree:
 * <pre>
 * Root
 *   +-- Music
 *   |     +-- Album1
 *   |           +-- Track1.mp3
 *   |           +-- Track2.mp3
 *   +-- Video
 *   |     +-- Movie1.mp4
 *   +-- Photos
 *         +-- Photo1.jpg
 * </pre>
 *
 * <p>The server advertises itself via SSDP and serves content metadata
 * via the ContentDirectory service.
 *
 * @since 0.1.0
 */
public class SimpleMediaServerDemo {

    private final MediaServerDevice server;

    /**
     * Creates and initializes the demo media server.
     *
     * @since 0.1.0
     */
    public SimpleMediaServerDemo() {
        server = new MediaServerDevice("Lego Flow Demo Media Server");
        server.setHttpPort(8200);
        server.setHostAddress("127.0.0.1");
        buildContentLibrary();
    }

    /**
     * Returns the media server device.
     *
     * @return the server device
     * @since 0.1.0
     */
    public MediaServerDevice getServer() {
        return server;
    }

    /**
     * Starts the demo media server.
     *
     * @since 0.1.0
     */
    public void start() {
        server.start();
    }

    /**
     * Stops the demo media server.
     *
     * @since 0.1.0
     */
    public void stop() {
        server.stop();
    }

    private void buildContentLibrary() {
        String baseUrl = server.getBaseUrl();

        // Root container (id=0) is auto-created
        // Music container
        var music = new ContentContainer("1", "0", "Music", true);
        server.addContainer(music);

        // Album1 container
        var album1 = new ContentContainer("10", "1", "Album1", true);
        server.addContainer(album1);

        // Track1
        var track1 = new ContentItem("100", "10", "Track1.mp3", ContentItemType.AUDIO_ITEM);
        track1.setCreator("Demo Artist")
                .setGenre("Rock")
                .setDate("2024-01-15")
                .setDuration(Duration.ofMinutes(3).plusSeconds(45))
                .setSize(5_200_000L)
                .setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/track1.mp3"));
        server.addContent(track1);

        // Track2
        var track2 = new ContentItem("101", "10", "Track2.mp3", ContentItemType.AUDIO_ITEM);
        track2.setCreator("Demo Artist")
                .setGenre("Rock")
                .setDate("2024-01-15")
                .setDuration(Duration.ofMinutes(4).plusSeconds(20))
                .setSize(6_100_000L)
                .setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/track2.mp3"));
        server.addContent(track2);

        // Video container
        var video = new ContentContainer("2", "0", "Video", true);
        server.addContainer(video);

        // Movie1
        var movie1 = new ContentItem("200", "2", "Movie1.mp4", ContentItemType.VIDEO_ITEM);
        movie1.setCreator("Demo Director")
                .setGenre("Action")
                .setDate("2024-06-01")
                .setDuration(Duration.ofHours(1).plusMinutes(42))
                .setSize(1_500_000_000L)
                .setResolution("1920x1080")
                .setProtocolInfo(DlnaMediaFormat.AVC_MP4_MP_SD.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/movie1.mp4"));
        server.addContent(movie1);

        // Photos container
        var photos = new ContentContainer("3", "0", "Photos", true);
        server.addContainer(photos);

        // Photo1
        var photo1 = new ContentItem("300", "3", "Photo1.jpg", ContentItemType.IMAGE_ITEM);
        photo1.setCreator("Demo Photographer")
                .setDate("2024-03-20")
                .setSize(2_500_000L)
                .setResolution("4000x3000")
                .setProtocolInfo(DlnaMediaFormat.JPEG_LRG.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/photo1.jpg"));
        server.addContent(photo1);
    }

    private static URL createUrl(String urlString) {
        try {
            return URI.create(urlString).toURL();
        } catch (Exception e) {
            throw new RuntimeException("Invalid URL: " + urlString, e);
        }
    }

    /**
     * Main entry point for running the demo standalone.
     *
     * @param args command-line arguments (unused)
     * @since 0.1.0
     */
    public static void main(String[] args) {
        var demo = new SimpleMediaServerDemo();
        demo.start();
        System.out.println("Media Server started: " + demo.getServer().getFriendlyName());
        System.out.println("Description URL: " + demo.getServer().getDescriptionUrl());
    }
}
