package ssg.legoflow.upnp.controlpoint;

import ssg.legoflow.upnp.dlna.DlnaMediaFormat;
import ssg.legoflow.upnp.mediarenderer.MediaRendererDevice;
import ssg.legoflow.upnp.mediarenderer.PlaybackEvent;
import ssg.legoflow.upnp.mediarenderer.TransportState;
import ssg.legoflow.upnp.mediaserver.ContentContainer;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.ContentItemType;
import ssg.legoflow.upnp.mediaserver.MediaServerDevice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ControlPoint}.
 *
 * @since 1.0.0
 */
class ControlPointTest {

    private ControlPoint controlPoint;
    private MediaServerDevice serverDevice;
    private MediaRendererDevice rendererDevice;

    @BeforeEach
    void setUp() {
        controlPoint = new ControlPoint();
        controlPoint.start();

        serverDevice = new MediaServerDevice("Test Server");
        serverDevice.setHttpPort(8200);
        buildServerLibrary();
        serverDevice.start();

        rendererDevice = new MediaRendererDevice("Test Renderer");
        rendererDevice.setHttpPort(8300);
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
    void testDiscover() {
        // When
        List<MediaServerProxy> servers = controlPoint.discoverMediaServers();
        List<MediaRendererProxy> renderers = controlPoint.discoverMediaRenderers();

        // Then
        assertThat(servers).hasSize(1);
        assertThat(renderers).hasSize(1);
        assertThat(servers.getFirst().getFriendlyName()).isEqualTo("Test Server");
        assertThat(renderers.getFirst().getFriendlyName()).isEqualTo("Test Renderer");
    }

    @Test
    void testFetchDescription() {
        // When
        List<DeviceProxy> devices = controlPoint.getDevices();

        // Then
        assertThat(devices).hasSize(2);
        for (DeviceProxy device : devices) {
            assertThat(device.getUdn()).isNotEmpty();
            assertThat(device.getDescriptionXml()).isNotEmpty();
            assertThat(device.getDescriptionXml()).contains("deviceType");
        }
    }

    @Test
    void testBrowseServer() {
        // Given
        MediaServerProxy server = controlPoint.discoverMediaServers().getFirst();

        // When
        List<ContentItem> rootItems = server.browseRoot();

        // Then
        assertThat(rootItems).isNotEmpty();
        assertThat(rootItems).extracting(ContentItem::getTitle).contains("Music");
    }

    @Test
    void testPlayOnRenderer() {
        // Given
        MediaServerProxy server = controlPoint.discoverMediaServers().getFirst();
        MediaRendererProxy renderer = controlPoint.discoverMediaRenderers().getFirst();
        ContentItem track = server.getContent("100");

        // When
        renderer.playItem(track);

        // Then
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PLAYING);
    }

    @Test
    void testControlPlayback() {
        // Given
        MediaRendererProxy renderer = controlPoint.discoverMediaRenderers().getFirst();
        ContentItem track = controlPoint.discoverMediaServers().getFirst().getContent("100");
        renderer.playItem(track);

        // When: pause
        renderer.pause();
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PAUSED_PLAYBACK);

        // When: resume
        renderer.play();
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PLAYING);

        // When: stop
        renderer.stop();
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.STOPPED);
    }

    @Test
    void testEventSubscription() {
        // Given
        MediaRendererProxy renderer = controlPoint.discoverMediaRenderers().getFirst();
        List<PlaybackEvent> events = new ArrayList<>();
        renderer.subscribeTransportEvents(events::add);

        // When
        ContentItem track = controlPoint.discoverMediaServers().getFirst().getContent("100");
        renderer.playItem(track);
        renderer.pause();
        renderer.stop();

        // Then
        assertThat(events).hasSizeGreaterThanOrEqualTo(3);
        assertThat(events.getFirst()).isInstanceOf(PlaybackEvent.PlayStarted.class);
    }

    @Test
    void testRefresh() {
        // Given
        assertThat(controlPoint.getDevices()).hasSize(2);

        // When
        controlPoint.refresh();

        // Then - devices are still cached
        assertThat(controlPoint.getDevices()).hasSize(2);
    }

    @Test
    void testDeviceCache() {
        // Given
        List<DeviceListener> addedDevices = new ArrayList<>();
        controlPoint.addDeviceListener(new DeviceListener() {
            @Override
            public void onDeviceAdded(DeviceProxy device) {
                // Already registered
            }

            @Override
            public void onDeviceRemoved(DeviceProxy device) {
                addedDevices.add(null); // mark removal
            }
        });

        // When: remove device
        String udn = serverDevice.getUdn();
        controlPoint.removeDevice(udn);

        // Then
        assertThat(controlPoint.discoverMediaServers()).isEmpty();
        assertThat(controlPoint.getDevices()).hasSize(1);
    }

    private void buildServerLibrary() {
        var music = new ContentContainer("1", "0", "Music", true);
        serverDevice.addContainer(music);

        var album = new ContentContainer("10", "1", "Album1", true);
        serverDevice.addContainer(album);

        var track = new ContentItem("100", "10", "Track1", ContentItemType.AUDIO_ITEM);
        track.setCreator("Artist")
                .setDuration(Duration.ofMinutes(3))
                .setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo());
        try {
            track.setResourceUrl(URI.create("http://127.0.0.1:8200/content/track1.mp3").toURL());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        serverDevice.addContent(track);
    }
}
