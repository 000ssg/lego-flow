package ssg.legoflow.upnp.mediaserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
/**
 * Integration tests for {@link MediaServerDevice} action dispatch,
 * verifying that all new actions are correctly wired through handleAction.
 *
 * @since 0.1.0
 */
class MediaServerActionDispatchTest {

    private MediaServerDevice device;

    @BeforeEach
    void setUp() {
        device = new MediaServerDevice("Test Server");
        device.addContent(new ContentItem("1", "0", "Rock Track", ContentItemType.AUDIO_ITEM));
        device.addContent(new ContentItem("2", "0", "Jazz Song", ContentItemType.AUDIO_ITEM));
    }

    // --- ContentDirectory actions ---

    @Test
    void testSearchAction() {
        var result = device.handleAction(ContentDirectory.SERVICE_ID, "Search",
                Map.of("ContainerID", "0",
                        "SearchCriteria", "dc:title contains \"Rock\"",
                        "Filter", "*",
                        "StartingIndex", "0",
                        "RequestedCount", "0",
                        "SortCriteria", ""));
        assertThat(result).containsKey("Result");
        assertThat(result).containsKey("NumberReturned");
        assertThat(result.get("NumberReturned")).isEqualTo("1");
        assertThat(result.get("TotalMatches")).isEqualTo("1");
    }

    @Test
    void testGetSystemUpdateIDAction() {
        var result = device.handleAction(ContentDirectory.SERVICE_ID, "GetSystemUpdateID", Map.of());
        assertThat(result).containsKey("Id");
        assertThat(Integer.parseInt(result.get("Id"))).isGreaterThan(0);
    }

    @Test
    void testBrowseAction() {
        var result = device.handleAction(ContentDirectory.SERVICE_ID, "Browse",
                Map.of("ObjectID", "0",
                        "BrowseFlag", "BrowseDirectChildren",
                        "Filter", "*",
                        "StartingIndex", "0",
                        "RequestedCount", "0",
                        "SortCriteria", ""));
        assertThat(result.get("NumberReturned")).isEqualTo("2");
    }

    @Test
    void testGetSearchCapabilitiesAction() {
        var result = device.handleAction(ContentDirectory.SERVICE_ID, "GetSearchCapabilities", Map.of());
        assertThat(result.get("SearchCaps")).contains("dc:title");
    }

    @Test
    void testGetSortCapabilitiesAction() {
        var result = device.handleAction(ContentDirectory.SERVICE_ID, "GetSortCapabilities", Map.of());
        assertThat(result.get("SortCaps")).contains("dc:title");
    }

    // --- ConnectionManager actions ---

    @Test
    void testPrepareForConnectionAction() {
        var result = device.handleAction(ConnectionManagerService.SERVICE_ID, "PrepareForConnection",
                Map.of("RemoteProtocolInfo", "http-get:*:audio/mpeg:*",
                        "PeerConnectionManager", "http://peer/cm",
                        "PeerConnectionID", "0",
                        "Direction", "Output"));
        assertThat(result).containsKey("ConnectionID");
    }

    @Test
    void testConnectionCompleteAction() {
        var prepResult = device.handleAction(ConnectionManagerService.SERVICE_ID, "PrepareForConnection",
                Map.of("RemoteProtocolInfo", "http-get:*:audio/mpeg:*",
                        "PeerConnectionManager", "",
                        "PeerConnectionID", "0",
                        "Direction", "Output"));

        var result = device.handleAction(ConnectionManagerService.SERVICE_ID, "ConnectionComplete",
                Map.of("ConnectionID", prepResult.get("ConnectionID")));
        assertThat(result).isEmpty();
    }

    @Test
    void testGetCurrentConnectionIDsAction() {
        var result = device.handleAction(ConnectionManagerService.SERVICE_ID, "GetCurrentConnectionIDs", Map.of());
        assertThat(result).containsKey("ConnectionIDs");
        assertThat(result.get("ConnectionIDs")).isEqualTo("0");
    }

    @Test
    void testGetCurrentConnectionInfoAction() {
        var result = device.handleAction(ConnectionManagerService.SERVICE_ID, "GetCurrentConnectionInfo",
                Map.of("ConnectionID", "0"));
        assertThat(result).containsKey("Direction");
        assertThat(result).containsKey("Status");
    }

    @Test
    void testUnknownServiceThrows() {
        assertThatThrownBy(() -> device.handleAction("unknown:service", "Browse", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUnknownContentDirectoryActionThrows() {
        assertThatThrownBy(() -> device.handleAction(ContentDirectory.SERVICE_ID, "NonExistent", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
