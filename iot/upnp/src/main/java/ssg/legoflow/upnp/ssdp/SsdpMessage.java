package ssg.legoflow.upnp.ssdp;

import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents an SSDP (Simple Service Discovery Protocol) message.
 *
 * <p>SSDP messages follow an HTTP-like format with a start line and headers.
 * This record encapsulates the message type, all headers, and the source address
 * (for received messages).
 *
 * <p>Factory methods are provided for constructing standard SSDP messages:
 * {@link #alive}, {@link #byebye}, {@link #search}, and {@link #searchResponse}.
 *
 * @param type    the SSDP message type
 * @param headers the message headers (case-insensitive keys stored in upper case)
 * @param source  the source socket address for received messages; may be {@code null} for outgoing
 * @since 1.0.0
 */
public record SsdpMessage(SsdpMessageType type, Map<String, String> headers, SocketAddress source) {

    /**
     * Creates a new {@code SsdpMessage} with validation.
     *
     * @param type    the message type; must not be {@code null}
     * @param headers the headers map; must not be {@code null}; defensively copied
     * @param source  the source address; may be {@code null}
     * @throws NullPointerException if {@code type} or {@code headers} is {@code null}
     */
    public SsdpMessage {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(headers, "headers must not be null");
        headers = Map.copyOf(headers);
    }

    /**
     * Returns the value of the specified header.
     *
     * @param name the header name (case-insensitive)
     * @return an {@link Optional} containing the header value, or empty if absent
     * @since 1.0.0
     */
    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name.toUpperCase()));
    }

    /**
     * Returns the LOCATION header value.
     *
     * @return the location URL, or empty if absent
     * @since 1.0.0
     */
    public Optional<String> location() {
        return header(SsdpConstants.HEADER_LOCATION);
    }

    /**
     * Returns the USN (Unique Service Name) header value.
     *
     * @return the USN, or empty if absent
     * @since 1.0.0
     */
    public Optional<String> usn() {
        return header(SsdpConstants.HEADER_USN);
    }

    /**
     * Returns the NT (Notification Type) header value.
     *
     * @return the notification type, or empty if absent
     * @since 1.0.0
     */
    public Optional<String> notificationType() {
        return header(SsdpConstants.HEADER_NT);
    }

    /**
     * Returns the ST (Search Target) header value.
     *
     * @return the search target, or empty if absent
     * @since 1.0.0
     */
    public Optional<String> searchTarget() {
        return header(SsdpConstants.HEADER_ST);
    }

    /**
     * Returns the SERVER header value.
     *
     * @return the server string, or empty if absent
     * @since 1.0.0
     */
    public Optional<String> server() {
        return header(SsdpConstants.HEADER_SERVER);
    }

    /**
     * Extracts the max-age value from the CACHE-CONTROL header.
     *
     * @return the max-age in seconds, or {@link SsdpConstants#DEFAULT_MAX_AGE} if not specified
     * @since 1.0.0
     */
    public int maxAge() {
        return header(SsdpConstants.HEADER_CACHE_CONTROL)
                .map(SsdpMessage::parseCacheControlMaxAge)
                .orElse(SsdpConstants.DEFAULT_MAX_AGE);
    }

    /**
     * Creates a NOTIFY ssdp:alive message for device advertisement.
     *
     * @param location    the URL to the device description
     * @param nt          the notification type (device type or service type)
     * @param usn         the unique service name
     * @param server      the server identification string
     * @param maxAge      the cache max-age in seconds
     * @return a new SSDP alive message
     * @throws NullPointerException if any required parameter is {@code null}
     * @since 1.0.0
     */
    public static SsdpMessage alive(String location, String nt, String usn, String server, int maxAge) {
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(nt, "nt must not be null");
        Objects.requireNonNull(usn, "usn must not be null");
        Objects.requireNonNull(server, "server must not be null");

        var headers = new LinkedHashMap<String, String>();
        headers.put(SsdpConstants.HEADER_HOST, SsdpConstants.MULTICAST_HOST);
        headers.put(SsdpConstants.HEADER_CACHE_CONTROL, "max-age=" + maxAge);
        headers.put(SsdpConstants.HEADER_LOCATION, location);
        headers.put(SsdpConstants.HEADER_NT, nt);
        headers.put(SsdpConstants.HEADER_NTS, SsdpConstants.NTS_ALIVE);
        headers.put(SsdpConstants.HEADER_SERVER, server);
        headers.put(SsdpConstants.HEADER_USN, usn);
        return new SsdpMessage(SsdpMessageType.NOTIFY_ALIVE, headers, null);
    }

    /**
     * Creates a NOTIFY ssdp:byebye message for device departure.
     *
     * @param nt  the notification type
     * @param usn the unique service name
     * @return a new SSDP byebye message
     * @throws NullPointerException if any required parameter is {@code null}
     * @since 1.0.0
     */
    public static SsdpMessage byebye(String nt, String usn) {
        Objects.requireNonNull(nt, "nt must not be null");
        Objects.requireNonNull(usn, "usn must not be null");

        var headers = new LinkedHashMap<String, String>();
        headers.put(SsdpConstants.HEADER_HOST, SsdpConstants.MULTICAST_HOST);
        headers.put(SsdpConstants.HEADER_NT, nt);
        headers.put(SsdpConstants.HEADER_NTS, SsdpConstants.NTS_BYEBYE);
        headers.put(SsdpConstants.HEADER_USN, usn);
        return new SsdpMessage(SsdpMessageType.NOTIFY_BYEBYE, headers, null);
    }

    /**
     * Creates an M-SEARCH request message.
     *
     * @param searchTarget the search target (e.g., "ssdp:all", "upnp:rootdevice", or a device/service type)
     * @param mx           the maximum wait time in seconds
     * @return a new M-SEARCH message
     * @throws NullPointerException     if {@code searchTarget} is {@code null}
     * @throws IllegalArgumentException if {@code mx} is not positive
     * @since 1.0.0
     */
    public static SsdpMessage search(String searchTarget, int mx) {
        Objects.requireNonNull(searchTarget, "searchTarget must not be null");
        if (mx <= 0) {
            throw new IllegalArgumentException("MX must be positive: " + mx);
        }

        var headers = new LinkedHashMap<String, String>();
        headers.put(SsdpConstants.HEADER_HOST, SsdpConstants.MULTICAST_HOST);
        headers.put(SsdpConstants.HEADER_MAN, SsdpConstants.MAN_DISCOVER);
        headers.put(SsdpConstants.HEADER_MX, String.valueOf(mx));
        headers.put(SsdpConstants.HEADER_ST, searchTarget);
        return new SsdpMessage(SsdpMessageType.M_SEARCH, headers, null);
    }

    /**
     * Creates an M-SEARCH response message.
     *
     * @param location the URL to the device description
     * @param st       the search target that was matched
     * @param usn      the unique service name
     * @param server   the server identification string
     * @param maxAge   the cache max-age in seconds
     * @return a new M-SEARCH response message
     * @throws NullPointerException if any required parameter is {@code null}
     * @since 1.0.0
     */
    public static SsdpMessage searchResponse(String location, String st, String usn, String server, int maxAge) {
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(st, "st must not be null");
        Objects.requireNonNull(usn, "usn must not be null");
        Objects.requireNonNull(server, "server must not be null");

        var headers = new LinkedHashMap<String, String>();
        headers.put(SsdpConstants.HEADER_CACHE_CONTROL, "max-age=" + maxAge);
        headers.put(SsdpConstants.HEADER_EXT, "");
        headers.put(SsdpConstants.HEADER_LOCATION, location);
        headers.put(SsdpConstants.HEADER_SERVER, server);
        headers.put(SsdpConstants.HEADER_ST, st);
        headers.put(SsdpConstants.HEADER_USN, usn);
        return new SsdpMessage(SsdpMessageType.M_SEARCH_RESPONSE, headers, null);
    }

    /**
     * Parses an SSDP message from raw text.
     *
     * <p>The text must follow the HTTP-like SSDP format with a start line
     * (e.g., "NOTIFY * HTTP/1.1", "M-SEARCH * HTTP/1.1", or "HTTP/1.1 200 OK")
     * followed by header lines separated by CRLF.
     *
     * @param text   the raw SSDP message text
     * @param source the source address of the message; may be {@code null}
     * @return the parsed SSDP message
     * @throws IllegalArgumentException if the text cannot be parsed as a valid SSDP message
     * @throws NullPointerException     if {@code text} is {@code null}
     * @since 1.0.0
     */
    public static SsdpMessage parse(String text, SocketAddress source) {
        Objects.requireNonNull(text, "text must not be null");

        var lines = text.split("\\r?\\n");
        if (lines.length < 2) {
            throw new IllegalArgumentException("Invalid SSDP message: too few lines");
        }

        var startLine = lines[0].trim();
        var type = parseStartLine(startLine);

        var headers = new LinkedHashMap<String, String>();
        for (int i = 1; i < lines.length; i++) {
            var line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            var colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                var key = line.substring(0, colonIdx).trim().toUpperCase();
                var value = line.substring(colonIdx + 1).trim();
                headers.put(key, value);
            }
        }

        // Refine type based on NTS header for NOTIFY messages
        if (type == SsdpMessageType.NOTIFY_ALIVE) {
            var nts = headers.get(SsdpConstants.HEADER_NTS);
            if (nts != null) {
                type = switch (nts) {
                    case SsdpConstants.NTS_BYEBYE -> SsdpMessageType.NOTIFY_BYEBYE;
                    case SsdpConstants.NTS_UPDATE -> SsdpMessageType.NOTIFY_UPDATE;
                    default -> SsdpMessageType.NOTIFY_ALIVE;
                };
            }
        }

        return new SsdpMessage(type, headers, source);
    }

    /**
     * Serializes this SSDP message to its wire format text representation.
     *
     * @return the SSDP message as a string with CRLF line endings
     * @since 1.0.0
     */
    public String serialize() {
        var sb = new StringBuilder();
        sb.append(startLine()).append("\r\n");
        for (var entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }

    private String startLine() {
        return switch (type) {
            case NOTIFY_ALIVE, NOTIFY_BYEBYE, NOTIFY_UPDATE -> "NOTIFY * HTTP/1.1";
            case M_SEARCH -> "M-SEARCH * HTTP/1.1";
            case M_SEARCH_RESPONSE -> "HTTP/1.1 200 OK";
        };
    }

    private static SsdpMessageType parseStartLine(String startLine) {
        if (startLine.startsWith("NOTIFY")) {
            return SsdpMessageType.NOTIFY_ALIVE; // Refined later by NTS header
        } else if (startLine.startsWith("M-SEARCH")) {
            return SsdpMessageType.M_SEARCH;
        } else if (startLine.startsWith("HTTP/")) {
            return SsdpMessageType.M_SEARCH_RESPONSE;
        }
        throw new IllegalArgumentException("Unknown SSDP start line: " + startLine);
    }

    private static int parseCacheControlMaxAge(String cacheControl) {
        var parts = cacheControl.split(",");
        for (var part : parts) {
            var trimmed = part.trim().toLowerCase();
            if (trimmed.startsWith("max-age")) {
                var eqIdx = trimmed.indexOf('=');
                if (eqIdx > 0) {
                    try {
                        return Integer.parseInt(trimmed.substring(eqIdx + 1).trim());
                    } catch (NumberFormatException e) {
                        return SsdpConstants.DEFAULT_MAX_AGE;
                    }
                }
            }
        }
        return SsdpConstants.DEFAULT_MAX_AGE;
    }
}
