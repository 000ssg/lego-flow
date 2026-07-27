package ssg.legoflow.upnp.gena;

/**
 * Constants for GENA (General Event Notification Architecture) eventing in UPnP.
 *
 * <p>Defines the HTTP headers and values used in GENA SUBSCRIBE, UNSUBSCRIBE,
 * and NOTIFY messages.
 *
 * @since 1.0.0
 */
public final class GenaConstants {

    /** SID (Subscription ID) header name. @since 1.0.0 */
    public static final String HEADER_SID = "SID";

    /** SEQ (event sequence number) header name. @since 1.0.0 */
    public static final String HEADER_SEQ = "SEQ";

    /** NT (notification type) header name. @since 1.0.0 */
    public static final String HEADER_NT = "NT";

    /** NTS (notification sub-type) header name. @since 1.0.0 */
    public static final String HEADER_NTS = "NTS";

    /** TIMEOUT header name. @since 1.0.0 */
    public static final String HEADER_TIMEOUT = "TIMEOUT";

    /** CALLBACK header name. @since 1.0.0 */
    public static final String HEADER_CALLBACK = "CALLBACK";

    /** NT value for GENA event subscriptions. @since 1.0.0 */
    public static final String NT_UPNP_EVENT = "upnp:event";

    /** NTS value for property change notifications. @since 1.0.0 */
    public static final String NTS_PROPCHANGE = "upnp:propchange";

    /** Default subscription timeout in seconds. @since 1.0.0 */
    public static final int DEFAULT_TIMEOUT_SECONDS = 1800;

    /** Prefix for the TIMEOUT header value. @since 1.0.0 */
    public static final String TIMEOUT_PREFIX = "Second-";

    /** Infinite timeout value. @since 1.0.0 */
    public static final String TIMEOUT_INFINITE = "infinite";

    private GenaConstants() {
        // Utility class
    }

    /**
     * Formats a timeout value for the TIMEOUT header.
     *
     * @param seconds the timeout in seconds
     * @return the formatted timeout string (e.g., "Second-1800")
     * @since 1.0.0
     */
    public static String formatTimeout(long seconds) {
        return TIMEOUT_PREFIX + seconds;
    }

    /**
     * Parses a timeout value from the TIMEOUT header.
     *
     * @param timeoutHeader the TIMEOUT header value (e.g., "Second-1800")
     * @return the timeout in seconds, or {@link #DEFAULT_TIMEOUT_SECONDS} if unparseable
     * @since 1.0.0
     */
    public static long parseTimeout(String timeoutHeader) {
        if (timeoutHeader == null || timeoutHeader.isBlank()) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        var trimmed = timeoutHeader.trim();
        if (TIMEOUT_INFINITE.equalsIgnoreCase(trimmed)) {
            return Long.MAX_VALUE;
        }
        if (trimmed.startsWith(TIMEOUT_PREFIX)) {
            try {
                return Long.parseLong(trimmed.substring(TIMEOUT_PREFIX.length()));
            } catch (NumberFormatException e) {
                return DEFAULT_TIMEOUT_SECONDS;
            }
        }
        return DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * Formats a callback URL for the CALLBACK header.
     *
     * @param callbackUrl the callback URL
     * @return the formatted callback header value (e.g., "&lt;http://host:port/callback&gt;")
     * @since 1.0.0
     */
    public static String formatCallback(String callbackUrl) {
        return "<" + callbackUrl + ">";
    }

    /**
     * Parses a callback URL from the CALLBACK header value.
     *
     * @param callbackHeader the CALLBACK header value (e.g., "&lt;http://host:port/callback&gt;")
     * @return the extracted callback URL
     * @since 1.0.0
     */
    public static String parseCallback(String callbackHeader) {
        if (callbackHeader == null) {
            return null;
        }
        var trimmed = callbackHeader.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
