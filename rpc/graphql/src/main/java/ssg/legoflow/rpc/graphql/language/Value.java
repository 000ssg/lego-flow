package ssg.legoflow.rpc.graphql.language;

import java.util.List;
import java.util.Map;

/**
 * Sealed interface representing literal values in GraphQL queries.
 *
 * @since 1.0.0
 */
public sealed interface Value {

    /**
     * Returns the Java object representation of this value.
     *
     * @return the value
     */
    Object toJava();

    /** Integer value. */
    record IntValue(int value) implements Value {
        @Override public Object toJava() { return value; }
        @Override public String toString() { return String.valueOf(value); }
    }

    /** Float value. */
    record FloatValue(double value) implements Value {
        @Override public Object toJava() { return value; }
        @Override public String toString() { return String.valueOf(value); }
    }

    /** String value. */
    record StringValue(String value) implements Value {
        @Override public Object toJava() { return value; }
        @Override public String toString() { return "\"" + value + "\""; }
    }

    /** Boolean value. */
    record BooleanValue(boolean value) implements Value {
        @Override public Object toJava() { return value; }
        @Override public String toString() { return String.valueOf(value); }
    }

    /** Null value. */
    record NullValue() implements Value {
        @Override public Object toJava() { return null; }
        @Override public String toString() { return "null"; }
    }

    /** Enum value (unquoted name). */
    record EnumValue(String value) implements Value {
        @Override public Object toJava() { return value; }
        @Override public String toString() { return value; }
    }

    /** List value. */
    record ListValue(List<Value> values) implements Value {
        @Override
        public Object toJava() {
            return values.stream().map(Value::toJava).toList();
        }
        @Override public String toString() { return values.toString(); }
    }

    /** Object value (input object literal). */
    record ObjectValue(Map<String, Value> fields) implements Value {
        @Override
        public Object toJava() {
            var map = new java.util.LinkedHashMap<String, Object>();
            fields.forEach((k, v) -> map.put(k, v.toJava()));
            return map;
        }
        @Override public String toString() { return fields.toString(); }
    }

    /** Variable reference ($name). */
    record VariableValue(String name) implements Value {
        @Override public Object toJava() { return "$" + name; }
        @Override public String toString() { return "$" + name; }
    }
}
