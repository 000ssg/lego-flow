package ssg.legoflow.network.ldap.filter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for LDAP search filter strings as defined in RFC 4515.
 *
 * <p>Parses filter expressions like {@code (&(objectClass=person)(cn=John*))}
 * into a {@link SearchFilter} tree.
 *
 * <p>This parser is stateless and thread-safe.
 *
 * @since 0.1.0
 */
public final class FilterParser {

    private FilterParser() {}

    /**
     * Parses an LDAP filter string into a {@link SearchFilter}.
     *
     * @param filterString the filter string (must start and end with parentheses)
     * @return the parsed filter
     * @throws FilterParseException if the filter string is invalid
     */
    public static SearchFilter parse(String filterString) {
        if (filterString == null || filterString.isEmpty()) {
            throw new FilterParseException("Filter string must not be null or empty");
        }
        String trimmed = filterString.trim();
        int[] pos = {0};
        SearchFilter result = parseFilter(trimmed, pos);
        if (pos[0] != trimmed.length()) {
            throw new FilterParseException("Unexpected trailing content at position " + pos[0]);
        }
        return result;
    }

    private static SearchFilter parseFilter(String s, int[] pos) {
        expect(s, pos, '(');

        if (pos[0] >= s.length()) {
            throw new FilterParseException("Unexpected end of filter");
        }

        SearchFilter filter;
        char c = s.charAt(pos[0]);
        filter = switch (c) {
            case '&' -> parseAndFilter(s, pos);
            case '|' -> parseOrFilter(s, pos);
            case '!' -> parseNotFilter(s, pos);
            default -> parseItemFilter(s, pos);
        };

        expect(s, pos, ')');
        return filter;
    }

    private static SearchFilter.And parseAndFilter(String s, int[] pos) {
        pos[0]++; // skip '&'
        List<SearchFilter> filters = new ArrayList<>();
        skipWhitespace(s, pos);
        while (pos[0] < s.length() && s.charAt(pos[0]) == '(') {
            filters.add(parseFilter(s, pos));
            skipWhitespace(s, pos);
        }
        if (filters.isEmpty()) {
            throw new FilterParseException("AND filter requires at least one sub-filter");
        }
        return new SearchFilter.And(filters);
    }

    private static SearchFilter.Or parseOrFilter(String s, int[] pos) {
        pos[0]++; // skip '|'
        List<SearchFilter> filters = new ArrayList<>();
        skipWhitespace(s, pos);
        while (pos[0] < s.length() && s.charAt(pos[0]) == '(') {
            filters.add(parseFilter(s, pos));
            skipWhitespace(s, pos);
        }
        if (filters.isEmpty()) {
            throw new FilterParseException("OR filter requires at least one sub-filter");
        }
        return new SearchFilter.Or(filters);
    }

    private static SearchFilter.Not parseNotFilter(String s, int[] pos) {
        pos[0]++; // skip '!'
        skipWhitespace(s, pos);
        SearchFilter inner = parseFilter(s, pos);
        return new SearchFilter.Not(inner);
    }

    private static SearchFilter parseItemFilter(String s, int[] pos) {
        // Parse attribute description
        int attrStart = pos[0];
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '=' || c == '~' || c == '>' || c == '<' || c == ':') break;
            pos[0]++;
        }

        String attribute = s.substring(attrStart, pos[0]).trim();

        if (pos[0] >= s.length()) {
            throw new FilterParseException("Unexpected end of filter after attribute");
        }

        char op = s.charAt(pos[0]);

        // Extensible match: attr:dn:rule:=value or :rule:=value
        if (op == ':' || attribute.contains(":")) {
            return parseExtensibleMatch(s, pos, attribute);
        }

        return switch (op) {
            case '=' -> {
                pos[0]++; // skip '='
                String value = parseAssertionValue(s, pos);
                // Check for presence: (attr=*)
                if ("*".equals(value)) {
                    yield new SearchFilter.Present(attribute);
                }
                // Check for substring: contains *
                if (value.contains("*")) {
                    yield parseSubstringFilter(attribute, value);
                }
                yield new SearchFilter.EqualityMatch(attribute,
                        unescapeFilterValue(value));
            }
            case '>' -> {
                pos[0]++; // skip '>'
                expect(s, pos, '=');
                String value = parseAssertionValue(s, pos);
                yield new SearchFilter.GreaterOrEqual(attribute,
                        unescapeFilterValue(value));
            }
            case '<' -> {
                pos[0]++; // skip '<'
                expect(s, pos, '=');
                String value = parseAssertionValue(s, pos);
                yield new SearchFilter.LessOrEqual(attribute,
                        unescapeFilterValue(value));
            }
            case '~' -> {
                pos[0]++; // skip '~'
                expect(s, pos, '=');
                String value = parseAssertionValue(s, pos);
                yield new SearchFilter.ApproxMatch(attribute,
                        unescapeFilterValue(value));
            }
            default -> throw new FilterParseException("Unexpected operator: " + op);
        };
    }

    private static SearchFilter parseExtensibleMatch(String s, int[] pos, String rawAttr) {
        // Parse extensible match components
        // Format: [attr][:dn][:rule]:=value
        String attribute = null;
        String matchingRule = null;
        boolean dnAttributes = false;

        // Re-parse from the full attribute+colons text
        String full = rawAttr;
        // Continue reading until :=
        while (pos[0] < s.length() && !(s.charAt(pos[0]) == '=' &&
                pos[0] > 0 && s.substring(attrStart(full, pos), pos[0]).contains(":"))) {
            if (s.charAt(pos[0]) == ':' && pos[0] + 1 < s.length() && s.charAt(pos[0] + 1) == '=') {
                break;
            }
            full += s.charAt(pos[0]);
            pos[0]++;
        }

        // Skip :=
        if (pos[0] < s.length() && s.charAt(pos[0]) == ':') pos[0]++;
        expect(s, pos, '=');

        String value = parseAssertionValue(s, pos);

        // Parse the components before :=
        String[] parts = full.split(":");
        if (parts.length >= 1 && !parts[0].isEmpty()) {
            attribute = parts[0];
        }
        for (int i = 1; i < parts.length; i++) {
            if ("dn".equalsIgnoreCase(parts[i])) {
                dnAttributes = true;
            } else if (!parts[i].isEmpty()) {
                matchingRule = parts[i];
            }
        }

        return new SearchFilter.ExtensibleMatch(matchingRule, attribute,
                unescapeFilterValue(value), dnAttributes);
    }

    private static int attrStart(String full, int[] pos) {
        return 0; // helper placeholder
    }

    private static SearchFilter.Substrings parseSubstringFilter(String attribute, String value) {
        String[] parts = value.split("\\*", -1);
        String initial = parts[0].isEmpty() ? null : parts[0];
        String finalStr = parts[parts.length - 1].isEmpty() ? null : parts[parts.length - 1];
        List<String> any = new ArrayList<>();
        for (int i = 1; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) {
                any.add(parts[i]);
            }
        }
        return new SearchFilter.Substrings(attribute, initial, any, finalStr);
    }

    private static String parseAssertionValue(String s, int[] pos) {
        int start = pos[0];
        while (pos[0] < s.length() && s.charAt(pos[0]) != ')') {
            pos[0]++;
        }
        return s.substring(start, pos[0]);
    }

    private static byte[] unescapeFilterValue(String value) {
        // Handle RFC 4515 hex escapes: \xx
        if (!value.contains("\\")) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
        var result = new java.io.ByteArrayOutputStream();
        int i = 0;
        while (i < value.length()) {
            if (value.charAt(i) == '\\' && i + 2 < value.length()) {
                try {
                    int hi = Character.digit(value.charAt(i + 1), 16);
                    int lo = Character.digit(value.charAt(i + 2), 16);
                    if (hi >= 0 && lo >= 0) {
                        result.write((hi << 4) | lo);
                        i += 3;
                        continue;
                    }
                } catch (Exception e) {
                    // fall through to literal
                }
            }
            byte[] bytes = String.valueOf(value.charAt(i)).getBytes(StandardCharsets.UTF_8);
            result.writeBytes(bytes);
            i++;
        }
        return result.toByteArray();
    }

    private static void expect(String s, int[] pos, char expected) {
        if (pos[0] >= s.length() || s.charAt(pos[0]) != expected) {
            throw new FilterParseException("Expected '" + expected + "' at position " + pos[0] +
                    (pos[0] < s.length() ? " but found '" + s.charAt(pos[0]) + "'" : " but reached end"));
        }
        pos[0]++;
    }

    private static void skipWhitespace(String s, int[] pos) {
        while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) {
            pos[0]++;
        }
    }
}
