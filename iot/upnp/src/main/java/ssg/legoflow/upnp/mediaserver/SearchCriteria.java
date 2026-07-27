package ssg.legoflow.upnp.mediaserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Parser and evaluator for UPnP ContentDirectory search criteria expressions.
 *
 * <p>Implements the UPnP search query language as defined in the ContentDirectory:1
 * service specification. Supports the following operators:
 * <ul>
 *   <li>{@code contains} — case-insensitive substring match</li>
 *   <li>{@code doesNotContain} — negated substring match</li>
 *   <li>{@code =} — exact match (case-insensitive for string properties)</li>
 *   <li>{@code !=} — not equal</li>
 *   <li>{@code derivedfrom} — checks if an upnp:class starts with the given prefix</li>
 *   <li>{@code exists} — checks if a property is present (non-null and non-empty)</li>
 * </ul>
 *
 * <p>Boolean combinators {@code and} and {@code or} are supported. Parentheses
 * are not currently supported (the spec allows them but most real-world queries
 * are flat conjunctions/disjunctions).
 *
 * <p>Example queries:
 * <pre>
 *   dc:title contains "love"
 *   upnp:class derivedfrom "object.item.audioItem" and dc:creator = "Beatles"
 *   dc:title contains "rock" or upnp:genre = "Rock"
 *   * (matches everything)
 * </pre>
 *
 * @since 1.0.0
 */
public final class SearchCriteria {

    private SearchCriteria() {
        // Utility class
    }

    /**
     * Parses a UPnP search criteria string into a {@link Predicate} that tests
     * {@link ContentItem} instances.
     *
     * @param criteria the search criteria string; may be {@code "*"} or empty to match all
     * @return a predicate that returns {@code true} for matching items
     * @throws NullPointerException if {@code criteria} is {@code null}
     * @since 1.0.0
     */
    public static Predicate<ContentItem> parse(String criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        String trimmed = criteria.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) {
            return item -> true;
        }
        return parseExpression(trimmed);
    }

    private static Predicate<ContentItem> parseExpression(String expr) {
        // Split on " or " first (lower precedence), then " and " (higher precedence)
        List<String> orParts = splitRespectingQuotes(expr, " or ");
        if (orParts.size() > 1) {
            Predicate<ContentItem> combined = item -> false;
            for (String part : orParts) {
                combined = combined.or(parseExpression(part.trim()));
            }
            return combined;
        }

        List<String> andParts = splitRespectingQuotes(expr, " and ");
        if (andParts.size() > 1) {
            Predicate<ContentItem> combined = item -> true;
            for (String part : andParts) {
                combined = combined.and(parseExpression(part.trim()));
            }
            return combined;
        }

        // Single comparison
        return parseComparison(expr.trim());
    }

    private static Predicate<ContentItem> parseComparison(String comparison) {
        // Try each operator in order
        for (String op : new String[]{"doesNotContain", "derivedfrom", "contains", "exists", "!=", "="}) {
            int idx = findOperator(comparison, op);
            if (idx >= 0) {
                String property = comparison.substring(0, idx).trim();
                String value = comparison.substring(idx + op.length()).trim();
                value = unquote(value);
                return buildPredicate(property, op, value);
            }
        }
        // Fallback: treat entire string as a title search term
        String term = unquote(comparison);
        return item -> item.getTitle().toLowerCase().contains(term.toLowerCase());
    }

    private static int findOperator(String expr, String op) {
        // Find operator outside of quoted strings
        boolean inQuote = false;
        String search = " " + op + " ";
        if ("exists".equals(op)) {
            search = " " + op + " ";
        }
        // For = and != we need exact boundary detection
        if ("=".equals(op)) {
            for (int i = 0; i < expr.length(); i++) {
                char c = expr.charAt(i);
                if (c == '"') inQuote = !inQuote;
                if (!inQuote && c == '=' && (i == 0 || expr.charAt(i - 1) != '!')) {
                    // Check it's surrounded by spaces
                    if ((i > 0 && expr.charAt(i - 1) == ' ') || i == 0) {
                        return i;
                    }
                }
            }
            return -1;
        }
        if ("!=".equals(op)) {
            int i = expr.indexOf("!=");
            if (i > 0) return i;
            return -1;
        }
        int i = expr.toLowerCase().indexOf(" " + op.toLowerCase() + " ");
        if (i >= 0) return i + 1; // skip the leading space
        // Also try at start of string for "exists"
        if (expr.toLowerCase().startsWith(op.toLowerCase() + " ")) {
            return 0;
        }
        return -1;
    }

    private static Predicate<ContentItem> buildPredicate(String property, String op, String value) {
        return switch (op) {
            case "contains" -> item -> {
                String propValue = getPropertyValue(item, property);
                return propValue != null && propValue.toLowerCase().contains(value.toLowerCase());
            };
            case "doesNotContain" -> item -> {
                String propValue = getPropertyValue(item, property);
                return propValue == null || !propValue.toLowerCase().contains(value.toLowerCase());
            };
            case "=" -> item -> {
                String propValue = getPropertyValue(item, property);
                return value.equalsIgnoreCase(propValue);
            };
            case "!=" -> item -> {
                String propValue = getPropertyValue(item, property);
                return !value.equalsIgnoreCase(propValue);
            };
            case "derivedfrom" -> item -> {
                String propValue = getPropertyValue(item, property);
                return propValue != null && propValue.startsWith(value);
            };
            case "exists" -> {
                boolean shouldExist = "true".equalsIgnoreCase(value);
                yield item -> {
                    String propValue = getPropertyValue(item, property);
                    boolean exists = propValue != null && !propValue.isEmpty();
                    return exists == shouldExist;
                };
            }
            default -> item -> true;
        };
    }

    /**
     * Extracts a property value from a {@link ContentItem} by UPnP property name.
     *
     * @param item     the content item
     * @param property the property name (e.g. "dc:title", "upnp:class", "dc:creator")
     * @return the property value, or {@code null} if not set
     * @since 1.0.0
     */
    static String getPropertyValue(ContentItem item, String property) {
        // Strip namespace prefix for matching
        String prop = property.contains(":") ? property.substring(property.indexOf(':') + 1) : property;
        return switch (prop.toLowerCase()) {
            case "title" -> item.getTitle();
            case "creator" -> item.getCreator();
            case "class" -> item.getType() != null ? item.getType().upnpClass() : null;
            case "genre" -> item.getGenre();
            case "date" -> item.getDate();
            case "albumarturi" -> item.getAlbumArtUri();
            default -> null;
        };
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static List<String> splitRespectingQuotes(String input, String separator) {
        List<String> parts = new ArrayList<>();
        String lower = input.toLowerCase();
        String lowerSep = separator.toLowerCase();
        int start = 0;
        boolean inQuote = false;

        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '"') {
                inQuote = !inQuote;
            }
            if (!inQuote && i + lowerSep.length() <= lower.length()
                    && lower.substring(i, i + lowerSep.length()).equals(lowerSep)) {
                parts.add(input.substring(start, i));
                start = i + separator.length();
                i = start - 1; // will be incremented by loop
            }
        }
        parts.add(input.substring(start));
        return parts;
    }
}
