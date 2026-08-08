package ssg.legoflow.upnp.demo;

import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.mediarenderer.TransportState;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MediaControllerDemo}.
 *
 * @since 0.1.0
 */
class MediaControllerDemoTest {

    private MediaControllerDemo demo;
    private SimpleMediaServerDemo serverDemo;
    private SimpleMediaRendererDemo rendererDemo;

    @BeforeEach
    void setUp() {
        serverDemo = new SimpleMediaServerDemo();
        rendererDemo = new SimpleMediaRendererDemo();
        serverDemo.start();
        rendererDemo.start();

        demo = new MediaControllerDemo();
        demo.start();
        demo.registerServer(serverDemo.getServer());
        demo.registerRenderer(rendererDemo.getRenderer());
    }

    @AfterEach
    void tearDown() {
        demo.stop();
        rendererDemo.stop();
        serverDemo.stop();
    }

    @Test
    void testDiscoverServer() {
        // When
        List<MediaServerProxy> servers = demo.getControlPoint().discoverMediaServers();

        // Then
        assertThat(servers).hasSize(1);
        assertThat(servers.getFirst().getFriendlyName())
                .isEqualTo("Lego Flow Demo Media Server");
    }

    @Test
    void testDiscoverRenderer() {
        // When
        List<MediaRendererProxy> renderers = demo.getControlPoint().discoverMediaRenderers();

        // Then
        assertThat(renderers).hasSize(1);
        assertThat(renderers.getFirst().getFriendlyName())
                .isEqualTo("Lego Flow Demo Renderer");
    }

    @Test
    void testBrowseServerLibrary() {
        // When
        List<ContentItem> rootItems = demo.browseServerLibrary();

        // Then
        assertThat(rootItems).isNotEmpty();
        assertThat(rootItems).extracting(ContentItem::getTitle)
                .contains("Music", "Video", "Photos");
    }

    @Test
    void testPlayOnRenderer() {
        // Given
        List<ContentItem> albumItems = demo.browseContainer("10");
        assertThat(albumItems).isNotEmpty();
        ContentItem track = albumItems.getFirst();

        // When
        demo.playOnRenderer(track);

        // Then
        var renderer = demo.getControlPoint().discoverMediaRenderers().getFirst();
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PLAYING);
    }

    @Test
    void testControlPlayback() {
        // Given
        List<ContentItem> albumItems = demo.browseContainer("10");
        demo.playOnRenderer(albumItems.getFirst());

        // When: pause
        demo.pause();
        var renderer = demo.getControlPoint().discoverMediaRenderers().getFirst();
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.PAUSED_PLAYBACK);

        // When: stop
        demo.stopPlayback();
        assertThat(renderer.getTransportState()).isEqualTo(TransportState.STOPPED);
    }

    @Test
    void testAdjustVolume() {
        // When
        demo.setVolume(80);

        // Then
        assertThat(demo.getVolume()).isEqualTo(80);
    }
}
