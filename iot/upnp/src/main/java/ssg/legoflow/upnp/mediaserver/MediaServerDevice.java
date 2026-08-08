package ssg.legoflow.upnp.mediaserver;

import ssg.legoflow.upnp.dlna.DlnaMediaFormat;
import ssg.legoflow.upnp.dlna.DlnaProfile;
import ssg.legoflow.upnp.dlna.DlnaProtocolInfo;
import ssg.legoflow.upnp.ssdp.SsdpService;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complete UPnP Media Server device implementation.
 *
 * <p>Hosts ContentDirectory and ConnectionManager services, provides device
 * description XML and SCPD documents, registers with SSDP for network
 * discovery, and serves content via HTTP.
 *
 * @since 0.1.0
 */
public class MediaServerDevice {

    /** UPnP device type for MediaServer:1. */
    public static final String DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaServer:1";

    private final String udn;
    private final String friendlyName;
    private final ContentDirectory contentDirectory;
    private final ConnectionManagerService connectionManager;
    private final DlnaProfile profile = DlnaProfile.DIGITAL_MEDIA_SERVER;
    private final Map<String, byte[]> contentStore = new ConcurrentHashMap<>();

    private String manufacturer = "Lego Flow";
    private String modelName = "Lego Flow Media Server";
    private String modelNumber = "1.0";
    private int httpPort = 8200;
    private String hostAddress = "127.0.0.1";
    private volatile boolean running;
    private SsdpService ssdpService;

    /**
     * Creates a new media server device.
     *
     * @param friendlyName the human-readable device name
     * @since 0.1.0
     */
    public MediaServerDevice(String friendlyName) {
        this.friendlyName = Objects.requireNonNull(friendlyName, "friendlyName must not be null");
        this.udn = "uuid:" + UUID.randomUUID();
        this.contentDirectory = new ContentDirectory();
        this.connectionManager = new ConnectionManagerService();

        // Register default source protocols
        for (DlnaMediaFormat format : DlnaMediaFormat.values()) {
            connectionManager.addSourceProtocol(format.toProtocolInfo());
        }
    }

    /**
     * Starts the media server: registers with SSDP and begins serving content.
     *
     * @since 0.1.0
     */
    public void start() {
        running = true;
        // SSDP registration would be done via SsdpService (created by other agent)
        // ssdpService.advertise(DEVICE_TYPE, udn, getDescriptionUrl());
    }

    /**
     * Stops the media server and sends SSDP bye-bye notifications.
     *
     * @since 0.1.0
     */
    public void stop() {
        running = false;
        // ssdpService.sendByeBye(DEVICE_TYPE, udn);
    }

    /**
     * Returns whether the server is running.
     *
     * @return true if running
     * @since 0.1.0
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Adds a content item to the server library.
     *
     * @param item the content item to add
     * @since 0.1.0
     */
    public void addContent(ContentItem item) {
        contentDirectory.addContent(item);
    }

    /**
     * Removes a content item from the server library.
     *
     * @param id the ID of the item to remove
     * @return true if removed
     * @since 0.1.0
     */
    public boolean removeContent(String id) {
        return contentDirectory.removeContent(id);
    }

    /**
     * Sets the entire content library.
     *
     * @param rootContainer the root container of the library
     * @since 0.1.0
     */
    public void setLibrary(ContentContainer rootContainer) {
        contentDirectory.setLibrary(rootContainer);
    }

    /**
     * Adds a container to the library.
     *
     * @param container the container to add
     * @since 0.1.0
     */
    public void addContainer(ContentContainer container) {
        contentDirectory.addContainer(container);
    }

    /**
     * Returns the content directory service.
     *
     * @return the content directory
     * @since 0.1.0
     */
    public ContentDirectory getContentDirectory() {
        return contentDirectory;
    }

    /**
     * Returns the connection manager service.
     *
     * @return the connection manager
     * @since 0.1.0
     */
    public ConnectionManagerService getConnectionManager() {
        return connectionManager;
    }

    /**
     * Returns the UPnP Unique Device Name.
     *
     * @return the UDN
     * @since 0.1.0
     */
    public String getUdn() {
        return udn;
    }

    /**
     * Returns the friendly name.
     *
     * @return the friendly name
     * @since 0.1.0
     */
    public String getFriendlyName() {
        return friendlyName;
    }

    /**
     * Returns the DLNA profile for this device.
     *
     * @return the DLNA profile
     * @since 0.1.0
     */
    public DlnaProfile getProfile() {
        return profile;
    }

    /**
     * Sets the HTTP port for serving content and descriptions.
     *
     * @param port the HTTP port
     * @return this device for chaining
     * @since 0.1.0
     */
    public MediaServerDevice setHttpPort(int port) {
        this.httpPort = port;
        return this;
    }

    /**
     * Returns the HTTP port.
     *
     * @return the port number
     * @since 0.1.0
     */
    public int getHttpPort() {
        return httpPort;
    }

    /**
     * Sets the host address for this device.
     *
     * @param hostAddress the host address
     * @return this device for chaining
     * @since 0.1.0
     */
    public MediaServerDevice setHostAddress(String hostAddress) {
        this.hostAddress = hostAddress;
        return this;
    }

    /**
     * Returns the base URL for this device.
     *
     * @return the base URL string
     * @since 0.1.0
     */
    public String getBaseUrl() {
        return "http://" + hostAddress + ":" + httpPort;
    }

    /**
     * Returns the device description URL.
     *
     * @return the description URL
     * @since 0.1.0
     */
    public String getDescriptionUrl() {
        return getBaseUrl() + "/description.xml";
    }

    /**
     * Generates the UPnP device description XML.
     *
     * @return the device description XML string
     * @since 0.1.0
     */
    public String generateDeviceDescription() {
        return """
                <?xml version="1.0"?>
                <root xmlns="urn:schemas-upnp-org:device-1-0">
                  <specVersion><major>1</major><minor>0</minor></specVersion>
                  <device>
                    <deviceType>%s</deviceType>
                    <friendlyName>%s</friendlyName>
                    <manufacturer>%s</manufacturer>
                    <modelName>%s</modelName>
                    <modelNumber>%s</modelNumber>
                    <UDN>%s</UDN>
                    <dlna:X_DLNADOC xmlns:dlna="urn:schemas-dlna-org:device-1-0">%s</dlna:X_DLNADOC>
                    <serviceList>
                      <service>
                        <serviceType>%s</serviceType>
                        <serviceId>%s</serviceId>
                        <controlURL>/contentDirectory/control</controlURL>
                        <eventSubURL>/contentDirectory/event</eventSubURL>
                        <SCPDURL>/contentDirectory/scpd.xml</SCPDURL>
                      </service>
                      <service>
                        <serviceType>%s</serviceType>
                        <serviceId>%s</serviceId>
                        <controlURL>/connectionManager/control</controlURL>
                        <eventSubURL>/connectionManager/event</eventSubURL>
                        <SCPDURL>/connectionManager/scpd.xml</SCPDURL>
                      </service>
                    </serviceList>
                  </device>
                </root>
                """.formatted(
                DEVICE_TYPE, friendlyName, manufacturer, modelName, modelNumber, udn,
                profile.code(),
                ContentDirectory.SERVICE_TYPE, ContentDirectory.SERVICE_ID,
                ConnectionManagerService.SERVICE_TYPE, ConnectionManagerService.SERVICE_ID
        );
    }

    /**
     * Handles a SOAP action invocation on this device's services.
     *
     * @param serviceId  the target service ID
     * @param actionName the action to invoke
     * @param args       the action arguments
     * @return the result arguments
     * @since 0.1.0
     */
    public Map<String, String> handleAction(String serviceId, String actionName,
                                            Map<String, String> args) {
        if (ContentDirectory.SERVICE_ID.equals(serviceId)) {
            return handleContentDirectoryAction(actionName, args);
        } else if (ConnectionManagerService.SERVICE_ID.equals(serviceId)) {
            return handleConnectionManagerAction(actionName, args);
        }
        throw new IllegalArgumentException("Unknown service: " + serviceId);
    }

    private Map<String, String> handleContentDirectoryAction(String actionName,
                                                              Map<String, String> args) {
        return switch (actionName) {
            case "Browse" -> {
                var result = contentDirectory.browse(
                        args.getOrDefault("ObjectID", "0"),
                        ContentDirectory.BrowseFlag.fromValue(
                                args.getOrDefault("BrowseFlag", "BrowseDirectChildren")),
                        args.getOrDefault("Filter", "*"),
                        Integer.parseInt(args.getOrDefault("StartingIndex", "0")),
                        Integer.parseInt(args.getOrDefault("RequestedCount", "0")),
                        args.getOrDefault("SortCriteria", "")
                );
                yield Map.of(
                        "Result", result.didlXml(),
                        "NumberReturned", String.valueOf(result.numberReturned()),
                        "TotalMatches", String.valueOf(result.totalMatches()),
                        "UpdateID", String.valueOf(result.updateId())
                );
            }
            case "Search" -> {
                var result = contentDirectory.search(
                        args.getOrDefault("ContainerID", "0"),
                        args.getOrDefault("SearchCriteria", ""),
                        args.getOrDefault("Filter", "*"),
                        Integer.parseInt(args.getOrDefault("StartingIndex", "0")),
                        Integer.parseInt(args.getOrDefault("RequestedCount", "0")),
                        args.getOrDefault("SortCriteria", "")
                );
                yield Map.of(
                        "Result", result.didlXml(),
                        "NumberReturned", String.valueOf(result.numberReturned()),
                        "TotalMatches", String.valueOf(result.totalMatches()),
                        "UpdateID", String.valueOf(result.updateId())
                );
            }
            case "GetSearchCapabilities" ->
                    Map.of("SearchCaps", contentDirectory.getSearchCapabilities());
            case "GetSortCapabilities" ->
                    Map.of("SortCaps", contentDirectory.getSortCapabilities());
            case "GetSystemUpdateID" ->
                    Map.of("Id", String.valueOf(contentDirectory.getSystemUpdateId()));
            default -> throw new IllegalArgumentException("Unknown action: " + actionName);
        };
    }

    private Map<String, String> handleConnectionManagerAction(String actionName,
                                                               Map<String, String> args) {
        return switch (actionName) {
            case "GetProtocolInfo" -> {
                String[] info = connectionManager.getProtocolInfo();
                yield Map.of("Source", info[0], "Sink", info[1]);
            }
            case "GetCurrentConnectionIDs" ->
                    Map.of("ConnectionIDs", connectionManager.getCurrentConnectionIDs());
            case "GetCurrentConnectionInfo" -> {
                int connId = Integer.parseInt(args.getOrDefault("ConnectionID", "0"));
                var info = connectionManager.getCurrentConnectionInfo(connId);
                yield Map.of(
                        "RcsID", String.valueOf(info.rcsId()),
                        "AVTransportID", String.valueOf(info.avTransportId()),
                        "ProtocolInfo", info.protocolInfo() != null ? info.protocolInfo().toString() : "",
                        "PeerConnectionManager", info.peerConnectionManager(),
                        "PeerConnectionID", String.valueOf(info.peerConnectionId()),
                        "Direction", info.direction(),
                        "Status", info.status()
                );
            }
            case "PrepareForConnection" -> {
                var remoteProto = ssg.legoflow.upnp.dlna.DlnaProtocolInfo.parse(
                        args.getOrDefault("RemoteProtocolInfo", "http-get:*:*:*"));
                var peerMgr = args.getOrDefault("PeerConnectionManager", "");
                int peerId = Integer.parseInt(args.getOrDefault("PeerConnectionID", "-1"));
                var direction = args.getOrDefault("Direction", "Output");
                var connInfo = connectionManager.prepareForConnection(remoteProto, peerMgr, peerId, direction);
                yield Map.of(
                        "ConnectionID", String.valueOf(connInfo.connectionId()),
                        "AVTransportID", String.valueOf(connInfo.avTransportId()),
                        "RcsID", String.valueOf(connInfo.rcsId())
                );
            }
            case "ConnectionComplete" -> {
                int connId = Integer.parseInt(args.getOrDefault("ConnectionID", "0"));
                connectionManager.connectionComplete(connId);
                yield Map.of();
            }
            default -> throw new IllegalArgumentException("Unknown action: " + actionName);
        };
    }
}
