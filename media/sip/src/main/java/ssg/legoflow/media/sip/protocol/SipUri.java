package ssg.legoflow.media.sip.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SIP URI parser and representation per RFC 3261 section 19.
 *
 * <p>Supports {@code sip:}, {@code sips:}, and {@code tel:} URI schemes.
 *
 * <p>Format: {@code sip:user@host:port;uri-parameters?headers}
 *
 * @since 0.1.0
 */
public final class SipUri {

    private final String scheme;
    private final Optional<String> user;
    private final Optional<String> password;
    private final String host;
    private final int port;
    private final Map<String, String> parameters;
    private final Map<String, String> headers;

    /**
     * Creates a SIP URI.
     *
     * @param scheme     the URI scheme (sip, sips, or tel)
     * @param user       the user part, or empty
     * @param password   the password part, or empty
     * @param host       the host part
     * @param port       the port, or -1 if not specified
     * @param parameters URI parameters
     * @param headers    header parameters
     */
    public SipUri(String scheme, Optional<String> user, Optional<String> password,
                  String host, int port,
                  Map<String, String> parameters, Map<String, String> headers) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.user = Objects.requireNonNull(user, "user");
        this.password = Objects.requireNonNull(password, "password");
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /**
     * Parses a SIP URI string.
     *
     * <p>Supports {@code sip:user@host:port;params?headers},
     * {@code sips:user@host:port;params?headers}, and
     * {@code tel:+number} formats.
     *
     * @param uri the URI string to parse
     * @return the parsed SIP URI
     * @throws IllegalArgumentException if the URI format is invalid
     */
    public static SipUri parse(String uri) {
        Objects.requireNonNull(uri, "uri");
        String trimmed = uri.strip();

        int colonIdx = trimmed.indexOf(':');
        if (colonIdx < 0) {
            throw new IllegalArgumentException("Invalid SIP URI, missing scheme: " + uri);
        }

        String scheme = trimmed.substring(0, colonIdx).toLowerCase();
        String remainder = trimmed.substring(colonIdx + 1);

        if ("tel".equals(scheme)) {
            return parseTelUri(scheme, remainder);
        }

        if (!"sip".equals(scheme) && !"sips".equals(scheme)) {
            throw new IllegalArgumentException("Unsupported URI scheme: " + scheme);
        }

        return parseSipUri(scheme, remainder);
    }

    private static SipUri parseTelUri(String scheme, String remainder) {
        // tel:+1-201-555-0123;param=value
        Map<String, String> params = new LinkedHashMap<>();
        String number;

        int semiIdx = remainder.indexOf(';');
        if (semiIdx >= 0) {
            number = remainder.substring(0, semiIdx);
            parseParameters(remainder.substring(semiIdx + 1), params);
        } else {
            number = remainder;
        }

        return new SipUri(scheme, Optional.of(number.strip()), Optional.empty(),
                "", -1, params, Map.of());
    }

    private static SipUri parseSipUri(String scheme, String remainder) {
        Optional<String> user = Optional.empty();
        Optional<String> password = Optional.empty();
        Map<String, String> params = new LinkedHashMap<>();
        Map<String, String> hdrs = new LinkedHashMap<>();

        // Split off headers (?...)
        String mainPart;
        int questionIdx = remainder.indexOf('?');
        if (questionIdx >= 0) {
            mainPart = remainder.substring(0, questionIdx);
            parseHeaders(remainder.substring(questionIdx + 1), hdrs);
        } else {
            mainPart = remainder;
        }

        // Split off parameters (;...)
        // Parameters come after host:port, so we need to parse user@hostport;params
        String userHostPort;
        int firstSemiIdx = findParameterStart(mainPart);
        if (firstSemiIdx >= 0) {
            userHostPort = mainPart.substring(0, firstSemiIdx);
            parseParameters(mainPart.substring(firstSemiIdx + 1), params);
        } else {
            userHostPort = mainPart;
        }

        // Check for user@host
        String hostPort;
        int atIdx = userHostPort.indexOf('@');
        if (atIdx >= 0) {
            String userInfo = userHostPort.substring(0, atIdx);
            hostPort = userHostPort.substring(atIdx + 1);

            // Check for user:password
            int pwdIdx = userInfo.indexOf(':');
            if (pwdIdx >= 0) {
                user = Optional.of(userInfo.substring(0, pwdIdx));
                password = Optional.of(userInfo.substring(pwdIdx + 1));
            } else {
                user = Optional.of(userInfo);
            }
        } else {
            hostPort = userHostPort;
        }

        // Parse host:port
        String host;
        int port = -1;

        if (hostPort.startsWith("[")) {
            // IPv6 address
            int bracketEnd = hostPort.indexOf(']');
            if (bracketEnd < 0) {
                throw new IllegalArgumentException("Invalid IPv6 address in URI: " + hostPort);
            }
            host = hostPort.substring(0, bracketEnd + 1);
            if (bracketEnd + 1 < hostPort.length() && hostPort.charAt(bracketEnd + 1) == ':') {
                port = Integer.parseInt(hostPort.substring(bracketEnd + 2));
            }
        } else {
            int portIdx = hostPort.lastIndexOf(':');
            if (portIdx >= 0) {
                host = hostPort.substring(0, portIdx);
                port = Integer.parseInt(hostPort.substring(portIdx + 1));
            } else {
                host = hostPort;
            }
        }

        return new SipUri(scheme, user, password, host, port, params, hdrs);
    }

    /**
     * Finds the start of URI parameters, skipping any colons in the host:port portion.
     */
    private static int findParameterStart(String mainPart) {
        int atIdx = mainPart.indexOf('@');
        int searchFrom = atIdx >= 0 ? atIdx + 1 : 0;

        // If there is a bracket (IPv6), skip past it
        if (mainPart.indexOf('[', searchFrom) >= 0) {
            int bracketEnd = mainPart.indexOf(']', searchFrom);
            if (bracketEnd >= 0) {
                searchFrom = bracketEnd + 1;
            }
        }

        // After host:port, find first semicolon
        // But first skip past the port (if any)
        int colonIdx = mainPart.indexOf(':', searchFrom);
        if (colonIdx >= 0) {
            // There is a port. Semicolons before it are not parameters.
            int semiAfterColon = mainPart.indexOf(';', colonIdx);
            if (semiAfterColon >= 0) {
                return semiAfterColon;
            }
            return -1;
        }

        return mainPart.indexOf(';', searchFrom);
    }

    private static void parseParameters(String paramStr, Map<String, String> params) {
        if (paramStr.isEmpty()) return;
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

    private static void parseHeaders(String headerStr, Map<String, String> headers) {
        if (headerStr.isEmpty()) return;
        String[] pairs = headerStr.split("&");
        for (String pair : pairs) {
            pair = pair.strip();
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                headers.put(pair.substring(0, eq).strip(), pair.substring(eq + 1).strip());
            }
        }
    }

    /** Returns the URI scheme (sip, sips, or tel). */
    public String scheme() { return scheme; }

    /** Returns the user part, or empty. */
    public Optional<String> user() { return user; }

    /** Returns the password part, or empty. */
    public Optional<String> password() { return password; }

    /** Returns the host part. */
    public String host() { return host; }

    /** Returns the port, or -1 if not specified. */
    public int port() { return port; }

    /** Returns the effective port (default 5060 for sip, 5061 for sips). */
    public int effectivePort() {
        if (port > 0) return port;
        return "sips".equals(scheme) ? 5061 : 5060;
    }

    /** Returns the URI parameters. */
    public Map<String, String> parameters() { return parameters; }

    /** Returns the header parameters. */
    public Map<String, String> headers() { return headers; }

    /** Returns true if this is a secure SIP URI (sips:). */
    public boolean isSecure() { return "sips".equals(scheme); }

    /** Returns true if this is a tel: URI. */
    public boolean isTelUri() { return "tel".equals(scheme); }

    /**
     * Gets a URI parameter value.
     *
     * @param name the parameter name
     * @return the parameter value, or empty
     */
    public Optional<String> parameter(String name) {
        return Optional.ofNullable(parameters.get(name.toLowerCase()));
    }

    /**
     * Returns the transport parameter value, if present.
     *
     * @return the transport (udp, tcp, tls, etc.), or empty
     */
    public Optional<String> transport() {
        return parameter("transport");
    }

    /**
     * Formats this URI as a string.
     *
     * @return the formatted URI string
     */
    public String format() {
        var sb = new StringBuilder();
        sb.append(scheme).append(':');

        if (isTelUri()) {
            user.ifPresent(sb::append);
        } else {
            user.ifPresent(u -> {
                sb.append(u);
                password.ifPresent(p -> sb.append(':').append(p));
                sb.append('@');
            });
            sb.append(host);
            if (port > 0) {
                sb.append(':').append(port);
            }
        }

        for (var entry : parameters.entrySet()) {
            sb.append(';').append(entry.getKey());
            if (!entry.getValue().isEmpty()) {
                sb.append('=').append(entry.getValue());
            }
        }

        if (!headers.isEmpty()) {
            sb.append('?');
            boolean first = true;
            for (var entry : headers.entrySet()) {
                if (!first) sb.append('&');
                sb.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SipUri other)) return false;
        return scheme.equals(other.scheme)
                && user.equals(other.user)
                && host.equalsIgnoreCase(other.host)
                && effectivePort() == other.effectivePort();
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, user, host.toLowerCase(), effectivePort());
    }
}
