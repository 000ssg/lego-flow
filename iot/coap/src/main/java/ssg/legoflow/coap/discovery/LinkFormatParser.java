package ssg.legoflow.coap.discovery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parser and serializer for CoRE Link Format (RFC 6690).
 *
 * <p>Link format is a comma-separated list of link entries, where each entry
 * is a URI in angle brackets followed by semicolon-separated attributes:
 * <pre>
 * &lt;/sensors/temp&gt;;rt="temperature";obs;ct=0,&lt;/sensors/humidity&gt;;rt="humidity"
 * </pre>
 *
 * @since 0.1.0
 */
public final class LinkFormatParser {

    private LinkFormatParser() {
        // Utility class
    }

    /**
     * Parses a CoRE Link Format string into a list of link entries.
     *
     * @param linkFormat the link format string
     * @return the parsed entries
     * @throws NullPointerException if {@code linkFormat} is {@code null}
     * @since 0.1.0
     */
    public static List<LinkFormatEntry> parse(String linkFormat) {
        Objects.requireNonNull(linkFormat, "linkFormat must not be null");
        var entries = new ArrayList<LinkFormatEntry>();

        if (linkFormat.isBlank()) {
            return entries;
        }

        // Split by comma, but be careful of commas inside quoted strings
        var links = splitLinks(linkFormat);

        for (var link : links) {
            link = link.trim();
            if (link.isEmpty()) continue;

            // Extract URI from angle brackets
            int start = link.indexOf('<');
            int end = link.indexOf('>');
            if (start < 0 || end < 0 || end <= start) continue;

            String uri = link.substring(start + 1, end);

            // Parse attributes
            var attributes = new LinkedHashMap<String, String>();
            String remaining = link.substring(end + 1);
            String[] parts = remaining.split(";");

            for (var part : parts) {
                part = part.trim();
                if (part.isEmpty()) continue;

                int eq = part.indexOf('=');
                if (eq >= 0) {
                    String key = part.substring(0, eq).trim();
                    String value = part.substring(eq + 1).trim();
                    // Remove surrounding quotes
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    attributes.put(key, value);
                } else {
                    // Flag attribute (e.g., "obs")
                    attributes.put(part, "");
                }
            }

            entries.add(new LinkFormatEntry(uri, attributes));
        }

        return entries;
    }

    /**
     * Serializes a list of link entries into CoRE Link Format.
     *
     * @param entries the entries to serialize
     * @return the link format string
     * @throws NullPointerException if {@code entries} is {@code null}
     * @since 0.1.0
     */
    public static String serialize(List<LinkFormatEntry> entries) {
        Objects.requireNonNull(entries, "entries must not be null");

        var sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(',');
            var entry = entries.get(i);
            sb.append('<').append(entry.uri()).append('>');

            for (var attr : entry.attributes().entrySet()) {
                sb.append(';').append(attr.getKey());
                if (!attr.getValue().isEmpty()) {
                    // Quote strings that contain special characters
                    String value = attr.getValue();
                    if (needsQuoting(value)) {
                        sb.append("=\"").append(value).append('"');
                    } else {
                        sb.append('=').append(value);
                    }
                }
            }
        }

        return sb.toString();
    }

    private static List<String> splitLinks(String input) {
        var links = new ArrayList<String>();
        int depth = 0;
        boolean inQuotes = false;
        int start = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && c == '<') {
                depth++;
            } else if (!inQuotes && c == '>') {
                depth--;
            } else if (!inQuotes && depth == 0 && c == ',') {
                links.add(input.substring(start, i));
                start = i + 1;
            }
        }
        if (start < input.length()) {
            links.add(input.substring(start));
        }

        return links;
    }

    private static boolean needsQuoting(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_' && c != '.') {
                return true;
            }
        }
        return false;
    }
}
