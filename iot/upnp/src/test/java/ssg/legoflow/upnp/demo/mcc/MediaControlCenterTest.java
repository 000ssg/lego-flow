package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.device.DeviceDescription;
import ssg.legoflow.upnp.dlna.DlnaMediaFormat;
import ssg.legoflow.upnp.mediarenderer.MediaRendererDevice;
import ssg.legoflow.upnp.mediarenderer.TransportState;
import ssg.legoflow.upnp.mediaserver.ContentContainer;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.ContentItemType;
import ssg.legoflow.upnp.mediaserver.MediaServerDevice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the Media Control Center application model and logic (non-UI).
 *
 * <p>Verifies device discovery, content browsing, playback control, volume
 * management, seek positioning, and device property retrieval using in-process
 * demo devices.
 *
 * @since 1.0.0
 */
class MediaControlCenterTest {

    private ControlPoint controlPoint;
    private MediaServerDevice serverDevice;
    private MediaRendererDevice rendererDevice;

    @BeforeEach
    void setUp() {
        controlPoint = new ControlPoint();
        controlPoint.start();

        serverDevice = new MediaServerDevice("Test Media Server");
        serverDevice.setHttpPort(8200).setHostAddress("127.0.0.1");
        buildTestLibrary(serverDevice);
        serverDevice.start();

        rendererDevice = new MediaRendererDevice("Test Media Renderer");
        rendererDevice.setHttpPort(8300).setHostAddress("127.0.0.1");
        rendererDevice.start();

        controlPoint.registerLocalServer(serverDevice);
        controlPoint.registerLocalRenderer(rendererDevice);
    }

    @AfterEach
    void tearDown() {
        controlPoint.stop();
        serverDevice.stop();
        rendererDevice.stop();
    }

    @Test
    void testDeviceDiscovery() {
        // When: discover devices via control point
        List<MediaServerProxy> servers = controlPoint.discoverMediaServers();
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();

        // Then: both demo server and renderer are discovered
        assertThat(servers).hasSize(1);
        assertThat(servers.getFirst().getFriendlyName()).isEqualTo("Test Media Server");

        assertThat(renderers).hasSize(1);
        assertThat(renderers.getFirst().getFriendlyName()).isEqualTo("Test Media Renderer");

        assertThat(controlPoint.getDevices()).hasSize(2);
    }

    @Test
    void testContentBrowsing() {
        // Given: a media server with content
        MediaServerProxy server = controlPoint.discoverMediaServers().getFirst();

        // When: browse root container
        List<ContentItem> rootItems = server.browseRoot();

        // Then: root has Music and Video containers
        assertThat(rootItems).isNotEmpty();
        assertThat(rootItems).anyMatch(item -> item.getTitle().equals("Music"));
        assertThat(rootItems).anyMatch(item -> item.getTitle().equals("Video"));

        // When: browse into Music container
        ContentItem musicContainer = rootItems.stream()
                .filter(i -> i.getTitle().equals("Music"))
                .findFirst().orElseThrow();
        List<ContentItem> musicItems = server.browse(musicContainer.getId());

        // Then: Music contains the album container
        assertThat(musicItems).isNotEmpty();
        assertThat(musicItems).anyMatch(item -> item.getTitle().equals("Test Album"));

        // When: browse into album
        ContentItem albumContainer = musicItems.stream()
                .filter(i -> i.getTitle().equals("Test Album"))
                .findFirst().orElseThrow();
        List<ContentItem> tracks = server.browse(albumContainer.getId());

        // Then: album contains audio tracks
        assertThat(tracks).hasSize(2);
        assertThat(tracks).allMatch(item -> item.getType() == ContentItemType.AUDIO_ITEM);
    }

    @Test
    void testPlaybackControl() {
        // Given: server and renderer proxies
        MediaServerProxy server = controlPoint.discoverMediaServers().getFirst();
        MediaRendererProxy renderer = controlPoint.discoverMediaRenderers().getFirst();

        // Get a playable track
        List<ContentItem> tracks = server.browse("10");
        ContentItem track = tracks.getFirst();

        // When: play the track
        renderer.playItem(track);

        // Then: state is PLAYING
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PLAYING);

        // When: pause
        renderer.pause();

        // Then: state is PAUSED_PLAYBACK
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PAUSED_PLAYBACK);

        // When: resume
        renderer.play();

        // Then: state is PLAYING again
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PLAYING);

        // When: stop
        renderer.stop();

        // Then: state is STOPPED
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.STOPPED);
    }

    @Test
    void testVolumeControl() {
        // Given: a renderer proxy
        MediaRendererProxy renderer = controlPoint.discoverMediaRenderers().getFirst();

        // When: set volume
        renderer.setVolume(75);

        // Then: volume is 75
        assertThat(renderer.getVolume()).isEqualTo(75);

        // When: mute
        renderer.setMute(true);

        // Then: muted
        assertThat(renderer.getMute()).isTrue();

        // When: unmute
        renderer.setMute(false);

        // Then: not muted
        assertThat(renderer.getMute()).isFalse();

        // And volume is preserved
        assertThat(renderer.getVolume()).isEqualTo(75);
    }

    @Test
    void testSeekPosition() {
        // Given: a renderer playing a track
        MediaServerProxy server = controlPoint.discoverMediaServers().getFirst();
        MediaRendererProxy renderer = controlPoint.discoverMediaRenderers().getFirst();
        List<ContentItem> tracks = server.browse("10");
        renderer.playItem(tracks.getFirst());

        // When: seek to 1:30
        Duration seekTarget = Duration.ofMinutes(1).plusSeconds(30);
        renderer.seek(seekTarget);

        // Then: position info reflects the seek
        var posInfo = renderer.getPosition();
        assertThat(posInfo).isNotNull();
        assertThat(posInfo.relTime()).isEqualTo(seekTarget);
    }

    @Test
    void testDeviceProperties() {
        // Given: a server proxy with description XML
        MediaServerProxy server = controlPoint.discoverMediaServers().getFirst();

        // When: parse the device description
        String xml = server.getDescriptionXml();
        DeviceDescription desc = DeviceDescription.parseXml(xml);

        // Then: verify device description fields
        assertThat(desc.friendlyName()).isEqualTo("Test Media Server");
        assertThat(desc.manufacturer()).isEqualTo("Lego Flow");
        assertThat(desc.modelName()).isEqualTo("Lego Flow Media Server");
        assertThat(desc.modelNumber()).isEqualTo("1.0");
        assertThat(desc.udn()).isEqualTo(serverDevice.getUdn());
        assertThat(desc.deviceType()).isEqualTo(MediaServerDevice.DEVICE_TYPE);
        assertThat(desc.services()).isNotEmpty();
    }

    private void buildTestLibrary(MediaServerDevice server) {
        String baseUrl = server.getBaseUrl();

        var music = new ContentContainer("1", "0", "Music", true);
        server.addContainer(music);

        var album = new ContentContainer("10", "1", "Test Album", true);
        server.addContainer(album);

        var track1 = new ContentItem("100", "10", "Track One", ContentItemType.AUDIO_ITEM);
        track1.setCreator("Test Artist")
                .setGenre("Electronic")
                .setDuration(Duration.ofMinutes(3).plusSeconds(30))
                .setSize(4_800_000L)
                .setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/track1.mp3"));
        server.addContent(track1);

        var track2 = new ContentItem("101", "10", "Track Two", ContentItemType.AUDIO_ITEM);
        track2.setCreator("Test Artist")
                .setGenre("Rock")
                .setDuration(Duration.ofMinutes(4).plusSeconds(15))
                .setSize(5_600_000L)
                .setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/track2.mp3"));
        server.addContent(track2);

        var video = new ContentContainer("2", "0", "Video", true);
        server.addContainer(video);

        var movie = new ContentItem("200", "2", "Test Movie", ContentItemType.VIDEO_ITEM);
        movie.setCreator("Test Director")
                .setDuration(Duration.ofHours(1).plusMinutes(45))
                .setSize(2_000_000_000L)
                .setResolution("1920x1080")
                .setProtocolInfo(DlnaMediaFormat.AVC_MP4_MP_SD.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/movie.mp4"));
        server.addContent(movie);
    }

    private static URL createUrl(String urlString) {
        try {
            return URI.create(urlString).toURL();
        } catch (Exception e) {
            throw new RuntimeException("Invalid URL: " + urlString, e);
        }
    }
}
