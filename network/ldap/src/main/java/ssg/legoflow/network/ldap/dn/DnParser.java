package ssg.legoflow.network.ldap.dn;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for LDAP Distinguished Name strings as defined in RFC 4514.
 *
 * <p>Handles escaped characters including:
 * <ul>
 *   <li>{@code \,} {@code \+} {@code \"} {@code \\} {@code \<} {@code \>} {@code \;}</li>
 *   <li>{@code \xx} hex-encoded bytes</li>
 *   <li>Leading/trailing space and {@code #} escaping</li>
 * </ul>
 *
 * <p>This parser is stateless and thread-safe.
 *
 * @since 1.0.0
 */
public final class DnParser {

    private static final String SPECIAL_CHARS = ",+\"\\<>;";

    private DnParser() {}

    /**
     * Parses a DN string.
     *
     * @param dnString the DN string (may be empty for root DSE)
     * @return the parsed DN
     * @throws DnParseException if the string is malformed
     */
    public static DistinguishedName parse(String dnString) {
        if (dnString == null) {
            throw new DnParseException("DN string must not be null");
        }
        String trimmed = dnString.trim();
        if (trimmed.isEmpty()) {
            return DistinguishedName.empty();
        }

        List<Rdn> rdns = new ArrayList<>();
        int pos = 0;

        while (pos < trimmed.length()) {
            int[] posRef = {pos};
            Rdn rdn = parseRdn(trimmed, posRef);
            rdns.add(rdn);
            pos = posRef[0];

            if (pos < trimmed.length()) {
                char sep = trimmed.charAt(pos);
                if (sep == ',' || sep == ';') {
                    pos++;
                } else {
                    throw new DnParseException("Expected ',' or ';' at position " + pos);
                }
            }
        }

        return new DistinguishedName(rdns);
    }

    private static Rdn parseRdn(String s, int[] pos) {
        List<Rdn.RdnComponent> components = new ArrayList<>();
        components.add(parseRdnComponent(s, pos));

        while (pos[0] < s.length() && s.charAt(pos[0]) == '+') {
            pos[0]++; // skip '+'
            components.add(parseRdnComponent(s, pos));
        }

        return new Rdn(components);
    }

    private static Rdn.RdnComponent parseRdnComponent(String s, int[] pos) {
        // Skip whitespace
        while (pos[0] < s.length() && s.charAt(pos[0]) == ' ') pos[0]++;

        // Parse attribute type
        int typeStart = pos[0];
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '=') break;
            pos[0]++;
        }
        String type = s.substring(typeStart, pos[0]).trim();
        if (type.isEmpty()) {
            throw new DnParseException("Empty attribute type at position " + typeStart);
        }

        if (pos[0] >= s.length() || s.charAt(pos[0]) != '=') {
            throw new DnParseException("Expected '=' at position " + pos[0]);
        }
        pos[0]++; // skip '='

        // Parse attribute value
        String value = parseAttributeValue(s, pos);

        return new Rdn.RdnComponent(type, value);
    }

    private static String parseAttributeValue(String s, int[] pos) {
        if (pos[0] >= s.length()) {
            return "";
        }

        // Check for hex-encoded value (starts with #)
        if (s.charAt(pos[0]) == '#') {
            return parseHexValue(s, pos);
        }

        StringBuilder sb = new StringBuilder();
        boolean inQuotes = s.charAt(pos[0]) == '"';

        if (inQuotes) {
            pos[0]++; // skip opening quote
            while (pos[0] < s.length()) {
                char c = s.charAt(pos[0]);
                if (c == '"') {
                    pos[0]++;
                    break;
                }
                if (c == '\\') {
                    sb.append(parseEscaped(s, pos));
                } else {
                    sb.append(c);
                    pos[0]++;
                }
            }
        } else {
            while (pos[0] < s.length()) {
                char c = s.charAt(pos[0]);
                if (c == ',' || c == ';' || c == '+') break;
                if (c == '\\') {
                    sb.append(parseEscaped(s, pos));
                } else {
                    sb.append(c);
                    pos[0]++;
                }
            }
            // Trim trailing spaces (unescaped)
            while (!sb.isEmpty() && sb.charAt(sb.length() - 1) == ' ') {
                sb.setLength(sb.length() - 1);
            }
        }

        return sb.toString();
    }

    private static String parseHexValue(String s, int[] pos) {
        pos[0]++; // skip '#'
        int start = pos[0];
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (!isHexDigit(c)) break;
            pos[0]++;
        }
        String hex = s.substring(start, pos[0]);
        if (hex.length() % 2 != 0) {
            throw new DnParseException("Odd number of hex digits in value at position " + start);
        }
        byte[] bytes = hexToBytes(hex);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static char parseEscaped(String s, int[] pos) {
        pos[0]++; // skip '\'
        if (pos[0] >= s.length()) {
            throw new DnParseException("Unexpected end after backslash");
        }
        char c = s.charAt(pos[0]);
        // Hex pair
        if (isHexDigit(c) && pos[0] + 1 < s.length() && isHexDigit(s.charAt(pos[0] + 1))) {
            int hi = Character.digit(c, 16);
            int lo = Character.digit(s.charAt(pos[0] + 1), 16);
            pos[0] += 2;
            return (char) ((hi << 4) | lo);
        }
        // Special character escape
        pos[0]++;
        return c;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    /**
     * Escapes a DN attribute value for RFC 4514 string representation.
     *
     * @param value the raw value
     * @return the escaped value
     */
    public static String escapeValue(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean needsEscape = SPECIAL_CHARS.indexOf(c) >= 0;
            // Escape leading space or #
            if (i == 0 && (c == ' ' || c == '#')) needsEscape = true;
            // Escape trailing space
            if (i == value.length() - 1 && c == ' ') needsEscape = true;

            if (needsEscape) {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format("\\%02x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
