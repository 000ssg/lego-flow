package ssg.legoflow.upnp.mediaserver;

import ssg.legoflow.upnp.dlna.DlnaMediaFormat;
import ssg.legoflow.upnp.dlna.DlnaProtocolInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DidlLiteParser}.
 *
 * @since 1.0.0
 */
class DidlLiteParserTest {

    private DidlLiteParser parser;

    @BeforeEach
    void setUp() {
        parser = new DidlLiteParser();
    }

    @Test
    void testParseAudioItem() {
        // Given
        String xml = """
                <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                           xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                  <item id="100" parentID="10">
                    <dc:title>My Song</dc:title>
                    <dc:creator>Artist Name</dc:creator>
                    <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                    <res protocolInfo="http-get:*:audio/mpeg:DLNA.ORG_PN=MP3" size="5000000" duration="0:03:45">http://example.com/song.mp3</res>
                  </item>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then
        assertThat(items).hasSize(1);
        ContentItem item = items.getFirst();
        assertThat(item.getId()).isEqualTo("100");
        assertThat(item.getTitle()).isEqualTo("My Song");
        assertThat(item.getCreator()).isEqualTo("Artist Name");
        assertThat(item.getType()).isEqualTo(ContentItemType.AUDIO_ITEM);
        assertThat(item.getSize()).isEqualTo(5_000_000L);
    }

    @Test
    void testParseVideoItem() {
        // Given
        String xml = """
                <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                           xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                  <item id="200" parentID="2">
                    <dc:title>My Movie</dc:title>
                    <upnp:class>object.item.videoItem</upnp:class>
                    <res protocolInfo="http-get:*:video/mp4:DLNA.ORG_PN=AVC_MP4_MP_SD" resolution="1920x1080">http://example.com/movie.mp4</res>
                  </item>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then
        assertThat(items).hasSize(1);
        ContentItem item = items.getFirst();
        assertThat(item.getType()).isEqualTo(ContentItemType.VIDEO_ITEM);
        assertThat(item.getResolution()).isEqualTo("1920x1080");
    }

    @Test
    void testParseContainer() {
        // Given
        String xml = """
                <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                           xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                  <container id="1" parentID="0" childCount="5" searchable="1">
                    <dc:title>Music</dc:title>
                    <upnp:class>object.container</upnp:class>
                  </container>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then
        assertThat(items).hasSize(1);
        ContentItem item = items.getFirst();
        assertThat(item.getType()).isEqualTo(ContentItemType.CONTAINER);
        assertThat(item.getTitle()).isEqualTo("Music");
        assertThat(item.getId()).isEqualTo("1");
    }

    @Test
    void testSerializeRoundtrip() {
        // Given
        var item = new ContentItem("42", "0", "Test Track", ContentItemType.AUDIO_ITEM);
        item.setCreator("Test Artist")
                .setGenre("Pop")
                .setDuration(Duration.ofSeconds(200))
                .setSize(3_000_000L)
                .setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo());

        try {
            item.setResourceUrl(URI.create("http://example.com/test.mp3").toURL());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // When
        String xml = parser.serialize(List.of(item));
        List<ContentItem> parsed = parser.parse(xml);

        // Then
        assertThat(parsed).hasSize(1);
        ContentItem result = parsed.getFirst();
        assertThat(result.getId()).isEqualTo("42");
        assertThat(result.getTitle()).isEqualTo("Test Track");
        assertThat(result.getCreator()).isEqualTo("Test Artist");
        assertThat(result.getGenre()).isEqualTo("Pop");
        assertThat(result.getType()).isEqualTo(ContentItemType.AUDIO_ITEM);
    }

    @Test
    void testNamespaces() {
        // Given/When
        var items = List.of(new ContentItem("1", "0", "Test", ContentItemType.AUDIO_ITEM));
        String xml = parser.serialize(items);

        // Then
        assertThat(xml).contains("xmlns=\"" + DidlLiteParser.DIDL_LITE_NS + "\"");
        assertThat(xml).contains("xmlns:dc=\"" + DidlLiteParser.DC_NS + "\"");
        assertThat(xml).contains("xmlns:upnp=\"" + DidlLiteParser.UPNP_NS + "\"");
        assertThat(xml).contains("<dc:title>");
        assertThat(xml).contains("<upnp:class>");
    }

    @Test
    void testAlbumArt() {
        // Given
        String xml = """
                <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                           xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                  <item id="100" parentID="10">
                    <dc:title>Song</dc:title>
                    <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                    <upnp:albumArtURI>http://example.com/art.jpg</upnp:albumArtURI>
                  </item>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then
        assertThat(items.getFirst().getAlbumArtUri()).isEqualTo("http://example.com/art.jpg");
    }

    @Test
    void testDuration() {
        // Given
        Duration duration = Duration.ofHours(1).plusMinutes(23).plusSeconds(45);

        // When
        String formatted = ContentItem.formatDuration(duration);
        Duration parsed = ContentItem.parseDuration(formatted);

        // Then
        assertThat(formatted).isEqualTo("1:23:45");
        assertThat(parsed).isEqualTo(duration);
    }

    @Test
    void testProtocolInfo() {
        // Given
        String xml = """
                <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                           xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                  <item id="100" parentID="10">
                    <dc:title>Song</dc:title>
                    <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                    <res protocolInfo="http-get:*:audio/mpeg:DLNA.ORG_PN=MP3">http://example.com/song.mp3</res>
                  </item>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then
        DlnaProtocolInfo info = items.getFirst().getProtocolInfo();
        assertThat(info).isNotNull();
        assertThat(info.protocol()).isEqualTo("http-get");
        assertThat(info.contentFormat()).isEqualTo("audio/mpeg");
        assertThat(info.additionalInfo()).isEqualTo("DLNA.ORG_PN=MP3");
    }

    // ── Real-world server compatibility tests ────────────────────────────

    @Test
    void testMiniDlnaStorageFolderContainer() {
        // Given: MiniDLNA returns object.container.storageFolder, not bare object.container
        String xml = """
                <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                           xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                  <container id="1" parentID="0" restricted="1" childCount="3">
                    <dc:title>Music</dc:title>
                    <upnp:class>object.container.storageFolder</upnp:class>
                  </container>
                  <container id="2" parentID="0" restricted="1" childCount="5">
                    <dc:title>Videos</dc:title>
                    <upnp:class>object.container.storageFolder</upnp:class>
                  </container>
                  <container id="3" parentID="0" restricted="1" childCount="12">
                    <dc:title>Photos</dc:title>
                    <upnp:class>object.container.storageFolder</upnp:class>
                  </container>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then: all storageFolder containers are parsed as CONTAINER type
        assertThat(items).hasSize(3);
        assertThat(items).allSatisfy(item ->
                assertThat(item.getType()).isEqualTo(ContentItemType.CONTAINER));
        assertThat(items.get(0).getTitle()).isEqualTo("Music");
        assertThat(items.get(1).getTitle()).isEqualTo("Videos");
        assertThat(items.get(2).getTitle()).isEqualTo("Photos");
    }

    @Test
    void testPlexMusicAlbumAndVideoMovie() {
        // Given: Plex returns album and movie containers/items with extended class names
        String xml = """
                <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                           xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                  <container id="10" parentID="1" restricted="1" childCount="12">
                    <dc:title>Abbey Road</dc:title>
                    <upnp:class>object.container.album.musicAlbum</upnp:class>
                    <dc:creator>The Beatles</dc:creator>
                    <upnp:albumArtURI>http://192.168.1.50:32469/art.jpg</upnp:albumArtURI>
                  </container>
                  <item id="100" parentID="10" restricted="1">
                    <dc:title>Come Together</dc:title>
                    <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                    <dc:creator>The Beatles</dc:creator>
                    <res protocolInfo="http-get:*:audio/mpeg:DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000" size="8234567" duration="0:04:20.000">http://192.168.1.50:32469/track100.mp3</res>
                  </item>
                  <item id="200" parentID="2" restricted="1">
                    <dc:title>Inception</dc:title>
                    <upnp:class>object.item.videoItem.movie</upnp:class>
                    <dc:creator>Christopher Nolan</dc:creator>
                    <res protocolInfo="http-get:*:video/mp4:DLNA.ORG_PN=AVC_MP4_MP_HD_720p" size="1500000000" duration="2:28:00" resolution="1920x1080">http://192.168.1.50:32469/video200.mp4</res>
                  </item>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then
        assertThat(items).hasSize(3);
        // musicAlbum container
        assertThat(items.get(0).getType()).isEqualTo(ContentItemType.CONTAINER);
        assertThat(items.get(0).getTitle()).isEqualTo("Abbey Road");
        assertThat(items.get(0).getAlbumArtUri()).isEqualTo("http://192.168.1.50:32469/art.jpg");
        // musicTrack item
        assertThat(items.get(1).getType()).isEqualTo(ContentItemType.AUDIO_ITEM);
        assertThat(items.get(1).getCreator()).isEqualTo("The Beatles");
        assertThat(items.get(1).getSize()).isEqualTo(8234567L);
        assertThat(items.get(1).getResourceUrl()).isNotNull();
        // videoItem.movie
        assertThat(items.get(2).getType()).isEqualTo(ContentItemType.VIDEO_ITEM);
        assertThat(items.get(2).getResolution()).isEqualTo("1920x1080");
    }

    @Test
    void testMultipleResElements() {
        // Given: item with multiple <res> elements (different formats)
        // Common on Jellyfin, Plex, and transcoding servers
        String xml = """
                <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                           xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                  <item id="42" parentID="1" restricted="1">
                    <dc:title>Track</dc:title>
                    <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                    <res protocolInfo="http-get:*:audio/flac:*" size="30000000" duration="0:04:30">http://server/track.flac</res>
                    <res protocolInfo="http-get:*:audio/mpeg:DLNA.ORG_PN=MP3" size="5000000" duration="0:04:30">http://server/track.mp3</res>
                    <res protocolInfo="http-get:*:audio/L16:*" size="60000000" duration="0:04:30">http://server/track.wav</res>
                  </item>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then: first <res> is used
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getResourceUrl().toString()).contains("track.flac");
        assertThat(items.getFirst().getProtocolInfo().contentFormat()).isEqualTo("audio/flac");
    }

    @Test
    void testGenreContainersAndBareObjectItem() {
        // Given: genre containers and bare object.item class
        String xml = """
                <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                           xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                  <container id="g1" parentID="0" childCount="42">
                    <dc:title>Rock</dc:title>
                    <upnp:class>object.container.genre.musicGenre</upnp:class>
                  </container>
                  <container id="g2" parentID="0" childCount="15">
                    <dc:title>Action</dc:title>
                    <upnp:class>object.container.genre.movieGenre</upnp:class>
                  </container>
                  <item id="x1" parentID="0">
                    <dc:title>Unknown File</dc:title>
                    <upnp:class>object.item</upnp:class>
                  </item>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then
        assertThat(items).hasSize(3);
        assertThat(items.get(0).getType()).isEqualTo(ContentItemType.CONTAINER);
        assertThat(items.get(1).getType()).isEqualTo(ContentItemType.CONTAINER);
        assertThat(items.get(2).getType()).isEqualTo(ContentItemType.GENERIC_ITEM);
    }

    @Test
    void testEmptyAndBlankXmlReturnsEmptyList() {
        // Given/When/Then
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
    }

    @Test
    void testMalformedXmlReturnsEmptyList() {
        // Given: completely invalid XML
        assertThat(parser.parse("not xml at all")).isEmpty();
        assertThat(parser.parse("<broken>xml")).isEmpty();
    }

    @Test
    void testWindowsMediaPlayerResponse() {
        // Given: WMP-style response with playlistContainer and audioBroadcast
        String xml = """
                <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                           xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                  <container id="pl1" parentID="0" childCount="3">
                    <dc:title>My Playlist</dc:title>
                    <upnp:class>object.container.playlistContainer</upnp:class>
                  </container>
                  <item id="b1" parentID="0">
                    <dc:title>BBC Radio</dc:title>
                    <upnp:class>object.item.audioItem.audioBroadcast</upnp:class>
                    <res protocolInfo="http-get:*:audio/mpeg:*">http://stream.bbc.co.uk/radio</res>
                  </item>
                  <item id="t1" parentID="0">
                    <dc:title>readme.txt</dc:title>
                    <upnp:class>object.item.textItem</upnp:class>
                  </item>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then
        assertThat(items).hasSize(3);
        assertThat(items.get(0).getType()).isEqualTo(ContentItemType.CONTAINER);
        assertThat(items.get(1).getType()).isEqualTo(ContentItemType.AUDIO_ITEM); // audioBroadcast → AUDIO_ITEM
        assertThat(items.get(2).getType()).isEqualTo(ContentItemType.TEXT_ITEM);
    }

    @Test
    void testHtmlVoidElementsInDidlLite() {
        // Given: DIDL-Lite with unclosed HTML void elements (common from NAS devices
        // like Synology, QNAP that embed HTML fragments in metadata descriptions)
        String xml = """
                <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                           xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                  <container id="1" parentID="0" childCount="5">
                    <dc:title>Photos</dc:title>
                    <upnp:class>object.container.storageFolder</upnp:class>
                    <upnp:albumArtURI>http://nas:8200/thumb.jpg</upnp:albumArtURI>
                    <desc>Album with <img src="http://nas:8200/icon.png"> thumbnail and <br> line break</desc>
                  </container>
                  <item id="2" parentID="1">
                    <dc:title>Vacation Photo</dc:title>
                    <upnp:class>object.item.imageItem.photo</upnp:class>
                    <res protocolInfo="http-get:*:image/jpeg:*">http://nas:8200/photo.jpg</res>
                  </item>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then: parser should handle unclosed HTML tags gracefully
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getType()).isEqualTo(ContentItemType.CONTAINER);
        assertThat(items.get(0).getTitle()).isEqualTo("Photos");
        assertThat(items.get(1).getType()).isEqualTo(ContentItemType.IMAGE_ITEM);
        assertThat(items.get(1).getTitle()).isEqualTo("Vacation Photo");
    }

    @Test
    void testAlreadySelfClosedHtmlElements() {
        // Given: DIDL-Lite where HTML elements are already properly self-closed
        String xml = """
                <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                           xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                  <container id="1" parentID="0">
                    <dc:title>Music</dc:title>
                    <upnp:class>object.container</upnp:class>
                    <desc>Info <img src="icon.png" /> and <br /> end</desc>
                  </container>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getTitle()).isEqualTo("Music");
    }

    @Test
    void testMultipleHtmlVoidElementTypes() {
        // Given: DIDL-Lite with multiple different HTML void element types
        String xml = """
                <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                           xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                  <item id="1" parentID="0">
                    <dc:title>Track</dc:title>
                    <upnp:class>object.item.audioItem</upnp:class>
                    <desc><img src="a.png"><br><hr><input type="hidden" value="x"><meta name="test" content="v"></desc>
                    <res protocolInfo="http-get:*:audio/mpeg:*">http://nas/track.mp3</res>
                  </item>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getTitle()).isEqualTo("Track");
    }

    @Test
    void testSpecialCharactersInTitle() {
        // Given: titles with XML special characters (common in real libraries)
        String xml = """
                <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                           xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                  <item id="1" parentID="0">
                    <dc:title>Rock &amp; Roll</dc:title>
                    <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                  </item>
                  <item id="2" parentID="0">
                    <dc:title>Tom &amp; Jerry &quot;Episode 1&quot;</dc:title>
                    <upnp:class>object.item.videoItem</upnp:class>
                  </item>
                </DIDL-Lite>
                """;

        // When
        List<ContentItem> items = parser.parse(xml);

        // Then: entities are properly unescaped
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getTitle()).isEqualTo("Rock & Roll");
        assertThat(items.get(1).getTitle()).isEqualTo("Tom & Jerry \"Episode 1\"");
    }
}
