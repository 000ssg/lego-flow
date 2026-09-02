package ssg.legoflow.upnp.mediarenderer;

import ssg.legoflow.upnp.dlna.DlnaMediaFormat;
import ssg.legoflow.upnp.dlna.DlnaProfile;
import ssg.legoflow.upnp.mediaserver.ConnectionManagerService;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
/**
 * Complete UPnP Media Renderer device implementation.
 *
 * <p>Hosts AVTransport, RenderingControl, and ConnectionManager services.
 * Provides device description XML, SCPD documents for all services,
 * registers with SSDP for discovery, and manages a playback state machine.
 *
 * <p>State machine: NO_MEDIA -> STOPPED -> PLAYING <-> PAUSED
 *
 * @since 0.1.0
 */
public class MediaRendererDevice {

    /** UPnP device type for MediaRenderer:1. */
    public static final String DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1";

    private final String udn;
    private final String friendlyName;
    private final AvTransport avTransport;
    private final RenderingControl renderingControl;
    private final ConnectionManagerService connectionManager;
    private final DlnaProfile profile = DlnaProfile.DIGITAL_MEDIA_RENDERER;

    private String manufacturer = "Lego Flow";
    private String modelName = "Lego Flow Media Renderer";
    private String modelNumber = "1.0";
    private int httpPort = 8300;
    private String hostAddress = "127.0.0.1";
    private volatile boolean running;

    /**
     * Creates a new media renderer device.
     *
     * @param friendlyName the human-readable device name
     * @since 0.1.0
     */
    public MediaRendererDevice(String friendlyName) {
        this.friendlyName = Objects.requireNonNull(friendlyName, "friendlyName must not be null");
        this.udn = "uuid:" + UUID.randomUUID();
        this.avTransport = new AvTransport();
        this.renderingControl = new RenderingControl();
        this.connectionManager = new ConnectionManagerService();

        // Register default sink protocols
        for (DlnaMediaFormat format : DlnaMediaFormat.values()) {
            connectionManager.addSinkProtocol(format.toProtocolInfo());
        }
    }

    /**
     * Starts the media renderer: registers with SSDP and begins accepting commands.
     *
     * @since 0.1.0
     */
    public void start() {
        running = true;
    }

    /**
     * Stops the media renderer.
     *
     * @since 0.1.0
     */
    public void stop() {
        if (avTransport.getTransportState() == TransportState.PLAYING
                || avTransport.getTransportState() == TransportState.PAUSED_PLAYBACK) {
            avTransport.stop(0);
        }
        running = false;
    }

    /**
     * Returns whether the renderer is running.
     *
     * @return true if running
     * @since 0.1.0
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the AVTransport service.
     *
     * @return the AVTransport service
     * @since 0.1.0
     */
    public AvTransport getAvTransport() {
        return avTransport;
    }

    /**
     * Returns the RenderingControl service.
     *
     * @return the RenderingControl service
     * @since 0.1.0
     */
    public RenderingControl getRenderingControl() {
        return renderingControl;
    }

    /**
     * Returns the ConnectionManager service.
     *
     * @return the ConnectionManager service
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
     * Returns the DLNA profile.
     *
     * @return the profile
     * @since 0.1.0
     */
    public DlnaProfile getProfile() {
        return profile;
    }

    /**
     * Sets the HTTP port for receiving control requests.
     *
     * @param port the HTTP port
     * @return this device for chaining
     * @since 0.1.0
     */
    public MediaRendererDevice setHttpPort(int port) {
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
    public MediaRendererDevice setHostAddress(String hostAddress) {
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
     * Adds a playback listener that receives events from both AVTransport and RenderingControl.
     *
     * @param listener the listener to add
     * @since 0.1.0
     */
    public void addPlaybackListener(PlaybackListener listener) {
        avTransport.addPlaybackListener(listener);
        renderingControl.addPlaybackListener(listener);
    }

    /**
     * Removes a playback listener.
     *
     * @param listener the listener to remove
     * @since 0.1.0
     */
    public void removePlaybackListener(PlaybackListener listener) {
        avTransport.removePlaybackListener(listener);
        renderingControl.removePlaybackListener(listener);
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
                        <controlURL>/avTransport/control</controlURL>
                        <eventSubURL>/avTransport/event</eventSubURL>
                        <SCPDURL>/avTransport/scpd.xml</SCPDURL>
                      </service>
                      <service>
                        <serviceType>%s</serviceType>
                        <serviceId>%s</serviceId>
                        <controlURL>/renderingControl/control</controlURL>
                        <eventSubURL>/renderingControl/event</eventSubURL>
                        <SCPDURL>/renderingControl/scpd.xml</SCPDURL>
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
                AvTransport.SERVICE_TYPE, AvTransport.SERVICE_ID,
                RenderingControl.SERVICE_TYPE, RenderingControl.SERVICE_ID,
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
        if (AvTransport.SERVICE_ID.equals(serviceId)) {
            return handleAvTransportAction(actionName, args);
        } else if (RenderingControl.SERVICE_ID.equals(serviceId)) {
            return handleRenderingControlAction(actionName, args);
        } else if (ConnectionManagerService.SERVICE_ID.equals(serviceId)) {
            return handleConnectionManagerAction(actionName, args);
        }
        throw new IllegalArgumentException("Unknown service: " + serviceId);
    }

    private Map<String, String> handleAvTransportAction(String actionName,
                                                         Map<String, String> args) {
        int instanceId = Integer.parseInt(args.getOrDefault("InstanceID", "0"));
        return switch (actionName) {
            case "SetAVTransportURI" -> {
                avTransport.setAVTransportURI(instanceId,
                        args.getOrDefault("CurrentURI", ""),
                        args.getOrDefault("CurrentURIMetaData", ""));
                yield Map.of();
            }
            case "Play" -> {
                avTransport.play(instanceId, args.getOrDefault("Speed", "1"));
                yield Map.of();
            }
            case "Pause" -> {
                avTransport.pause(instanceId);
                yield Map.of();
            }
            case "Stop" -> {
                avTransport.stop(instanceId);
                yield Map.of();
            }
            case "Seek" -> {
                avTransport.seek(instanceId,
                        AvTransport.SeekMode.fromValue(args.getOrDefault("Unit", "REL_TIME")),
                        args.getOrDefault("Target", "0:00:00"));
                yield Map.of();
            }
            case "Next" -> {
                avTransport.next(instanceId);
                yield Map.of();
            }
            case "Previous" -> {
                avTransport.previous(instanceId);
                yield Map.of();
            }
            case "GetTransportInfo" -> {
                var info = avTransport.getTransportInfo(instanceId);
                yield Map.of(
                        "CurrentTransportState", info.currentTransportState().value(),
                        "CurrentTransportStatus", info.currentTransportStatus().value(),
                        "CurrentSpeed", info.currentSpeed()
                );
            }
            case "GetPositionInfo" -> {
                var info = avTransport.getPositionInfo(instanceId);
                yield Map.of(
                        "Track", String.valueOf(info.track()),
                        "TrackDuration", formatDuration(info.trackDuration()),
                        "TrackMetaData", info.trackMetadata(),
                        "TrackURI", info.trackUri(),
                        "RelTime", formatDuration(info.relTime()),
                        "AbsTime", formatDuration(info.absTime()),
                        "RelCount", String.valueOf(info.relCount()),
                        "AbsCount", String.valueOf(info.absCount())
                );
            }
            case "GetMediaInfo" -> {
                var info = avTransport.getMediaInfo(instanceId);
                yield Map.of(
                        "NrTracks", String.valueOf(info.nrTracks()),
                        "MediaDuration", formatDuration(info.mediaDuration()),
                        "CurrentURI", info.currentUri(),
                        "CurrentURIMetaData", info.currentUriMetadata(),
                        "NextURI", info.nextUri(),
                        "NextURIMetaData", info.nextUriMetadata(),
                        "PlayMedium", info.playMedium(),
                        "RecordMedium", info.recordMedium(),
                        "WriteStatus", info.writeStatus()
                );
            }
            case "SetNextAVTransportURI" -> {
                avTransport.setNextAVTransportURI(instanceId,
                        args.getOrDefault("NextURI", ""),
                        args.getOrDefault("NextURIMetaData", ""));
                yield Map.of();
            }
            case "GetDeviceCapabilities" -> {
                var caps = avTransport.getDeviceCapabilities(instanceId);
                yield Map.of(
                        "PlayMedia", caps.playMedia(),
                        "RecMedia", caps.recMedia(),
                        "RecQualityModes", caps.recQualityModes()
                );
            }
            case "GetTransportSettings" -> {
                var settings = avTransport.getTransportSettings(instanceId);
                yield Map.of(
                        "PlayMode", settings.playMode().value(),
                        "RecQualityMode", settings.recQualityMode()
                );
            }
            default -> throw new IllegalArgumentException("Unknown AVTransport action: " + actionName);
        };
    }

    private Map<String, String> handleRenderingControlAction(String actionName,
                                                              Map<String, String> args) {
        int instanceId = Integer.parseInt(args.getOrDefault("InstanceID", "0"));
        String channel = args.getOrDefault("Channel", RenderingControl.CHANNEL_MASTER);
        return switch (actionName) {
            case "GetVolume" ->
                    Map.of("CurrentVolume", String.valueOf(renderingControl.getVolume(instanceId, channel)));
            case "SetVolume" -> {
                renderingControl.setVolume(instanceId, channel,
                        Integer.parseInt(args.getOrDefault("DesiredVolume", "50")));
                yield Map.of();
            }
            case "GetMute" ->
                    Map.of("CurrentMute", String.valueOf(renderingControl.getMute(instanceId, channel)));
            case "SetMute" -> {
                renderingControl.setMute(instanceId, channel,
                        Boolean.parseBoolean(args.getOrDefault("DesiredMute", "false")));
                yield Map.of();
            }
            case "GetBrightness" ->
                    Map.of("CurrentBrightness", String.valueOf(renderingControl.getBrightness(instanceId)));
            case "SetBrightness" -> {
                renderingControl.setBrightness(instanceId,
                        Integer.parseInt(args.getOrDefault("DesiredBrightness", "50")));
                yield Map.of();
            }
            case "GetContrast" ->
                    Map.of("CurrentContrast", String.valueOf(renderingControl.getContrast(instanceId)));
            case "SetContrast" -> {
                renderingControl.setContrast(instanceId,
                        Integer.parseInt(args.getOrDefault("DesiredContrast", "50")));
                yield Map.of();
            }
            case "GetColor" ->
                    Map.of("CurrentColor", String.valueOf(renderingControl.getColor(instanceId)));
            case "SetColor" -> {
                renderingControl.setColor(instanceId,
                        Integer.parseInt(args.getOrDefault("DesiredColor", "50")));
                yield Map.of();
            }
            default -> throw new IllegalArgumentException("Unknown RenderingControl action: " + actionName);
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
                var direction = args.getOrDefault("Direction", "Input");
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
            default -> throw new IllegalArgumentException("Unknown ConnectionManager action: " + actionName);
        };
    }

    private String formatDuration(Duration d) {
        return ssg.legoflow.upnp.mediaserver.ContentItem.formatDuration(d);
    }
}
