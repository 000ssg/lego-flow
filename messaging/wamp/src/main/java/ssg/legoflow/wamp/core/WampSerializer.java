package ssg.legoflow.wamp.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple JSON serializer/deserializer for WAMP messages.
 * Converts between {@link WampMessage} instances and their JSON array representation
 * as specified by the WAMP protocol (e.g. {@code [type_code, ...fields]}).
 *
 * <p>This implementation handles basic JSON types: strings, numbers, booleans,
 * null, arrays, and objects. It does not depend on external JSON libraries.</p>
 *
 * @since 1.0.0
 */
public class WampSerializer {

    /**
     * Serializes a WAMP message to its JSON array representation.
     *
     * @param msg the message to serialize
     * @return the JSON string
     * @throws IllegalArgumentException if the message type is unsupported
     */
    public String serialize(WampMessage msg) {
        var sb = new StringBuilder();
        sb.append('[');
        sb.append(msg.type().code());
        switch (msg) {
            case WampMessage.Hello m -> {
                sb.append(',').append(quote(m.realm()));
                sb.append(',').append(toJson(m.details()));
            }
            case WampMessage.Welcome m -> {
                sb.append(',').append(m.sessionId());
                sb.append(',').append(toJson(m.details()));
            }
            case WampMessage.Abort m -> {
                sb.append(',').append(toJson(m.details()));
                sb.append(',').append(quote(m.reason()));
            }
            case WampMessage.Challenge m -> {
                sb.append(',').append(quote(m.authMethod()));
                sb.append(',').append(toJson(m.extra()));
            }
            case WampMessage.Authenticate m -> {
                sb.append(',').append(quote(m.signature()));
                sb.append(',').append(toJson(m.extra()));
            }
            case WampMessage.Goodbye m -> {
                sb.append(',').append(toJson(m.details()));
                sb.append(',').append(quote(m.reason()));
            }
            case WampMessage.Error m -> {
                sb.append(',').append(m.requestType());
                sb.append(',').append(m.requestId());
                sb.append(',').append(toJson(m.details()));
                sb.append(',').append(quote(m.error()));
            }
            case WampMessage.Publish m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(toJson(m.options()));
                sb.append(',').append(quote(m.topic()));
                if (m.args() != null && !m.args().isEmpty()) {
                    sb.append(',').append(toJson(m.args()));
                }
            }
            case WampMessage.Published m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(m.publicationId());
            }
            case WampMessage.Subscribe m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(toJson(m.options()));
                sb.append(',').append(quote(m.topic()));
            }
            case WampMessage.Subscribed m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(m.subscriptionId());
            }
            case WampMessage.Unsubscribe m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(m.subscriptionId());
            }
            case WampMessage.Unsubscribed m -> {
                sb.append(',').append(m.requestId());
            }
            case WampMessage.Event m -> {
                sb.append(',').append(m.subscriptionId());
                sb.append(',').append(m.publicationId());
                sb.append(',').append(toJson(m.details()));
                if (m.args() != null && !m.args().isEmpty()) {
                    sb.append(',').append(toJson(m.args()));
                }
            }
            case WampMessage.Call m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(toJson(m.options()));
                sb.append(',').append(quote(m.procedure()));
                if (m.args() != null && !m.args().isEmpty()) {
                    sb.append(',').append(toJson(m.args()));
                }
            }
            case WampMessage.Cancel m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(toJson(m.options()));
            }
            case WampMessage.Result m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(toJson(m.details()));
                if (m.args() != null && !m.args().isEmpty()) {
                    sb.append(',').append(toJson(m.args()));
                }
            }
            case WampMessage.Register m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(toJson(m.options()));
                sb.append(',').append(quote(m.procedure()));
            }
            case WampMessage.Registered m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(m.registrationId());
            }
            case WampMessage.Unregister m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(m.registrationId());
            }
            case WampMessage.Unregistered m -> {
                sb.append(',').append(m.requestId());
            }
            case WampMessage.Invocation m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(m.registrationId());
                sb.append(',').append(toJson(m.details()));
                if (m.args() != null && !m.args().isEmpty()) {
                    sb.append(',').append(toJson(m.args()));
                }
            }
            case WampMessage.Interrupt m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(toJson(m.options()));
            }
            case WampMessage.Yield m -> {
                sb.append(',').append(m.requestId());
                sb.append(',').append(toJson(m.options()));
                if (m.args() != null && !m.args().isEmpty()) {
                    sb.append(',').append(toJson(m.args()));
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Deserializes a JSON array string into a WAMP message.
     *
     * @param json the JSON string to deserialize
     * @return the parsed WAMP message
     * @throws IllegalArgumentException if the JSON is malformed or the type is unknown
     */
    @SuppressWarnings("unchecked")
    public WampMessage deserialize(String json) {
        var array = (List<Object>) parseJson(json.trim());
        int typeCode = ((Number) array.get(0)).intValue();
        var type = WampMessageType.fromCode(typeCode);
        return switch (type) {
            case HELLO -> new WampMessage.Hello(
                    (String) array.get(1),
                    asMap(array.get(2)));
            case WELCOME -> new WampMessage.Welcome(
                    asLong(array.get(1)),
                    asMap(array.get(2)));
            case ABORT -> new WampMessage.Abort(
                    asMap(array.get(1)),
                    (String) array.get(2));
            case CHALLENGE -> new WampMessage.Challenge(
                    (String) array.get(1),
                    asMap(array.get(2)));
            case AUTHENTICATE -> new WampMessage.Authenticate(
                    (String) array.get(1),
                    asMap(array.get(2)));
            case GOODBYE -> new WampMessage.Goodbye(
                    asMap(array.get(1)),
                    (String) array.get(2));
            case ERROR -> new WampMessage.Error(
                    ((Number) array.get(1)).intValue(),
                    asLong(array.get(2)),
                    asMap(array.get(3)),
                    (String) array.get(4));
            case PUBLISH -> new WampMessage.Publish(
                    asLong(array.get(1)),
                    asMap(array.get(2)),
                    (String) array.get(3),
                    array.size() > 4 ? asList(array.get(4)) : List.of());
            case PUBLISHED -> new WampMessage.Published(
                    asLong(array.get(1)),
                    asLong(array.get(2)));
            case SUBSCRIBE -> new WampMessage.Subscribe(
                    asLong(array.get(1)),
                    asMap(array.get(2)),
                    (String) array.get(3));
            case SUBSCRIBED -> new WampMessage.Subscribed(
                    asLong(array.get(1)),
                    asLong(array.get(2)));
            case UNSUBSCRIBE -> new WampMessage.Unsubscribe(
                    asLong(array.get(1)),
                    asLong(array.get(2)));
            case UNSUBSCRIBED -> new WampMessage.Unsubscribed(
                    asLong(array.get(1)));
            case EVENT -> new WampMessage.Event(
                    asLong(array.get(1)),
                    asLong(array.get(2)),
                    asMap(array.get(3)),
                    array.size() > 4 ? asList(array.get(4)) : List.of());
            case CALL -> new WampMessage.Call(
                    asLong(array.get(1)),
                    asMap(array.get(2)),
                    (String) array.get(3),
                    array.size() > 4 ? asList(array.get(4)) : List.of());
            case CANCEL -> new WampMessage.Cancel(
                    asLong(array.get(1)),
                    asMap(array.get(2)));
            case RESULT -> new WampMessage.Result(
                    asLong(array.get(1)),
                    asMap(array.get(2)),
                    array.size() > 3 ? asList(array.get(3)) : List.of());
            case REGISTER -> new WampMessage.Register(
                    asLong(array.get(1)),
                    asMap(array.get(2)),
                    (String) array.get(3));
            case REGISTERED -> new WampMessage.Registered(
                    asLong(array.get(1)),
                    asLong(array.get(2)));
            case UNREGISTER -> new WampMessage.Unregister(
                    asLong(array.get(1)),
                    asLong(array.get(2)));
            case UNREGISTERED -> new WampMessage.Unregistered(
                    asLong(array.get(1)));
            case INVOCATION -> new WampMessage.Invocation(
                    asLong(array.get(1)),
                    asLong(array.get(2)),
                    asMap(array.get(3)),
                    array.size() > 4 ? asList(array.get(4)) : List.of());
            case INTERRUPT -> new WampMessage.Interrupt(
                    asLong(array.get(1)),
                    asMap(array.get(2)));
            case YIELD -> new WampMessage.Yield(
                    asLong(array.get(1)),
                    asMap(array.get(2)),
                    array.size() > 3 ? asList(array.get(3)) : List.of());
        };
    }

    // --- JSON generation helpers ---

    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String s) return quote(s);
        if (obj instanceof Number n) return n.toString();
        if (obj instanceof Boolean b) return b.toString();
        if (obj instanceof Map<?, ?> map) {
            var sb = new StringBuilder("{");
            var first = true;
            for (var entry : map.entrySet()) {
                if (!first) sb.append(',');
                sb.append(quote(entry.getKey().toString()));
                sb.append(':');
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append('}');
            return sb.toString();
        }
        if (obj instanceof List<?> list) {
            var sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(toJson(list.get(i)));
            }
            sb.append(']');
            return sb.toString();
        }
        return quote(obj.toString());
    }

    private String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // --- Minimal JSON parser ---

    private int pos;
    private String input;

    private synchronized Object parseJson(String json) {
        this.input = json;
        this.pos = 0;
        var result = parseValue();
        return result;
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= input.length()) throw new IllegalArgumentException("Unexpected end of JSON");
        char c = input.charAt(pos);
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
        pos++; // skip opening quote
        var sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\\') {
                pos++;
                char escaped = input.charAt(pos);
                sb.append(switch (escaped) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    default -> escaped;
                });
            } else if (c == '"') {
                pos++;
                return sb.toString();
            } else {
                sb.append(c);
            }
            pos++;
        }
        throw new IllegalArgumentException("Unterminated string");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject() {
        pos++; // skip {
        var map = new HashMap<String, Object>();
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == '}') { pos++; return map; }
        while (pos < input.length()) {
            skipWhitespace();
            var key = parseString();
            skipWhitespace();
            pos++; // skip :
            var value = parseValue();
            map.put(key, value);
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ',') { pos++; }
            else break;
        }
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == '}') pos++;
        return map;
    }

    private List<Object> parseArray() {
        pos++; // skip [
        var list = new ArrayList<Object>();
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == ']') { pos++; return list; }
        while (pos < input.length()) {
            list.add(parseValue());
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ',') { pos++; }
            else break;
        }
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == ']') pos++;
        return list;
    }

    private Number parseNumber() {
        int start = pos;
        if (pos < input.length() && input.charAt(pos) == '-') pos++;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        boolean isDouble = false;
        if (pos < input.length() && input.charAt(pos) == '.') {
            isDouble = true;
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        }
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            isDouble = true;
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        }
        var numStr = input.substring(start, pos);
        if (isDouble) return Double.parseDouble(numStr);
        long val = Long.parseLong(numStr);
        if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) return (int) val;
        return val;
    }

    private Boolean parseBoolean() {
        if (input.startsWith("true", pos)) { pos += 4; return true; }
        if (input.startsWith("false", pos)) { pos += 5; return false; }
        throw new IllegalArgumentException("Expected boolean at pos " + pos);
    }

    private Object parseNull() {
        if (input.startsWith("null", pos)) { pos += 4; return null; }
        throw new IllegalArgumentException("Expected null at pos " + pos);
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }

    // --- Type coercion helpers ---

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object obj) {
        if (obj == null) return Map.of();
        return (Map<String, Object>) obj;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object obj) {
        if (obj == null) return List.of();
        return (List<Object>) obj;
    }

    private static long asLong(Object obj) {
        return ((Number) obj).longValue();
    }
}
