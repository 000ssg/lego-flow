package ssg.legoflow.rpc.graphql.transport;

import java.util.*;

/**
 * Minimal JSON encoder/decoder for GraphQL transport.
 *
 * <p>Handles the JSON types needed for GraphQL: objects, arrays, strings,
 * numbers, booleans, and null. No external dependencies.
 *
 * @since 1.0.0
 */
public final class JsonCodec {

    private JsonCodec() {}

    /**
     * Encodes a Java object as a JSON string.
     *
     * @param value the value to encode
     * @return the JSON string
     */
    public static String encode(Object value) {
        var sb = new StringBuilder();
        encodeValue(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void encodeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    case '\b' -> sb.append("\\b");
                    case '\f' -> sb.append("\\f");
                    default -> {
                        if (c < 0x20) {
                            sb.append("\\u").append(String.format("%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            sb.append('"');
        } else if (value instanceof Number) {
            if (value instanceof Double d) {
                if (d.isInfinite() || d.isNaN()) {
                    sb.append("null");
                } else if (d == Math.floor(d) && !Double.isInfinite(d) && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
                    sb.append((long) d.doubleValue());
                } else {
                    sb.append(d);
                }
            } else if (value instanceof Float f) {
                if (f.isInfinite() || f.isNaN()) {
                    sb.append("null");
                } else {
                    sb.append(f);
                }
            } else {
                sb.append(value);
            }
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            var it = map.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                encodeValue(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                encodeValue(sb, entry.getValue());
                if (it.hasNext()) sb.append(',');
            }
            sb.append('}');
        } else if (value instanceof Collection<?> coll) {
            sb.append('[');
            var it = coll.iterator();
            while (it.hasNext()) {
                encodeValue(sb, it.next());
                if (it.hasNext()) sb.append(',');
            }
            sb.append(']');
        } else if (value instanceof Object[] arr) {
            sb.append('[');
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(',');
                encodeValue(sb, arr[i]);
            }
            sb.append(']');
        } else {
            encodeValue(sb, value.toString());
        }
    }

    /**
     * Decodes a JSON string into a Java object.
     *
     * @param json the JSON string
     * @return the decoded object (Map, List, String, Number, Boolean, or null)
     */
    public static Object decode(String json) {
        if (json == null || json.isBlank()) return null;
        var parser = new JsonParser(json.trim());
        return parser.parseValue();
    }

    /**
     * Decodes a JSON string into a Map.
     *
     * @param json the JSON string
     * @return the decoded map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> decodeObject(String json) {
        var result = decode(json);
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static final class JsonParser {
        private final String source;
        private int pos;

        JsonParser(String source) {
            this.source = source;
            this.pos = 0;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= source.length()) return null;
            char c = source.charAt(pos);
            return switch (c) {
                case '"' -> parseString();
                case '{' -> parseObject();
                case '[' -> parseArray();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private String parseString() {
            pos++; // skip opening "
            var sb = new StringBuilder();
            while (pos < source.length()) {
                char c = source.charAt(pos);
                if (c == '"') {
                    pos++;
                    return sb.toString();
                }
                if (c == '\\') {
                    pos++;
                    if (pos >= source.length()) break;
                    char escaped = source.charAt(pos);
                    switch (escaped) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            pos++;
                            var hex = source.substring(pos, Math.min(pos + 4, source.length()));
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 3; // advance 3, the 4th done below
                        }
                        default -> sb.append(escaped);
                    }
                } else {
                    sb.append(c);
                }
                pos++;
            }
            return sb.toString();
        }

        private Map<String, Object> parseObject() {
            pos++; // skip {
            var map = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (pos < source.length() && source.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < source.length()) {
                skipWhitespace();
                var key = parseString();
                skipWhitespace();
                if (pos < source.length() && source.charAt(pos) == ':') pos++;
                skipWhitespace();
                var value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (pos < source.length() && source.charAt(pos) == ',') {
                    pos++;
                } else {
                    break;
                }
            }
            skipWhitespace();
            if (pos < source.length() && source.charAt(pos) == '}') pos++;
            return map;
        }

        private List<Object> parseArray() {
            pos++; // skip [
            var list = new ArrayList<>();
            skipWhitespace();
            if (pos < source.length() && source.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < source.length()) {
                skipWhitespace();
                list.add(parseValue());
                skipWhitespace();
                if (pos < source.length() && source.charAt(pos) == ',') {
                    pos++;
                } else {
                    break;
                }
            }
            skipWhitespace();
            if (pos < source.length() && source.charAt(pos) == ']') pos++;
            return list;
        }

        private Number parseNumber() {
            int start = pos;
            if (pos < source.length() && source.charAt(pos) == '-') pos++;
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) pos++;
            boolean isFloat = false;
            if (pos < source.length() && source.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < source.length() && Character.isDigit(source.charAt(pos))) pos++;
            }
            if (pos < source.length() && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < source.length() && (source.charAt(pos) == '+' || source.charAt(pos) == '-')) pos++;
                while (pos < source.length() && Character.isDigit(source.charAt(pos))) pos++;
            }
            var numStr = source.substring(start, pos);
            if (isFloat) {
                return Double.parseDouble(numStr);
            }
            try {
                return Integer.parseInt(numStr);
            } catch (NumberFormatException e) {
                return Long.parseLong(numStr);
            }
        }

        private Boolean parseBoolean() {
            if (source.startsWith("true", pos)) {
                pos += 4;
                return true;
            }
            if (source.startsWith("false", pos)) {
                pos += 5;
                return false;
            }
            throw new IllegalArgumentException("Expected boolean at " + pos);
        }

        private Object parseNull() {
            if (source.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Expected null at " + pos);
        }

        private void skipWhitespace() {
            while (pos < source.length()) {
                char c = source.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }
    }
}
