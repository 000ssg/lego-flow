package ssg.legoflow.rpc.graphql.schema;

import java.util.Objects;

/**
 * Defines an argument on a field or directive.
 *
 * <p>An argument has a name, a type (must be an input type), an optional
 * description, and an optional default value.
 *
 * @since 0.1.0
 */
public final class ArgumentDefinition {

    private final String name;
    private final String description;
    private final GraphQLType type;
    private final Object defaultValue;
    private final boolean hasDefaultValue;

    /**
     * Creates a new argument definition.
     *
     * @param name            the argument name
     * @param description     the argument description
     * @param type            the argument type (must be an input type)
     * @param defaultValue    the default value, or null
     * @param hasDefaultValue whether a default value is provided
     */
    public ArgumentDefinition(String name, String description, GraphQLType type,
                              Object defaultValue, boolean hasDefaultValue) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.type = Objects.requireNonNull(type);
        this.defaultValue = defaultValue;
        this.hasDefaultValue = hasDefaultValue;
    }

    /**
     * Creates a required argument with no default value.
     *
     * @param name the argument name
     * @param type the argument type
     * @return a new argument definition
     */
    public static ArgumentDefinition of(String name, GraphQLType type) {
        return new ArgumentDefinition(name, null, type, null, false);
    }

    /**
     * Creates an argument with a default value.
     *
     * @param name         the argument name
     * @param type         the argument type
     * @param defaultValue the default value
     * @return a new argument definition
     */
    public static ArgumentDefinition of(String name, GraphQLType type, Object defaultValue) {
        return new ArgumentDefinition(name, null, type, defaultValue, true);
    }

    /**
     * Creates an argument with description and default value.
     *
     * @param name         the argument name
     * @param description  the argument description
     * @param type         the argument type
     * @param defaultValue the default value
     * @return a new argument definition
     */
    public static ArgumentDefinition of(String name, String description,
                                        GraphQLType type, Object defaultValue) {
        return new ArgumentDefinition(name, description, type, defaultValue, true);
    }

    public String name() { return name; }
    public String description() { return description; }
    public GraphQLType type() { return type; }
    public Object defaultValue() { return defaultValue; }
    public boolean hasDefaultValue() { return hasDefaultValue; }

    @Override
    public String toString() {
        var sb = new StringBuilder(name).append(": ").append(type);
        if (hasDefaultValue) sb.append(" = ").append(defaultValue);
        return sb.toString();
    }
}
