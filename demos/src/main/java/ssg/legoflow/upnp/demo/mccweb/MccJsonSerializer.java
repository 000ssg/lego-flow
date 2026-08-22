package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.DeviceProxy;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.device.DeviceDescription;
import ssg.legoflow.upnp.mediarenderer.PositionInfo;
import ssg.legoflow.upnp.mediarenderer.TransportInfo;
import ssg.legoflow.upnp.mediaserver.ContentContainer;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.DidlLiteParser;
import java.time.Duration;
import java.util.List;
/**
 * JSON serialization utilities for the Media Control Center web API.
 *
 * <p>Provides methods to serialize UPnP domain objects to JSON strings
 * and parse JSON request bodies. Uses {@link StringBuilder}-based JSON
 * construction with no external dependencies.
 *
 * @since 0.1.0
 */
public final class MccJsonSerializer {

    private MccJsonSerializer() {
        // Utility class
    }

    /**
     * Serializes a device proxy to a JSON string.
     *
     * @param device the device proxy to serialize
     * @return the JSON string
     * @since 0.1.0
     */
    public static String deviceToJson(DeviceProxy device) {
        var sb = new StringBuilder();
        sb.append('{');
        appendString(sb, "udn", device.getUdn());
        sb.append(',');
        appendString(sb, "friendlyName", device.getFriendlyName());
        sb.append(',');
        appendString(sb, "deviceType", device.getDeviceType());
        sb.append(',');
        appendString(sb, "baseUrl", device.getBaseUrl() != null ? device.getBaseUrl().toString() : "");
        sb.append(',');
        appendString(sb, "isServer", String.valueOf(device instanceof MediaServerProxy));
        sb.append(',');
        appendString(sb, "isRenderer", String.valueOf(device instanceof MediaRendererProxy));
        sb.append('}');
        return sb.toString();
    }

    /**
     * Serializes a list of device proxies to a JSON array string.
     *
     * @param devices the list of device proxies
     * @return the JSON array string
     * @since 0.1.0
     */
    public static String devicesToJson(List<? extends DeviceProxy> devices) {
        var sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < devices.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(deviceToJson(devices.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Serializes detailed device information including description XML fields,
     * per-type properties (renderer transport, server protocol info), and services.
     *
     * @param device the device proxy to serialize
     * @return the JSON string with extended device details
     * @since 0.1.0
     */
    public static String deviceDetailsToJson(DeviceProxy device) {
        var sb = new StringBuilder();
        sb.append('{');
        appendString(sb, "udn", device.getUdn());
        sb.append(',');
        appendString(sb, "friendlyName", device.getFriendlyName());
        sb.append(',');
        appendString(sb, "deviceType", device.getDeviceType());
        sb.append(',');
        appendString(sb, "baseUrl", device.getBaseUrl() != null ? device.getBaseUrl().toString() : "");
        sb.append(',');
        appendString(sb, "isServer", String.valueOf(device instanceof MediaServerProxy));
        sb.append(',');
        appendString(sb, "isRenderer", String.valueOf(device instanceof MediaRendererProxy));

        // Parse description XML for extended fields
        try {
            String xml = device.getDescriptionXml();
            if (xml != null && !xml.isEmpty()) {
                var desc = DeviceDescription.parseXml(xml);
                sb.append(',');
                appendString(sb, "manufacturer", desc.manufacturer() != null ? desc.manufacturer() : "");
                sb.append(',');
                appendString(sb, "modelName", desc.modelName() != null ? desc.modelName() : "");
                sb.append(',');
                appendString(sb, "modelNumber", desc.modelNumber() != null ? desc.modelNumber() : "");
                sb.append(',');
                appendString(sb, "serialNumber", desc.serialNumber() != null ? desc.serialNumber() : "");
                sb.append(",\"services\":[");
                var services = desc.services();
                for (int i = 0; i < services.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append('{');
                    appendString(sb, "serviceType", services.get(i).serviceType());
                    sb.append(',');
                    appendString(sb, "serviceId", services.get(i).serviceId());
                    sb.append('}');
                }
                sb.append(']');
            } else {
                appendEmptyDescriptionFields(sb);
            }
        } catch (Exception e) {
            appendEmptyDescriptionFields(sb);
        }

        // Renderer-specific: transport state, position, volume
        if (device instanceof MediaRendererProxy renderer) {
            try {
                sb.append(',');
                appendString(sb, "transportState", renderer.getTransportState().value());
                sb.append(',');
                var pos = renderer.getPosition();
                appendString(sb, "trackUri", pos.trackUri() != null ? pos.trackUri() : "");
                sb.append(',');
                appendString(sb, "trackTitle", extractTrackTitle(pos));
                sb.append(',');
                appendString(sb, "position", ContentItem.formatDuration(pos.relTime()));
                sb.append(',');
                appendString(sb, "duration", ContentItem.formatDuration(pos.trackDuration()));
                sb.append(',');
                int vol = renderer.getVolume();
                sb.append("\"rendererVolume\":").append(vol >= 0 ? vol : 0);
                sb.append(',');
                sb.append("\"rendererVolumeAvailable\":").append(vol >= 0);
                sb.append(',');
                sb.append("\"rendererMuted\":").append(renderer.getMute());
            } catch (Exception e) {
                sb.append(",\"transportState\":\"UNKNOWN\",\"trackUri\":\"\",\"position\":\"0:00:00\"");
                sb.append(",\"duration\":\"0:00:00\",\"rendererVolume\":0,\"rendererMuted\":false");
            }
        }

        // Server-specific: protocol info
        if (device instanceof MediaServerProxy server) {
            try {
                var protocols = server.getProtocolInfo();
                sb.append(",\"protocolInfo\":[");
                for (int i = 0; i < protocols.size(); i++) {
                    if (i > 0) sb.append(',');
                    var pi = protocols.get(i);
                    sb.append('{');
                    appendString(sb, "protocol", pi.protocol());
                    sb.append(',');
                    appendString(sb, "network", pi.network());
                    sb.append(',');
                    appendString(sb, "contentFormat", pi.contentFormat());
                    sb.append(',');
                    appendString(sb, "additionalInfo", pi.additionalInfo());
                    sb.append('}');
                }
                sb.append(']');
            } catch (Exception e) {
                sb.append(",\"protocolInfo\":[]");
            }
        }

        sb.append('}');
        return sb.toString();
    }

    private static void appendEmptyDescriptionFields(StringBuilder sb) {
        sb.append(",\"manufacturer\":\"\",\"modelName\":\"\",\"modelNumber\":\"\"");
        sb.append(",\"serialNumber\":\"\",\"services\":[]");
    }

    /**
     * Serializes a content item to a JSON string.
     *
     * @param item the content item to serialize
     * @return the JSON string
     * @since 0.1.0
     */
    public static String contentItemToJson(ContentItem item) {
        var sb = new StringBuilder();
        sb.append('{');
        appendString(sb, "id", item.getId());
        sb.append(',');
        appendString(sb, "parentId", item.getParentId());
        sb.append(',');
        appendString(sb, "title", item.getTitle());
        sb.append(',');
        appendString(sb, "type", item.getType().name());
        sb.append(',');
        appendString(sb, "creator", item.getCreator() != null ? item.getCreator() : "");
        sb.append(',');
        appendString(sb, "duration",
                item.getDuration() != null ? ContentItem.formatDuration(item.getDuration()) : "");
        sb.append(',');
        sb.append("\"size\":").append(item.getSize());
        sb.append(',');
        appendString(sb, "resolution", item.getResolution() != null ? item.getResolution() : "");
        sb.append(',');
        appendString(sb, "albumArtUri", item.getAlbumArtUri() != null ? item.getAlbumArtUri() : "");
        sb.append(',');
        appendString(sb, "resourceUrl",
                item.getResourceUrl() != null ? item.getResourceUrl().toString() : "");
        sb.append(',');
        appendString(sb, "protocolInfo",
                item.getProtocolInfo() != null ? item.getProtocolInfo().toString() : "");
        sb.append(',');
        appendString(sb, "mimeType",
                item.getProtocolInfo() != null ? item.getProtocolInfo().contentFormat() : "");
        sb.append('}');
        return sb.toString();
    }

    /**
     * Serializes a list of content items to a JSON array string.
     *
     * @param items the list of content items
     * @return the JSON array string
     * @since 0.1.0
     */
    public static String contentItemsToJson(List<ContentItem> items) {
        var sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(contentItemToJson(items.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Serializes a content container to a JSON string.
     *
     * @param container the content container to serialize
     * @return the JSON string
     * @since 0.1.0
     */
    public static String contentContainerToJson(ContentContainer container) {
        var sb = new StringBuilder();
        sb.append('{');
        appendString(sb, "id", container.getId());
        sb.append(',');
        appendString(sb, "parentId", container.getParentId());
        sb.append(',');
        appendString(sb, "title", container.getTitle());
        sb.append(',');
        sb.append("\"childCount\":").append(container.getChildCount());
        sb.append(',');
        sb.append("\"children\":").append(contentItemsToJson(container.getChildren()));
        sb.append('}');
        return sb.toString();
    }

    /**
     * Serializes transport info and position info to a JSON string.
     *
     * @param transportInfo the transport info
     * @param positionInfo  the position info
     * @return the JSON string
     * @since 0.1.0
     */
    public static String transportInfoToJson(TransportInfo transportInfo, PositionInfo positionInfo) {
        var sb = new StringBuilder();
        sb.append('{');
        appendString(sb, "state", transportInfo.currentTransportState().value());
        sb.append(',');
        appendString(sb, "speed", transportInfo.currentSpeed());
        sb.append(',');
        appendString(sb, "status", transportInfo.currentTransportStatus().value());
        sb.append(',');
        sb.append("\"track\":").append(positionInfo.track());
        sb.append(',');
        appendString(sb, "trackDuration", ContentItem.formatDuration(positionInfo.trackDuration()));
        sb.append(',');
        appendString(sb, "trackUri", positionInfo.trackUri());
        sb.append(',');
        appendString(sb, "trackTitle", extractTrackTitle(positionInfo));
        sb.append(',');
        appendString(sb, "relTime", ContentItem.formatDuration(positionInfo.relTime()));
        sb.append(',');
        appendString(sb, "absTime", ContentItem.formatDuration(positionInfo.absTime()));
        sb.append('}');
        return sb.toString();
    }

    /**
     * Serializes position info to a JSON string.
     *
     * @param positionInfo the position info
     * @return the JSON string
     * @since 0.1.0
     */
    public static String positionInfoToJson(PositionInfo positionInfo) {
        var sb = new StringBuilder();
        sb.append('{');
        sb.append("\"track\":").append(positionInfo.track());
        sb.append(',');
        appendString(sb, "trackDuration", ContentItem.formatDuration(positionInfo.trackDuration()));
        sb.append(',');
        appendString(sb, "trackUri", positionInfo.trackUri());
        sb.append(',');
        appendString(sb, "trackTitle", extractTrackTitle(positionInfo));
        sb.append(',');
        appendString(sb, "relTime", ContentItem.formatDuration(positionInfo.relTime()));
        sb.append(',');
        appendString(sb, "absTime", ContentItem.formatDuration(positionInfo.absTime()));
        sb.append('}');
        return sb.toString();
    }

    /**
     * Serializes volume state to a JSON string.
     *
     * @param volume the current volume level (0-100)
     * @param muted  whether audio is muted
     * @return the JSON string
     * @since 0.1.0
     */
    public static String volumeToJson(int volume, boolean muted) {
        return "{\"volume\":" + volume + ",\"muted\":" + muted + "}";
    }

    /**
     * Parses a play request from JSON.
     *
     * @param json the JSON string
     * @return the parsed play request
     * @since 0.1.0
     */
    public static PlayRequest parsePlayRequest(String json) {
        if (json == null || json.isBlank()) {
            return new PlayRequest(null, null);
        }
        String uri = extractStringValue(json, "itemUri");
        String metadata = extractStringValue(json, "itemMetadata");
        return new PlayRequest(uri, metadata);
    }

    /**
     * Parses a seek position from JSON.
     *
     * @param json the JSON string
     * @return the seek duration
     * @since 0.1.0
     */
    public static Duration parseSeekRequest(String json) {
        String position = extractStringValue(json, "position");
        if (position == null || position.isEmpty()) {
            return Duration.ZERO;
        }
        return ContentItem.parseDuration(position);
    }

    /**
     * Parses a volume value from JSON.
     *
     * @param json the JSON string
     * @return the volume level (0-100)
     * @since 0.1.0
     */
    public static int parseVolumeRequest(String json) {
        return extractIntValue(json, "volume", 50);
    }

    /**
     * Parses a mute state from JSON.
     *
     * @param json the JSON string
     * @return the mute state
     * @since 0.1.0
     */
    public static boolean parseMuteRequest(String json) {
        return extractBooleanValue(json, "muted", false);
    }

    /**
     * Serializes a list of failed devices to a JSON array string.
     *
     * @param failedDevices the list of failed device records
     * @return the JSON array string
     * @since 0.1.0
     */
    public static String failedDevicesToJson(List<ControlPoint.FailedDevice> failedDevices) {
        var sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < failedDevices.size(); i++) {
            if (i > 0) sb.append(',');
            var fd = failedDevices.get(i);
            sb.append('{');
            appendString(sb, "udn", fd.udn());
            sb.append(',');
            appendString(sb, "location", fd.location());
            sb.append(',');
            appendString(sb, "errorMessage", fd.errorMessage() != null ? fd.errorMessage() : "Unknown error");
            sb.append(',');
            appendString(sb, "responseText", fd.responseText() != null ? fd.responseText() : "");
            sb.append(',');
            sb.append("\"timestamp\":").append(fd.timestamp());
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Creates a JSON error response.
     *
     * @param message the error message
     * @return the JSON error string
     * @since 0.1.0
     */
    public static String errorToJson(String message) {
        var sb = new StringBuilder();
        sb.append('{');
        appendString(sb, "error", escapeJson(message));
        sb.append('}');
        return sb.toString();
    }

    /**
     * Creates a JSON success response with a message.
     *
     * @param message the success message
     * @return the JSON string
     * @since 0.1.0
     */
    public static String messageToJson(String message) {
        var sb = new StringBuilder();
        sb.append('{');
        appendString(sb, "message", escapeJson(message));
        sb.append('}');
        return sb.toString();
    }

    /**
     * A play request containing optional URI and metadata.
     *
     * @param uri      the media URI to play, or null for resume
     * @param metadata the DIDL-Lite metadata, or null
     * @since 0.1.0
     */
    public record PlayRequest(String uri, String metadata) {
    }

    // --- Private helpers ---

    private static final DidlLiteParser DIDL_PARSER = new DidlLiteParser();

    /**
     * Extracts the track title from position info by parsing the DIDL-Lite metadata.
     * Falls back to extracting a human-readable name from the track URI if metadata
     * is unavailable or unparseable.
     *
     * @param positionInfo the position info containing track metadata and URI
     * @return the track title, or empty string if unavailable
     * @since 0.1.0
     */
    private static String extractTrackTitle(PositionInfo positionInfo) {
        // First: try parsing the DIDL-Lite metadata for the real title
        String metadata = positionInfo.trackMetadata();
        if (metadata != null && !metadata.isEmpty() && !metadata.equals("NOT_IMPLEMENTED")) {
            try {
                var items = DIDL_PARSER.parse(metadata);
                if (!items.isEmpty()) {
                    String title = items.getFirst().getTitle();
                    if (title != null && !title.isEmpty() && !title.equals("Unknown")) {
                        return title;
                    }
                }
            } catch (Exception ignored) {
                // Fall through to URL-based extraction
            }
        }

        // Fallback: extract a readable name from the track URI
        String uri = positionInfo.trackUri();
        if (uri == null || uri.isEmpty()) return "";

        // Strip query parameters
        String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
        // Get the last path segment
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            String filename = path.substring(lastSlash + 1);
            // Remove file extension
            int dotIdx = filename.lastIndexOf('.');
            if (dotIdx > 0) {
                filename = filename.substring(0, dotIdx);
            }
            // URL-decode common patterns
            return java.net.URLDecoder.decode(filename, java.nio.charset.StandardCharsets.UTF_8);
        }
        return "";
    }

    private static void appendString(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":\"").append(escapeJson(value)).append('"');
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    static String extractStringValue(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIndex = json.indexOf(search);
        if (keyIndex < 0) return null;
        int colonIndex = json.indexOf(':', keyIndex + search.length());
        if (colonIndex < 0) return null;
        int quoteStart = json.indexOf('"', colonIndex + 1);
        if (quoteStart < 0) return null;
        int quoteEnd = findClosingQuote(json, quoteStart + 1);
        if (quoteEnd < 0) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    private static int findClosingQuote(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '\\') {
                i++; // skip escaped char
            } else if (json.charAt(i) == '"') {
                return i;
            }
        }
        return -1;
    }

    static int extractIntValue(String json, String key, int defaultValue) {
        String search = "\"" + key + "\"";
        int keyIndex = json.indexOf(search);
        if (keyIndex < 0) return defaultValue;
        int colonIndex = json.indexOf(':', keyIndex + search.length());
        if (colonIndex < 0) return defaultValue;
        int start = colonIndex + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (start == end) return defaultValue;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static boolean extractBooleanValue(String json, String key, boolean defaultValue) {
        String search = "\"" + key + "\"";
        int keyIndex = json.indexOf(search);
        if (keyIndex < 0) return defaultValue;
        int colonIndex = json.indexOf(':', keyIndex + search.length());
        if (colonIndex < 0) return defaultValue;
        String rest = json.substring(colonIndex + 1).trim();
        if (rest.startsWith("true")) return true;
        if (rest.startsWith("false")) return false;
        return defaultValue;
    }
}
