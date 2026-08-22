package ssg.legoflow.media.sip.header;

import ssg.legoflow.media.sip.protocol.SipUri;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
/**
 * Parsed SIP address header (From, To, Contact) per RFC 3261 section 20.
 *
 * <p>Format: {@code "Display Name" <sip:user@host>;tag=value} or
 * {@code sip:user@host;tag=value}
 *
 * @param displayName the display name, or empty
 * @param uri         the SIP URI
 * @param params      header parameters (tag, etc.)
 * @since 0.1.0
 */
public record AddressHeader(
        Optional<String> displayName,
        SipUri uri,
        Map<String, String> params
) {

    /**
     * Creates an address header.
     *
     * @since 0.1.0
     */
    public AddressHeader {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(uri, "uri");
        params = Map.copyOf(params);
    }

    /**
     * Parses an address header value string.
     *
     * @param value the header value
     * @return the parsed address header
     * @throws IllegalArgumentException if the format is invalid
     * @since 0.1.0
     */
    public static AddressHeader parse(String value) {
        Objects.requireNonNull(value, "value");
        String s = value.strip();

        Optional<String> displayName = Optional.empty();
        String uriStr;
        Map<String, String> params = new LinkedHashMap<>();

        if (s.contains("<")) {
            // Name-addr format: "Display Name" <uri>;params or <uri>;params
            int ltIdx = s.indexOf('<');
            int gtIdx = s.indexOf('>');
            if (gtIdx < 0) {
                throw new IllegalArgumentException("Missing '>' in address: " + value);
            }

            String namePart = s.substring(0, ltIdx).strip();
            if (!namePart.isEmpty()) {
                // Strip quotes if present
                if (namePart.startsWith("\"") && namePart.endsWith("\"")) {
                    namePart = namePart.substring(1, namePart.length() - 1);
                }
                displayName = Optional.of(namePart);
            }

            uriStr = s.substring(ltIdx + 1, gtIdx).strip();

            // Parse parameters after '>'
            String afterGt = s.substring(gtIdx + 1).strip();
            if (afterGt.startsWith(";")) {
                parseParams(afterGt.substring(1), params);
            }
        } else {
            // addr-spec format: uri;params
            // Parameters in addr-spec belong to the header, not the URI
            int semiIdx = findHeaderParamStart(s);
            if (semiIdx >= 0) {
                uriStr = s.substring(0, semiIdx).strip();
                parseParams(s.substring(semiIdx + 1), params);
            } else {
                uriStr = s;
            }
        }

        SipUri uri = SipUri.parse(uriStr);
        return new AddressHeader(displayName, uri, params);
    }

    /**
     * Finds the start of header parameters in addr-spec format.
     * Must skip URI parameters which come before header parameters.
     */
    private static int findHeaderParamStart(String s) {
        // For addr-spec, parameters after the URI host:port are header params
        // Look for ;tag= specifically, or the last set of semicolons
        int tagIdx = s.toLowerCase().indexOf(";tag=");
        if (tagIdx >= 0) return tagIdx;

        // For simple cases, find the first semicolon after the host
        int atIdx = s.indexOf('@');
        if (atIdx >= 0) {
            // After @ look for host:port;params
            int searchFrom = atIdx + 1;
            // Skip past host:port
            int colonIdx = s.indexOf(':', searchFrom);
            if (colonIdx >= 0) {
                return s.indexOf(';', colonIdx);
            }
            return s.indexOf(';', searchFrom);
        }
        return -1;
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
     * Returns the tag parameter value, if present.
     *
     * @return the tag, or empty
     * @since 0.1.0
     */
    public Optional<String> tag() {
        return Optional.ofNullable(params.get("tag"));
    }

    /**
     * Creates a new address header with the specified tag.
     *
     * @param tag the tag value
     * @return a new address header with the tag
     * @since 0.1.0
     */
    public AddressHeader withTag(String tag) {
        var newParams = new LinkedHashMap<>(params);
        newParams.put("tag", tag);
        return new AddressHeader(displayName, uri, newParams);
    }

    /**
     * Formats this address header as a string value.
     *
     * @return the formatted value
     * @since 0.1.0
     */
    public String format() {
        var sb = new StringBuilder();

        displayName.ifPresent(name -> sb.append('"').append(name).append("\" "));
        sb.append('<').append(uri.format()).append('>');

        for (var entry : params.entrySet()) {
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
