package ssg.legoflow.upnp.demo;

import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.mediarenderer.TransportState;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MultiRoomDemo}.
 *
 * @since 1.0.0
 */
class MultiRoomDemoTest {

    private MultiRoomDemo demo;

    @BeforeEach
    void setUp() {
        demo = new MultiRoomDemo();
        demo.start();
    }

    @AfterEach
    void tearDown() {
        demo.stop();
    }

    @Test
    void testMultiRoomDiscovery() {
        // When
        List<MediaServerProxy> servers = demo.getControlPoint().discoverMediaServers();
        List<MediaRendererProxy> renderers = demo.getControlPoint().discoverMediaRenderers();

        // Then
        assertThat(servers).hasSize(1);
        assertThat(renderers).hasSize(3);
        assertThat(renderers).extracting(MediaRendererProxy::getFriendlyName)
                .containsExactlyInAnyOrder("Living Room", "Bedroom", "Kitchen");
    }

    @Test
    void testSynchronizedPlay() {
        // Given
        MediaServerProxy server = demo.getControlPoint().discoverMediaServers().getFirst();
        List<ContentItem> albumItems = server.browse("10");
        assertThat(albumItems).isNotEmpty();
        ContentItem track = albumItems.getFirst();

        // When
        demo.playOnAllRenderers(track);

        // Then - all renderers are playing
        List<MediaRendererProxy> renderers = demo.getControlPoint().discoverMediaRenderers();
        for (MediaRendererProxy renderer : renderers) {
            assertThat(renderer.getTransportState()).isEqualTo(TransportState.PLAYING);
        }
    }

    @Test
    void testIndependentVolume() {
        // When
        demo.setVolume(0, 80); // Living Room
        demo.setVolume(1, 60); // Bedroom
        demo.setVolume(2, 40); // Kitchen

        // Then
        List<MediaRendererProxy> renderers = demo.getControlPoint().discoverMediaRenderers();
        assertThat(renderers.get(0).getVolume()).isEqualTo(80);
        assertThat(renderers.get(1).getVolume()).isEqualTo(60);
        assertThat(renderers.get(2).getVolume()).isEqualTo(40);
    }

    @Test
    void testStopAll() {
        // Given
        MediaServerProxy server = demo.getControlPoint().discoverMediaServers().getFirst();
        List<ContentItem> albumItems = server.browse("10");
        demo.playOnAllRenderers(albumItems.getFirst());

        // When
        demo.stopAll();

        // Then
        List<MediaRendererProxy> renderers = demo.getControlPoint().discoverMediaRenderers();
        for (MediaRendererProxy renderer : renderers) {
            assertThat(renderer.getTransportState()).isEqualTo(TransportState.STOPPED);
        }
    }
}
