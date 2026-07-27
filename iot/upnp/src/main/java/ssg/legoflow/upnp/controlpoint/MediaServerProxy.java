package ssg.legoflow.upnp.controlpoint;

import ssg.legoflow.upnp.dlna.DlnaProtocolInfo;
import ssg.legoflow.upnp.mediaserver.BrowseResult;
import ssg.legoflow.upnp.mediaserver.ContentDirectory;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.DidlLiteParser;
import ssg.legoflow.upnp.mediaserver.MediaServerDevice;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Proxy for a remote UPnP Media Server device.
 *
 * <p>Provides typed methods for browsing and searching the server's
 * content directory. Can work with a local {@link MediaServerDevice}
 * for in-process operation or via SOAP for remote devices.
 *
 * @since 1.0.0
 */
public class MediaServerProxy extends DeviceProxy {

    private final DidlLiteParser didlParser = new DidlLiteParser();
    private MediaServerDevice localDevice;

    /**
     * Creates a proxy for a remote media server.
     *
     * @param udn            the Unique Device Name
     * @param friendlyName   the human-readable name
     * @param baseUrl        the device base URL
     * @param descriptionXml the device description XML
     * @since 1.0.0
     */
    public MediaServerProxy(String udn, String friendlyName, URL baseUrl,
                            String descriptionXml) {
        super(udn, friendlyName, MediaServerDevice.DEVICE_TYPE, baseUrl, descriptionXml);
    }

    /**
     * Creates a proxy backed by a local media server device (in-process).
     *
     * @param device the local media server device
     * @since 1.0.0
     */
    public MediaServerProxy(MediaServerDevice device) {
        super(device.getUdn(), device.getFriendlyName(),
                MediaServerDevice.DEVICE_TYPE,
                createUrl(device.getBaseUrl()),
                device.generateDeviceDescription());
        this.localDevice = device;
    }

    /**
     * Browses the root container of the media server.
     *
     * @return the list of items in the root container
     * @since 1.0.0
     */
    public List<ContentItem> browseRoot() {
        return browse("0");
    }

    /**
     * Browses a specific object in the media server.
     *
     * @param objectId the object ID to browse
     * @return the list of child items
     * @since 1.0.0
     */
    public List<ContentItem> browse(String objectId) {
        BrowseResult result = browseChildren(objectId, 0, 0);
        return didlParser.parse(result.didlXml());
    }

    /**
     * Browses children of a container with pagination.
     *
     * @param containerId the container ID
     * @param start       the starting index
     * @param count       the maximum number of items (0 for all)
     * @return the browse result
     * @since 1.0.0
     */
    public BrowseResult browseChildren(String containerId, int start, int count) {
        if (localDevice != null) {
            return localDevice.getContentDirectory().browse(
                    containerId,
                    ContentDirectory.BrowseFlag.BROWSE_DIRECT_CHILDREN,
                    "*", start, count, "");
        }

        Map<String, String> result = invokeAction(
                ContentDirectory.SERVICE_ID, "Browse",
                Map.of(
                        "ObjectID", containerId,
                        "BrowseFlag", "BrowseDirectChildren",
                        "Filter", "*",
                        "StartingIndex", String.valueOf(start),
                        "RequestedCount", String.valueOf(count),
                        "SortCriteria", ""
                )
        );
        return new BrowseResult(
                result.get("Result"),
                Integer.parseInt(result.getOrDefault("NumberReturned", "0")),
                Integer.parseInt(result.getOrDefault("TotalMatches", "0")),
                Long.parseLong(result.getOrDefault("UpdateID", "0"))
        );
    }

    /**
     * Searches for content items matching the given query.
     *
     * @param query the search query (searches in title by default)
     * @return the list of matching items
     * @since 1.0.0
     */
    public List<ContentItem> search(String query) {
        if (localDevice != null) {
            var result = localDevice.getContentDirectory().search(
                    "0", "dc:title contains \"" + query + "\"",
                    "*", 0, 0, "");
            return didlParser.parse(result.didlXml());
        }

        Map<String, String> result = invokeAction(
                ContentDirectory.SERVICE_ID, "Search",
                Map.of(
                        "ContainerID", "0",
                        "SearchCriteria", "dc:title contains \"" + query + "\"",
                        "Filter", "*",
                        "StartingIndex", "0",
                        "RequestedCount", "0",
                        "SortCriteria", ""
                )
        );
        return didlParser.parse(result.get("Result"));
    }

    /**
     * Gets a specific content item by its object ID.
     *
     * @param objectId the object ID
     * @return the content item, or null if not found
     * @since 1.0.0
     */
    public ContentItem getContent(String objectId) {
        if (localDevice != null) {
            return localDevice.getContentDirectory().getItem(objectId);
        }

        Map<String, String> result = invokeAction(
                ContentDirectory.SERVICE_ID, "Browse",
                Map.of(
                        "ObjectID", objectId,
                        "BrowseFlag", "BrowseMetadata",
                        "Filter", "*",
                        "StartingIndex", "0",
                        "RequestedCount", "1",
                        "SortCriteria", ""
                )
        );
        List<ContentItem> items = didlParser.parse(result.get("Result"));
        return items.isEmpty() ? null : items.getFirst();
    }

    /**
     * Returns the protocol info supported by this server.
     *
     * @return the list of supported protocols
     * @since 1.0.0
     */
    public List<DlnaProtocolInfo> getProtocolInfo() {
        if (localDevice != null) {
            return localDevice.getConnectionManager().getSourceProtocols();
        }

        Map<String, String> result = invokeAction(
                "urn:upnp-org:serviceId:ConnectionManager", "GetProtocolInfo",
                Map.of()
        );
        String source = result.getOrDefault("Source", "");
        List<DlnaProtocolInfo> protocols = new ArrayList<>();
        if (!source.isEmpty()) {
            for (String info : source.split(",")) {
                try {
                    protocols.add(DlnaProtocolInfo.parse(info.trim()));
                } catch (IllegalArgumentException ignored) {
                    // skip malformed entries
                }
            }
        }
        return protocols;
    }

    private static URL createUrl(String urlString) {
        try {
            return java.net.URI.create(urlString).toURL();
        } catch (Exception e) {
            throw new RuntimeException("Invalid URL: " + urlString, e);
        }
    }
}
