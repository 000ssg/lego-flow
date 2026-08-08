package ssg.legoflow.rpc.graphql.schema;

import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a GraphQL scalar type.
 *
 * <p>Scalars represent leaf values in a GraphQL type system. The built-in
 * scalars are Int, Float, String, Boolean, and ID. Custom scalars can be
 * defined with serialization and parsing functions.
 *
 * @since 0.1.0
 */
public final class ScalarType implements GraphQLType {

    /** Built-in Int scalar: signed 32-bit integer. */
    public static final ScalarType INT = new ScalarType("Int", "Built-in Int scalar",
            ScalarType::coerceInt, ScalarType::parseIntLiteral);

    /** Built-in Float scalar: double-precision floating-point. */
    public static final ScalarType FLOAT = new ScalarType("Float", "Built-in Float scalar",
            ScalarType::coerceFloat, ScalarType::parseFloatLiteral);

    /** Built-in String scalar: UTF-8 character sequence. */
    public static final ScalarType STRING = new ScalarType("String", "Built-in String scalar",
            v -> v instanceof String s ? s : String.valueOf(v),
            v -> v instanceof String s ? s : null);

    /** Built-in Boolean scalar: true or false. */
    public static final ScalarType BOOLEAN = new ScalarType("Boolean", "Built-in Boolean scalar",
            ScalarType::coerceBoolean, ScalarType::parseBooleanLiteral);

    /** Built-in ID scalar: unique identifier serialized as String. */
    public static final ScalarType ID = new ScalarType("ID", "Built-in ID scalar",
            v -> String.valueOf(v),
            v -> v instanceof String s ? s : v instanceof Number n ? String.valueOf(n.longValue()) : null);

    private final String name;
    private final String description;
    private final Function<Object, Object> serializer;
    private final Function<Object, Object> parser;

    /**
     * Creates a new scalar type.
     *
     * @param name        the scalar type name
     * @param description the scalar type description
     * @param serializer  function to serialize a value for output
     * @param parser      function to parse an input literal value
     */
    public ScalarType(String name, String description,
                      Function<Object, Object> serializer,
                      Function<Object, Object> parser) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.serializer = Objects.requireNonNull(serializer);
        this.parser = Objects.requireNonNull(parser);
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the scalar description.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    /**
     * Serializes a result value for output.
     *
     * @param value the value to serialize
     * @return the serialized value
     */
    public Object serialize(Object value) {
        return serializer.apply(value);
    }

    /**
     * Parses an input literal value.
     *
     * @param value the literal value to parse
     * @return the parsed value, or null if invalid
     */
    public Object parseLiteral(Object value) {
        return parser.apply(value);
    }

    /**
     * Returns whether this is a built-in scalar.
     *
     * @return true if built-in
     */
    public boolean isBuiltIn() {
        return this == INT || this == FLOAT || this == STRING || this == BOOLEAN || this == ID;
    }

    private static Object coerceInt(Object v) {
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return (int) (long) l;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static Object parseIntLiteral(Object v) {
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) {
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) (long) l;
            return null;
        }
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static Object coerceFloat(Object v) {
        if (v instanceof Double d) return d;
        if (v instanceof Float f) return (double) f;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static Object parseFloatLiteral(Object v) {
        if (v instanceof Double d) return d;
        if (v instanceof Float f) return (double) f;
        if (v instanceof Integer i) return (double) i;
        if (v instanceof Long l) return (double) l;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static Object coerceBoolean(Object v) {
        if (v instanceof Boolean b) return b;
        return null;
    }

    private static Object parseBooleanLiteral(Object v) {
        if (v instanceof Boolean b) return b;
        return null;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ScalarType s && name.equals(s.name));
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
