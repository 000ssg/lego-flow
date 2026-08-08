package ssg.legoflow.media.sip.header;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Parsed SIP Via header per RFC 3261 section 20.42.
 *
 * <p>Format: {@code SIP/2.0/transport host:port;branch=z9hG4bK...;received=ip;rport=port}
 *
 * @param protocol  the protocol (e.g., "SIP/2.0")
 * @param transport the transport (UDP, TCP, TLS, etc.)
 * @param host      the sent-by host
 * @param port      the sent-by port, or -1 if not specified
 * @param branch    the branch parameter (transaction ID)
 * @param params    additional parameters
 * @since 0.1.0
 */
public record ViaHeader(
        String protocol,
        String transport,
        String host,
        int port,
        String branch,
        Map<String, String> params
) {

    /** Via branch magic cookie prefix (RFC 3261 section 8.1.1.7). */
    public static final String BRANCH_MAGIC_COOKIE = "z9hG4bK";

    /**
     * Creates a Via header.
     *
     * @since 0.1.0
     */
    public ViaHeader {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(transport, "transport");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(branch, "branch");
        params = Map.copyOf(params);
    }

    /**
     * Parses a Via header value string.
     *
     * @param value the Via header value
     * @return the parsed Via header
     * @throws IllegalArgumentException if the format is invalid
     * @since 0.1.0
     */
    public static ViaHeader parse(String value) {
        Objects.requireNonNull(value, "value");
        String s = value.strip();

        // Parse protocol/version/transport  sent-by ;params
        // e.g., "SIP/2.0/UDP 192.168.1.1:5060;branch=z9hG4bK776asdhds"
        int spaceIdx = s.indexOf(' ');
        if (spaceIdx < 0) {
            throw new IllegalArgumentException("Invalid Via header: " + value);
        }

        String protocolPart = s.substring(0, spaceIdx);
        String rest = s.substring(spaceIdx + 1).strip();

        // Split protocol into protocol/version and transport
        int lastSlash = protocolPart.lastIndexOf('/');
        if (lastSlash < 0) {
            throw new IllegalArgumentException("Invalid Via protocol: " + protocolPart);
        }
        String protocol = protocolPart.substring(0, lastSlash);
        String transport = protocolPart.substring(lastSlash + 1).toUpperCase();

        // Parse sent-by and parameters
        Map<String, String> params = new LinkedHashMap<>();
        String sentBy;

        int semiIdx = rest.indexOf(';');
        if (semiIdx >= 0) {
            sentBy = rest.substring(0, semiIdx).strip();
            parseParams(rest.substring(semiIdx + 1), params);
        } else {
            sentBy = rest;
        }

        // Parse host:port
        String host;
        int port = -1;
        if (sentBy.startsWith("[")) {
            int bracketEnd = sentBy.indexOf(']');
            host = sentBy.substring(0, bracketEnd + 1);
            if (bracketEnd + 1 < sentBy.length() && sentBy.charAt(bracketEnd + 1) == ':') {
                port = Integer.parseInt(sentBy.substring(bracketEnd + 2));
            }
        } else {
            int colonIdx = sentBy.lastIndexOf(':');
            if (colonIdx >= 0) {
                host = sentBy.substring(0, colonIdx);
                port = Integer.parseInt(sentBy.substring(colonIdx + 1));
            } else {
                host = sentBy;
            }
        }

        String branch = params.getOrDefault("branch", "");

        return new ViaHeader(protocol, transport, host, port, branch, params);
    }

    private static void parseParams(String paramStr, Map<String, String> params) {
        String[] pairs = paramStr.split(";");
        for (String pair : pairs) {
            pair = pair.strip();
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                params.put(pair.substring(0, eq).strip().toLowerCase(),
                        pair.substring(eq + 1).strip());
            } else {
                params.put(pair.strip().toLowerCase(), "");
            }
        }
    }

    /**
     * Returns the received parameter, if present.
     *
     * @return the received IP address, or empty
     * @since 0.1.0
     */
    public Optional<String> received() {
        return Optional.ofNullable(params.get("received"));
    }

    /**
     * Returns the rport parameter, if present.
     *
     * @return the rport value, or empty
     * @since 0.1.0
     */
    public Optional<Integer> rport() {
        String rp = params.get("rport");
        if (rp == null || rp.isEmpty()) return Optional.empty();
        return Optional.of(Integer.parseInt(rp));
    }

    /**
     * Formats this Via header as a string value.
     *
     * @return the formatted Via value
     * @since 0.1.0
     */
    public String format() {
        var sb = new StringBuilder();
        sb.append(protocol).append('/').append(transport);
        sb.append(' ').append(host);
        if (port > 0) {
            sb.append(':').append(port);
        }

        if (!branch.isEmpty()) {
            sb.append(";branch=").append(branch);
        }
        for (var entry : params.entrySet()) {
            if ("branch".equals(entry.getKey())) continue;
            sb.append(';').append(entry.getKey());
            if (!entry.getValue().isEmpty()) {
                sb.append('=').append(entry.getValue());
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }
}
