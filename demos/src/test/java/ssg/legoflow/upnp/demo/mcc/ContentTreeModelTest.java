package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.dlna.DlnaMediaFormat;
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
 * Tests for {@link ContentTreeModel}.
 *
 * <p>Verifies lazy-loading behavior, tree navigation, search, and refresh
 * functionality using an in-process media server.
 *
 * @since 0.1.0
 */
class ContentTreeModelTest {

    private ControlPoint controlPoint;
    private MediaServerDevice serverDevice;
    private MediaServerProxy serverProxy;
    private ContentTreeModel treeModel;

    @BeforeEach
    void setUp() {
        controlPoint = new ControlPoint();
        controlPoint.start();

        serverDevice = new MediaServerDevice("Tree Test Server");
        serverDevice.setHttpPort(8200).setHostAddress("127.0.0.1");
        buildTestLibrary(serverDevice);
        serverDevice.start();

        controlPoint.registerLocalServer(serverDevice);
        serverProxy = controlPoint.discoverMediaServers().getFirst();
        treeModel = new ContentTreeModel(serverProxy);
    }

    @AfterEach
    void tearDown() {
        controlPoint.stop();
        serverDevice.stop();
    }

    @Test
    void testRootChildren() {
        // When: get root children
        var root = (ContentTreeModel.ContentTreeNode) treeModel.getRoot();
        int childCount = treeModel.getChildCount(root);

        // Then: root has Music and Video containers
        assertThat(childCount).isGreaterThanOrEqualTo(2);

        var children = treeModel.getChildren(root);
        assertThat(children).anyMatch(n -> n.getTitle().equals("Music") && n.isContainer());
        assertThat(children).anyMatch(n -> n.getTitle().equals("Video") && n.isContainer());
    }

    @Test
    void testLazyLoading() {
        // Given: a fresh tree model
        var root = (ContentTreeModel.ContentTreeNode) treeModel.getRoot();

        // When: check children of Music container before expanding it
        var rootChildren = treeModel.getChildren(root);
        var musicNode = rootChildren.stream()
                .filter(n -> n.getTitle().equals("Music"))
                .findFirst().orElseThrow();

        // Then: Music is reported as a container (not a leaf)
        assertThat(treeModel.isLeaf(musicNode)).isFalse();

        // When: now explicitly load children (simulating tree expand)
        var musicChildren = treeModel.getChildren(musicNode);

        // Then: children are loaded and cached
        assertThat(musicChildren).isNotEmpty();
        assertThat(musicChildren).anyMatch(n -> n.getTitle().equals("Test Album"));

        // When: access again (should return cached)
        var cachedChildren = treeModel.getChildren(musicNode);
        assertThat(cachedChildren).isEqualTo(musicChildren);
    }

    @Test
    void testNavigateIntoContainer() {
        // Given: tree model
        var root = (ContentTreeModel.ContentTreeNode) treeModel.getRoot();
        var rootChildren = treeModel.getChildren(root);
        var musicNode = rootChildren.stream()
                .filter(n -> n.getTitle().equals("Music"))
                .findFirst().orElseThrow();

        // When: navigate into Music -> Test Album
        var musicChildren = treeModel.getChildren(musicNode);
        var albumNode = musicChildren.stream()
                .filter(n -> n.getTitle().equals("Test Album"))
                .findFirst().orElseThrow();

        // Then: album has children (tracks)
        var albumChildren = treeModel.getChildren(albumNode);
        assertThat(albumChildren).isNotEmpty();
        assertThat(albumChildren).anyMatch(n -> n.getTitle().equals("Track One"));
    }

    @Test
    void testSearch() {
        // When: search for a track
        List<ContentItem> results = serverProxy.search("Track One");

        // Then: search returns the matching item
        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(item -> item.getTitle().equals("Track One"));
    }

    @Test
    void testRefresh() {
        // Given: children are loaded
        var root = (ContentTreeModel.ContentTreeNode) treeModel.getRoot();
        var rootChildren = treeModel.getChildren(root);
        assertThat(rootChildren).isNotEmpty();

        // When: add new content and refresh
        var newContainer = new ContentContainer("4", "0", "Podcasts", true);
        serverDevice.addContainer(newContainer);
        treeModel.refresh();

        // Then: refreshed root children include the new container
        var refreshedChildren = treeModel.getChildren(root);
        assertThat(refreshedChildren).anyMatch(n -> n.getTitle().equals("Podcasts"));
    }

    private void buildTestLibrary(MediaServerDevice server) {
        String baseUrl = server.getBaseUrl();

        var music = new ContentContainer("1", "0", "Music", true);
        server.addContainer(music);

        var album = new ContentContainer("10", "1", "Test Album", true);
        server.addContainer(album);

        var track1 = new ContentItem("100", "10", "Track One", ContentItemType.AUDIO_ITEM);
        track1.setCreator("Test Artist")
                .setDuration(Duration.ofMinutes(3).plusSeconds(30))
                .setSize(4_800_000L)
                .setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/track1.mp3"));
        server.addContent(track1);

        var track2 = new ContentItem("101", "10", "Track Two", ContentItemType.AUDIO_ITEM);
        track2.setCreator("Test Artist")
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
