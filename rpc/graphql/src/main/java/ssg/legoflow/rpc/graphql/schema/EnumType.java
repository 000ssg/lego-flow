package ssg.legoflow.rpc.graphql.schema;

import java.util.*;

/**
 * Represents a GraphQL enum type.
 *
 * <p>Enum types describe a set of possible values. Like scalars, enum types
 * represent leaf values in a GraphQL type system.
 *
 * @since 1.0.0
 */
public final class EnumType implements GraphQLType {

    private final String name;
    private final String description;
    private final List<EnumValue> values;
    private final Map<String, EnumValue> valuesByName;

    /**
     * Creates a new enum type.
     *
     * @param name        the enum name
     * @param description the enum description
     * @param values      the enum values
     */
    public EnumType(String name, String description, List<EnumValue> values) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.values = values != null ? List.copyOf(values) : List.of();
        var byName = new LinkedHashMap<String, EnumValue>();
        for (var v : this.values) {
            byName.put(v.name(), v);
        }
        this.valuesByName = Collections.unmodifiableMap(byName);
    }

    /**
     * Creates a simple enum type from value names.
     *
     * @param name       the enum name
     * @param valueNames the value names
     * @return a new enum type
     */
    public static EnumType of(String name, String... valueNames) {
        var values = new ArrayList<EnumValue>();
        for (var vn : valueNames) {
            values.add(new EnumValue(vn, null, false, null));
        }
        return new EnumType(name, null, values);
    }

    /**
     * Creates an enum type with EnumValue instances.
     *
     * @param name   the enum name
     * @param values the enum values
     * @return a new enum type
     */
    public static EnumType of(String name, List<EnumValue> values) {
        return new EnumType(name, null, values);
    }

    @Override
    public String name() { return name; }
    public String description() { return description; }
    public List<EnumValue> values() { return values; }

    /**
     * Returns the enum value with the given name.
     *
     * @param name the value name
     * @return the enum value, or null
     */
    public EnumValue getValue(String name) { return valuesByName.get(name); }

    /**
     * Returns whether the given value name is valid for this enum.
     *
     * @param name the value name
     * @return true if valid
     */
    public boolean isValidValue(String name) { return valuesByName.containsKey(name); }

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof EnumType t && name.equals(t.name));
    }

    @Override
    public int hashCode() { return name.hashCode(); }

    /**
     * Represents a single value within an enum type.
     *
     * @param name              the value name
     * @param description       the value description
     * @param deprecated        whether the value is deprecated
     * @param deprecationReason the deprecation reason
     * @since 1.0.0
     */
    public record EnumValue(String name, String description,
                            boolean deprecated, String deprecationReason) {

        /**
         * Creates a simple non-deprecated enum value.
         *
         * @param name the value name
         * @return a new enum value
         */
        public static EnumValue of(String name) {
            return new EnumValue(name, null, false, null);
        }

        /**
         * Creates a deprecated enum value.
         *
         * @param name   the value name
         * @param reason the deprecation reason
         * @return a new enum value
         */
        public static EnumValue deprecated(String name, String reason) {
            return new EnumValue(name, null, true, reason);
        }
    }
}
