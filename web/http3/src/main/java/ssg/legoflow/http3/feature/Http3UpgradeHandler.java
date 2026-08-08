package ssg.legoflow.http3.feature;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Handles HTTP/3 Alt-Svc discovery per RFC 7838.
 *
 * <p>HTTP/3 endpoints are discovered via the Alt-Svc header or ALTSVC
 * HTTP/2 frame. The server advertises {@code h3=":port"} and the client
 * parses this to learn about available HTTP/3 endpoints.</p>
 *
 * @since 0.1.0
 */
public class Http3UpgradeHandler {

    /** The HTTP/3 ALPN protocol identifier. */
    public static final String H3_PROTOCOL = "h3";

    private static final long DEFAULT_MAX_AGE = 86400;
    private static final Pattern ALT_SVC_PATTERN = Pattern.compile(
            "(?<proto>[\\w-]+)=\"(?<host>[^\"]*):(?<port>\\d+)\"(?:;\\s*ma=(?<ma>\\d+))?");

    /**
     * An Alt-Svc entry representing an alternative service endpoint.
     *
     * @param protocol the ALPN protocol (e.g., "h3")
     * @param host     the host (empty string means same host)
     * @param port     the port number
     * @param maxAge   the maximum age in seconds
     * @since 0.1.0
     */
    public record AltSvcEntry(String protocol, String host, int port, long maxAge) {}

    /**
     * Generates an Alt-Svc header value for the given HTTP/3 endpoint.
     *
     * @param host the host (use empty string for same-host)
     * @param port the UDP port number
     * @return the Alt-Svc header value, e.g. {@code h3=":443"}
     * @since 0.1.0
     */
    public String generateAltSvcHeader(String host, int port) {
        if (host == null || host.isEmpty()) {
            return H3_PROTOCOL + "=\":" + port + "\"; ma=" + DEFAULT_MAX_AGE;
        }
        return H3_PROTOCOL + "=\"" + host + ":" + port + "\"; ma=" + DEFAULT_MAX_AGE;
    }

    /**
     * Parses an Alt-Svc header value to discover HTTP/3 endpoints.
     *
     * @param header the Alt-Svc header value
     * @return an optional containing the parsed entry, or empty if not parseable
     * @since 0.1.0
     */
    public Optional<AltSvcEntry> parseAltSvc(String header) {
        if (header == null || header.isEmpty()) {
            return Optional.empty();
        }

        if ("clear".equalsIgnoreCase(header.trim())) {
            return Optional.empty();
        }

        var matcher = ALT_SVC_PATTERN.matcher(header);
        if (matcher.find()) {
            var protocol = matcher.group("proto");
            var host = matcher.group("host");
            int port = Integer.parseInt(matcher.group("port"));
            var maStr = matcher.group("ma");
            long maxAge = maStr != null ? Long.parseLong(maStr) : DEFAULT_MAX_AGE;
            return Optional.of(new AltSvcEntry(protocol, host, port, maxAge));
        }

        return Optional.empty();
    }

    /**
     * Checks whether the given Alt-Svc header advertises HTTP/3.
     *
     * @param header the Alt-Svc header value
     * @return {@code true} if h3 is advertised
     * @since 0.1.0
     */
    public boolean isHttp3Available(String header) {
        return parseAltSvc(header)
                .map(entry -> H3_PROTOCOL.equals(entry.protocol()))
                .orElse(false);
    }
}
