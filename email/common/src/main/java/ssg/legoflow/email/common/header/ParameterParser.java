package ssg.legoflow.email.common.header;

import ssg.legoflow.email.common.encoding.CharsetUtils;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
/**
 * Parses structured header parameters ({@code ;key=value} pairs).
 *
 * <p>Supports RFC 2231 parameter value continuations and charset/language
 * encoding for non-ASCII filenames and other values.
 *
 * @since 0.1.0
 */
public final class ParameterParser {

    private ParameterParser() {
    }

    /**
     * Parses parameters from a header value string.
     *
     * <p>The input should be the portion after the main value, starting with
     * or containing semicolons. For example, for a Content-Type header
     * {@code text/plain; charset=utf-8}, the input could be the full value.
     *
     * @param headerValue the header value string containing parameters
     * @return a map of parameter names (lowercase) to decoded values
     */
    public static Map<String, String> parse(String headerValue) {
        var params = new LinkedHashMap<String, String>();
        if (headerValue == null || headerValue.isEmpty()) {
            return params;
        }

        // Collect raw parameter segments
        var rawSegments = new LinkedHashMap<String, String>();
        String[] parts = splitParams(headerValue);

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String name = part.substring(0, eq).trim().toLowerCase();
            String value = part.substring(eq + 1).trim();
            value = unquote(value);
            rawSegments.put(name, value);
        }

        // Process RFC 2231 continuations
        var continuations = new TreeMap<String, TreeMap<Integer, String>>();
        var charsetInfo = new LinkedHashMap<String, String>(); // paramName -> charset

        for (var entry : rawSegments.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();

            if (name.contains("*")) {
                // RFC 2231 parameter
                String baseName;
                int section = -1;
                boolean encoded = name.endsWith("*");
                String cleanName = encoded ? name.substring(0, name.length() - 1) : name;

                int dash = cleanName.lastIndexOf('*');
                if (dash > 0) {
                    // Try to parse section number
                    String afterDash = cleanName.substring(dash + 1);
                    try {
                        section = Integer.parseInt(afterDash);
                        baseName = cleanName.substring(0, dash);
                    } catch (NumberFormatException e) {
                        // Not a section number — it's charset-encoded without continuation
                        baseName = cleanName;
                        section = 0;
                    }
                } else {
                    baseName = cleanName;
                    section = 0;
                }

                continuations.computeIfAbsent(baseName, k -> new TreeMap<>()).put(section, value);
                if (encoded && section == 0) {
                    charsetInfo.put(baseName, value);
                }
            } else {
                params.put(name, value);
            }
        }

        // Assemble continuations
        for (var entry : continuations.entrySet()) {
            String baseName = entry.getKey();
            var sections = entry.getValue();
            var assembled = new StringBuilder();
            Charset charset = StandardCharsets.UTF_8;

            for (var sectionEntry : sections.entrySet()) {
                String val = sectionEntry.getValue();
                if (sectionEntry.getKey() == 0 && charsetInfo.containsKey(baseName)) {
                    // First section with charset info: charset'language'value
                    int firstQuote = val.indexOf('\'');
                    if (firstQuote >= 0) {
                        String charsetName = val.substring(0, firstQuote);
                        int secondQuote = val.indexOf('\'', firstQuote + 1);
                        if (secondQuote >= 0) {
                            charset = CharsetUtils.forName(charsetName);
                            val = val.substring(secondQuote + 1);
                        }
                    }
                    assembled.append(decodePercent(val, charset));
                } else if (charsetInfo.containsKey(baseName)) {
                    assembled.append(decodePercent(val, charset));
                } else {
                    assembled.append(val);
                }
            }
            params.put(baseName, assembled.toString());
        }

        return params;
    }

    /**
     * Serializes parameters to a header value string.
     *
     * @param params the parameters to serialize
     * @return the serialized parameter string (starting with {@code ;})
     */
    public static String serialize(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        for (var entry : params.entrySet()) {
            sb.append("; ");
            sb.append(entry.getKey());
            sb.append("=");
            String value = entry.getValue();
            if (needsQuoting(value)) {
                sb.append('"');
                sb.append(value.replace("\\", "\\\\").replace("\"", "\\\""));
                sb.append('"');
            } else {
                sb.append(value);
            }
        }
        return sb.toString();
    }

    static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            String inner = value.substring(1, value.length() - 1);
            // Process escape sequences
            var sb = new StringBuilder(inner.length());
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (c == '\\' && i + 1 < inner.length()) {
                    sb.append(inner.charAt(i + 1));
                    i++;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return value;
    }

    private static String decodePercent(String value, Charset charset) {
        var out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) | lo);
                    i += 2;
                    continue;
                }
            }
            out.write((byte) c);
        }
        return new String(out.toByteArray(), charset);
    }

    private static boolean needsQuoting(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ' || c == '"' || c == '(' || c == ')' || c == ',' || c == '/'
                    || c == ':' || c == ';' || c == '<' || c == '=' || c == '>'
                    || c == '?' || c == '@' || c == '[' || c == ']' || c == '\\') {
                return true;
            }
        }
        return false;
    }

    private static String[] splitParams(String value) {
        // Split on semicolons, respecting quoted strings
        var parts = new java.util.ArrayList<String>();
        var current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == '\\' && inQuotes && i + 1 < value.length()) {
                current.append(c);
                current.append(value.charAt(i + 1));
                i++;
            } else if (c == ';' && !inQuotes) {
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts.toArray(new String[0]);
    }
}
