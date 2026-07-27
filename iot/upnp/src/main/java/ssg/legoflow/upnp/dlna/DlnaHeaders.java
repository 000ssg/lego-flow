package ssg.legoflow.upnp.dlna;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * DLNA-specific HTTP headers used in content transfer between UPnP AV devices.
 *
 * <p>Implements support for the following DLNA HTTP headers:
 * <ul>
 *   <li>{@code getcontentFeatures.dlna.org} — advertises DLNA content features
 *       (profile name, operations, flags) for a served resource</li>
 *   <li>{@code transferMode.dlna.org} — negotiates the transfer mode
 *       (Streaming, Interactive, Background) between client and server</li>
 *   <li>{@code TimeSeekRange.dlna.org} — enables time-based seeking within
 *       streaming content using HTTP byte-range-like semantics</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class DlnaHeaders {

    /** The DLNA content features HTTP header name. @since 1.0.0 */
    public static final String CONTENT_FEATURES = "getcontentFeatures.dlna.org";

    /** The DLNA transfer mode HTTP header name. @since 1.0.0 */
    public static final String TRANSFER_MODE = "transferMode.dlna.org";

    /** The DLNA time seek range HTTP header name. @since 1.0.0 */
    public static final String TIME_SEEK_RANGE = "TimeSeekRange.dlna.org";

    /** The DLNA content type HTTP header name. @since 1.0.0 */
    public static final String CONTENT_TYPE = "contentType.dlna.org";

    private DlnaHeaders() {
        // Utility class
    }

    /**
     * Transfer mode values for the {@code transferMode.dlna.org} header.
     *
     * @since 1.0.0
     */
    public enum TransferMode {
        /** Streaming mode for audio/video content. */
        STREAMING("Streaming"),
        /** Interactive mode for images and other interactive content. */
        INTERACTIVE("Interactive"),
        /** Background download mode. */
        BACKGROUND("Background");

        private final String value;

        TransferMode(String value) {
            this.value = value;
        }

        /**
         * Returns the HTTP header value.
         *
         * @return the transfer mode string
         * @since 1.0.0
         */
        public String value() {
            return value;
        }

        /**
         * Parses a transfer mode from its string value.
         *
         * @param value the string value
         * @return the transfer mode
         * @throws IllegalArgumentException if the value is unknown
         * @since 1.0.0
         */
        public static TransferMode fromValue(String value) {
            for (TransferMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown transfer mode: " + value);
        }
    }

    /**
     * Builds a content features header value for the given DLNA profile.
     *
     * <p>The content features header includes the DLNA profile name,
     * operation flags, and DLNA flags for the content.
     *
     * @param profileName the DLNA.ORG_PN profile name (e.g. "MP3", "JPEG_SM")
     * @param flags       the DLNA flags to include
     * @return the content features header value
     * @since 1.0.0
     */
    public static String buildContentFeatures(String profileName, Set<DlnaFlags> flags) {
        Objects.requireNonNull(profileName, "profileName must not be null");
        Objects.requireNonNull(flags, "flags must not be null");

        var sb = new StringBuilder();
        sb.append("DLNA.ORG_PN=").append(profileName);

        // Determine DLNA.ORG_OP based on flags
        boolean timeSeek = flags.contains(DlnaFlags.LSOP_TIME_BASED_SEEK);
        boolean byteSeek = flags.contains(DlnaFlags.LSOP_BYTE_BASED_SEEK);
        sb.append(";DLNA.ORG_OP=");
        sb.append(timeSeek ? "1" : "0");
        sb.append(byteSeek ? "1" : "0");

        sb.append(";DLNA.ORG_FLAGS=").append(DlnaFlags.toHexString(flags));

        return sb.toString();
    }

    /**
     * Builds a content features header value from a {@link DlnaProtocolInfo}.
     *
     * @param protocolInfo the DLNA protocol info
     * @return the content features header value
     * @since 1.0.0
     */
    public static String buildContentFeatures(DlnaProtocolInfo protocolInfo) {
        Objects.requireNonNull(protocolInfo, "protocolInfo must not be null");
        // The additionalInfo field already contains the DLNA parameters
        return protocolInfo.additionalInfo();
    }

    /**
     * Builds a time seek range header value for the given start and end times.
     *
     * <p>Format: {@code npt=<start>-<end>/<duration>}
     *
     * @param startSeconds  the start position in seconds
     * @param endSeconds    the end position in seconds
     * @param totalSeconds  the total duration in seconds
     * @return the time seek range header value
     * @since 1.0.0
     */
    public static String buildTimeSeekRange(double startSeconds, double endSeconds, double totalSeconds) {
        return String.format("npt=%s-%s/%s",
                formatNptTime(startSeconds),
                formatNptTime(endSeconds),
                formatNptTime(totalSeconds));
    }

    /**
     * Parses a time seek range header value into start and end positions.
     *
     * @param headerValue the TimeSeekRange.dlna.org header value
     * @return a two-element array [startSeconds, endSeconds], or null if parsing fails
     * @since 1.0.0
     */
    public static double[] parseTimeSeekRange(String headerValue) {
        if (headerValue == null || headerValue.isEmpty()) {
            return null;
        }
        // Format: npt=<start>-<end>[/<duration>]
        String range = headerValue;
        if (range.startsWith("npt=")) {
            range = range.substring(4);
        }
        // Remove duration part if present
        int slashIdx = range.indexOf('/');
        if (slashIdx >= 0) {
            range = range.substring(0, slashIdx);
        }
        String[] parts = range.split("-", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            double start = parseNptTime(parts[0].trim());
            double end = parts[1].trim().isEmpty() ? -1 : parseNptTime(parts[1].trim());
            return new double[]{start, end};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Negotiates the transfer mode for a content request.
     *
     * <p>If the requested mode is supported, it is returned. Otherwise, a suitable
     * default is chosen based on the content MIME type.
     *
     * @param requestedMode the requested transfer mode (may be null)
     * @param mimeType      the MIME type of the content
     * @return the negotiated transfer mode
     * @since 1.0.0
     */
    public static TransferMode negotiateTransferMode(String requestedMode, String mimeType) {
        if (requestedMode != null && !requestedMode.isEmpty()) {
            try {
                return TransferMode.fromValue(requestedMode);
            } catch (IllegalArgumentException e) {
                // Fall through to default
            }
        }

        // Default based on content type
        if (mimeType != null) {
            if (mimeType.startsWith("image/")) {
                return TransferMode.INTERACTIVE;
            }
            if (mimeType.startsWith("audio/") || mimeType.startsWith("video/")) {
                return TransferMode.STREAMING;
            }
        }
        return TransferMode.INTERACTIVE;
    }

    /**
     * Builds the complete set of DLNA HTTP response headers for serving content.
     *
     * @param protocolInfo  the DLNA protocol info for the content
     * @param transferMode  the negotiated transfer mode
     * @return a map of HTTP header names to values
     * @since 1.0.0
     */
    public static Map<String, String> buildResponseHeaders(DlnaProtocolInfo protocolInfo,
                                                            TransferMode transferMode) {
        var headers = new LinkedHashMap<String, String>();
        if (protocolInfo != null && !"*".equals(protocolInfo.additionalInfo())) {
            headers.put(CONTENT_FEATURES, protocolInfo.additionalInfo());
        }
        if (transferMode != null) {
            headers.put(TRANSFER_MODE, transferMode.value());
        }
        return headers;
    }

    /**
     * Formats a time value in seconds as an NPT (Normal Play Time) string.
     *
     * @param seconds the time in seconds
     * @return the NPT string (e.g. "1:23:45.678")
     * @since 1.0.0
     */
    static String formatNptTime(double seconds) {
        if (seconds < 0) return "";
        long totalSecs = (long) seconds;
        long hours = totalSecs / 3600;
        long minutes = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;
        double fraction = seconds - totalSecs;
        if (fraction > 0.0005) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, secs,
                    Math.round(fraction * 1000));
        }
        return String.format("%d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Parses an NPT (Normal Play Time) string to seconds.
     *
     * @param npt the NPT string (e.g. "1:23:45.678" or "83.45")
     * @return the time in seconds
     * @since 1.0.0
     */
    static double parseNptTime(String npt) {
        if (npt == null || npt.isEmpty()) {
            return 0;
        }
        String[] parts = npt.split(":");
        if (parts.length == 3) {
            double hours = Double.parseDouble(parts[0]);
            double minutes = Double.parseDouble(parts[1]);
            double seconds = Double.parseDouble(parts[2]);
            return hours * 3600 + minutes * 60 + seconds;
        } else if (parts.length == 2) {
            double minutes = Double.parseDouble(parts[0]);
            double seconds = Double.parseDouble(parts[1]);
            return minutes * 60 + seconds;
        }
        return Double.parseDouble(npt);
    }
}
