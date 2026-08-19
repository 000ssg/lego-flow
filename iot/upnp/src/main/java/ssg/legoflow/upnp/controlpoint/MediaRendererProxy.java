package ssg.legoflow.upnp.controlpoint;

import ssg.legoflow.upnp.mediarenderer.*;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.DidlLiteParser;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Proxy for a remote UPnP Media Renderer device.
 *
 * <p>Provides typed methods for controlling playback, volume, and
 * transport state on a media renderer. Can work with a local
 * {@link MediaRendererDevice} for in-process operation.
 *
 * @since 0.1.0
 */
public class MediaRendererProxy extends DeviceProxy {

    private static final Logger LOG = LoggerFactory.getLogger(MediaRendererProxy.class);
    private static final DidlLiteParser DIDL_PARSER = new DidlLiteParser();

    private MediaRendererDevice localDevice;

    /**
     * Creates a proxy for a remote media renderer.
     *
     * @param udn            the Unique Device Name
     * @param friendlyName   the human-readable name
     * @param baseUrl        the device base URL
     * @param descriptionXml the device description XML
     * @since 0.1.0
     */
    public MediaRendererProxy(String udn, String friendlyName, URL baseUrl,
                              String descriptionXml) {
        super(udn, friendlyName, MediaRendererDevice.DEVICE_TYPE, baseUrl, descriptionXml);
    }

    /**
     * Creates a proxy backed by a local media renderer device (in-process).
     *
     * @param device the local media renderer device
     * @since 0.1.0
     */
    public MediaRendererProxy(MediaRendererDevice device) {
        super(device.getUdn(), device.getFriendlyName(),
                MediaRendererDevice.DEVICE_TYPE,
                createUrl(device.getBaseUrl()),
                device.generateDeviceDescription());
        this.localDevice = device;
    }

    /**
     * Sets the URI and plays a content item.
     *
     * <p>Generates DIDL-Lite metadata XML from the content item to include
     * MIME type and protocol info in the {@code SetAVTransportURI} action.
     * Many renderers (especially LG webOS TVs) require valid metadata with
     * a {@code protocolInfo} attribute to accept the transport URI.
     *
     * @param item the content item to play
     * @since 0.1.0
     */
    public void playItem(ContentItem item) {
        String uri = item.getResourceUrl() != null ? item.getResourceUrl().toString() : "";
        String metadata = generateDidlLiteMetadata(item);
        if (localDevice != null) {
            localDevice.getAvTransport().setAVTransportURI(0, uri, metadata);
            localDevice.getAvTransport().play(0, "1");
            return;
        }
        invokeAction(AvTransport.SERVICE_ID, "SetAVTransportURI",
                Map.of("InstanceID", "0", "CurrentURI", uri, "CurrentURIMetaData", metadata));
        invokeAction(AvTransport.SERVICE_ID, "Play",
                Map.of("InstanceID", "0", "Speed", "1"));
    }

    /**
     * Generates DIDL-Lite XML metadata for a content item.
     *
     * <p>If the item has no protocol info set, attempts to infer the MIME type
     * from the resource URL (e.g. query parameters like {@code mime=video/x-matroska}
     * or file extension).
     *
     * @param item the content item
     * @return DIDL-Lite XML string, or empty string if metadata cannot be generated
     * @since 0.1.0
     */
    private String generateDidlLiteMetadata(ContentItem item) {
        try {
            // Ensure protocol info is set — infer from URL if missing
            if (item.getProtocolInfo() == null && item.getResourceUrl() != null) {
                String mimeType = inferMimeType(item.getResourceUrl().toString());
                if (mimeType != null) {
                    item.setProtocolInfo(
                            ssg.legoflow.upnp.dlna.DlnaProtocolInfo.httpGetSimple(mimeType));
                }
            }
            return DIDL_PARSER.serialize(List.of(item));
        } catch (Exception e) {
            LOG.warn("Failed to generate DIDL-Lite metadata for {}: {}", item, e.getMessage());
            return "";
        }
    }

    /**
     * Infers a MIME type from a media URL.
     *
     * <p>Checks for {@code mime=} query parameter first (common in UPnP media server URLs),
     * then falls back to file extension mapping.
     *
     * @param url the media URL string
     * @return the inferred MIME type, or null if unknown
     * @since 0.1.0
     */
    public static String inferMimeType(String url) {
        if (url == null || url.isEmpty()) return null;

        // Check for mime= query parameter (e.g., from MiniDLNA/ReadyMedia URLs)
        int mimeIdx = url.indexOf("mime=");
        if (mimeIdx >= 0) {
            String afterMime = url.substring(mimeIdx + 5);
            // Find the end of the mime value (next & or end of string)
            int endIdx = afterMime.indexOf('&');
            if (endIdx < 0) endIdx = afterMime.length();
            String mimeValue = afterMime.substring(0, endIdx);
            if (!mimeValue.isEmpty()) {
                return mimeValue;
            }
        }

        // Fall back to file extension mapping
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        String lower = path.toLowerCase();
        if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) return "video/mp4";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".wmv")) return "video/x-ms-wmv";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".ts") || lower.endsWith(".m2ts")) return "video/mp2t";
        if (lower.endsWith(".flv")) return "video/x-flv";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".wma")) return "audio/x-ms-wma";
        if (lower.endsWith(".aac") || lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".ogg") || lower.endsWith(".oga")) return "audio/ogg";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return null;
    }

    /**
     * Starts or resumes playback.
     *
     * @since 0.1.0
     */
    public void play() {
        if (localDevice != null) {
            localDevice.getAvTransport().play(0, "1");
            return;
        }
        invokeAction(AvTransport.SERVICE_ID, "Play",
                Map.of("InstanceID", "0", "Speed", "1"));
    }

    /**
     * Pauses playback.
     *
     * @since 0.1.0
     */
    public void pause() {
        if (localDevice != null) {
            localDevice.getAvTransport().pause(0);
            return;
        }
        invokeAction(AvTransport.SERVICE_ID, "Pause",
                Map.of("InstanceID", "0"));
    }

    /**
     * Stops playback.
     *
     * @since 0.1.0
     */
    public void stop() {
        if (localDevice != null) {
            localDevice.getAvTransport().stop(0);
            return;
        }
        invokeAction(AvTransport.SERVICE_ID, "Stop",
                Map.of("InstanceID", "0"));
    }

    /**
     * Seeks to a position.
     *
     * @param position the target position
     * @since 0.1.0
     */
    public void seek(Duration position) {
        String timeStr = ContentItem.formatDuration(position);
        if (localDevice != null) {
            localDevice.getAvTransport().seek(0, AvTransport.SeekMode.REL_TIME, timeStr);
            return;
        }
        invokeAction(AvTransport.SERVICE_ID, "Seek",
                Map.of("InstanceID", "0", "Unit", "REL_TIME", "Target", timeStr));
    }

    /**
     * Advances to the next track.
     *
     * @since 0.1.0
     */
    public void next() {
        if (localDevice != null) {
            localDevice.getAvTransport().next(0);
            return;
        }
        invokeAction(AvTransport.SERVICE_ID, "Next",
                Map.of("InstanceID", "0"));
    }

    /**
     * Returns to the previous track.
     *
     * @since 0.1.0
     */
    public void previous() {
        if (localDevice != null) {
            localDevice.getAvTransport().previous(0);
            return;
        }
        invokeAction(AvTransport.SERVICE_ID, "Previous",
                Map.of("InstanceID", "0"));
    }

    /**
     * Returns the current volume level.
     *
     * <p>Returns -1 if the renderer does not support or authorize the
     * {@code GetVolume} action (e.g. LG webOS TVs return UPnP error 606).
     *
     * @return the volume (0-100), or -1 if unavailable
     * @since 0.1.0
     */
    public int getVolume() {
        if (localDevice != null) {
            return localDevice.getRenderingControl().getVolume(0, RenderingControl.CHANNEL_MASTER);
        }
        try {
            Map<String, String> result = invokeAction(RenderingControl.SERVICE_ID, "GetVolume",
                    Map.of("InstanceID", "0", "Channel", "Master"));
            return Integer.parseInt(result.getOrDefault("CurrentVolume", "50"));
        } catch (Exception e) {
            LOG.debug("GetVolume not available on {}: {}", getFriendlyName(), e.getMessage());
            return -1;
        }
    }

    /**
     * Sets the volume level.
     *
     * @param volume the desired volume (0-100)
     * @since 0.1.0
     */
    public void setVolume(int volume) {
        if (localDevice != null) {
            localDevice.getRenderingControl().setVolume(0, RenderingControl.CHANNEL_MASTER, volume);
            return;
        }
        invokeAction(RenderingControl.SERVICE_ID, "SetVolume",
                Map.of("InstanceID", "0", "Channel", "Master",
                        "DesiredVolume", String.valueOf(volume)));
    }

    /**
     * Returns whether audio is muted.
     *
     * <p>Returns false if the renderer does not support or authorize the
     * {@code GetMute} action (e.g. LG webOS TVs return UPnP error 606).
     *
     * @return true if muted, false if unmuted or unavailable
     * @since 0.1.0
     */
    public boolean getMute() {
        if (localDevice != null) {
            return localDevice.getRenderingControl().getMute(0, RenderingControl.CHANNEL_MASTER);
        }
        try {
            Map<String, String> result = invokeAction(RenderingControl.SERVICE_ID, "GetMute",
                    Map.of("InstanceID", "0", "Channel", "Master"));
            return Boolean.parseBoolean(result.getOrDefault("CurrentMute", "false"));
        } catch (Exception e) {
            LOG.debug("GetMute not available on {}: {}", getFriendlyName(), e.getMessage());
            return false;
        }
    }

    /**
     * Sets the mute state.
     *
     * @param muted true to mute, false to unmute
     * @since 0.1.0
     */
    public void setMute(boolean muted) {
        if (localDevice != null) {
            localDevice.getRenderingControl().setMute(0, RenderingControl.CHANNEL_MASTER, muted);
            return;
        }
        invokeAction(RenderingControl.SERVICE_ID, "SetMute",
                Map.of("InstanceID", "0", "Channel", "Master",
                        "DesiredMute", String.valueOf(muted)));
    }

    /**
     * Returns the current transport state.
     *
     * @return the transport state
     * @since 0.1.0
     */
    public TransportState getTransportState() {
        if (localDevice != null) {
            return localDevice.getAvTransport().getTransportState();
        }
        Map<String, String> result = invokeAction(AvTransport.SERVICE_ID, "GetTransportInfo",
                Map.of("InstanceID", "0"));
        return TransportState.fromValue(result.getOrDefault("CurrentTransportState", "STOPPED"));
    }

    /**
     * Returns the current position info.
     *
     * @return the position info
     * @since 0.1.0
     */
    public PositionInfo getPosition() {
        if (localDevice != null) {
            return localDevice.getAvTransport().getPositionInfo(0);
        }
        Map<String, String> result = invokeAction(AvTransport.SERVICE_ID, "GetPositionInfo",
                Map.of("InstanceID", "0"));
        return new PositionInfo(
                Integer.parseInt(result.getOrDefault("Track", "0")),
                ContentItem.parseDuration(result.getOrDefault("TrackDuration", "0:00:00")),
                result.getOrDefault("TrackMetaData", ""),
                result.getOrDefault("TrackURI", ""),
                ContentItem.parseDuration(result.getOrDefault("RelTime", "0:00:00")),
                ContentItem.parseDuration(result.getOrDefault("AbsTime", "0:00:00")),
                Integer.parseInt(result.getOrDefault("RelCount", "0")),
                Integer.parseInt(result.getOrDefault("AbsCount", "0"))
        );
    }

    /**
     * Returns the current media info.
     *
     * @return the media info
     * @since 0.1.0
     */
    public MediaInfo getMediaInfo() {
        if (localDevice != null) {
            return localDevice.getAvTransport().getMediaInfo(0);
        }
        Map<String, String> result = invokeAction(AvTransport.SERVICE_ID, "GetMediaInfo",
                Map.of("InstanceID", "0"));
        return new MediaInfo(
                Integer.parseInt(result.getOrDefault("NrTracks", "0")),
                ContentItem.parseDuration(result.getOrDefault("MediaDuration", "0:00:00")),
                result.getOrDefault("CurrentURI", ""),
                result.getOrDefault("CurrentURIMetaData", ""),
                result.getOrDefault("NextURI", ""),
                result.getOrDefault("NextURIMetaData", ""),
                result.getOrDefault("PlayMedium", "NONE"),
                result.getOrDefault("RecordMedium", "NOT_IMPLEMENTED"),
                result.getOrDefault("WriteStatus", "NOT_IMPLEMENTED")
        );
    }

    /**
     * Subscribes to transport state change events.
     *
     * @param listener the playback listener
     * @since 0.1.0
     */
    public void subscribeTransportEvents(PlaybackListener listener) {
        if (localDevice != null) {
            localDevice.addPlaybackListener(listener);
        }
        // For remote devices, GENA subscription would be set up here
    }

    private static URL createUrl(String urlString) {
        try {
            return java.net.URI.create(urlString).toURL();
        } catch (Exception e) {
            throw new RuntimeException("Invalid URL: " + urlString, e);
        }
    }
}
