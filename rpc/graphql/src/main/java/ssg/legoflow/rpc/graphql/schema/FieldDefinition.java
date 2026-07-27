package ssg.legoflow.rpc.graphql.schema;

import ssg.legoflow.rpc.graphql.execution.DataFetcher;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Defines a field on an object or interface type.
 *
 * <p>A field has a name, a return type, optional arguments, an optional
 * description, deprecation info, and a resolver (data fetcher).
 *
 * @since 1.0.0
 */
public final class FieldDefinition {

    private final String name;
    private final String description;
    private final GraphQLType type;
    private final List<ArgumentDefinition> arguments;
    private final boolean deprecated;
    private final String deprecationReason;
    private DataFetcher<?> dataFetcher;

    /**
     * Creates a new field definition.
     *
     * @param name               the field name
     * @param description        the field description
     * @param type               the field return type
     * @param arguments          the field arguments
     * @param deprecated         whether the field is deprecated
     * @param deprecationReason  the deprecation reason
     */
    public FieldDefinition(String name, String description, GraphQLType type,
                           List<ArgumentDefinition> arguments,
                           boolean deprecated, String deprecationReason) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.type = Objects.requireNonNull(type);
        this.arguments = arguments != null ? List.copyOf(arguments) : List.of();
        this.deprecated = deprecated;
        this.deprecationReason = deprecationReason;
    }

    /**
     * Creates a simple field definition with no arguments.
     *
     * @param name the field name
     * @param type the field return type
     * @return a new field definition
     */
    public static FieldDefinition of(String name, GraphQLType type) {
        return new FieldDefinition(name, null, type, List.of(), false, null);
    }

    /**
     * Creates a field definition with arguments.
     *
     * @param name      the field name
     * @param type      the field return type
     * @param arguments the field arguments
     * @return a new field definition
     */
    public static FieldDefinition of(String name, GraphQLType type, List<ArgumentDefinition> arguments) {
        return new FieldDefinition(name, null, type, arguments, false, null);
    }

    /**
     * Creates a field definition with a description.
     *
     * @param name        the field name
     * @param description the field description
     * @param type        the field return type
     * @return a new field definition
     */
    public static FieldDefinition of(String name, String description, GraphQLType type) {
        return new FieldDefinition(name, description, type, List.of(), false, null);
    }

    public String name() { return name; }
    public String description() { return description; }
    public GraphQLType type() { return type; }
    public List<ArgumentDefinition> arguments() { return arguments; }
    public boolean isDeprecated() { return deprecated; }
    public String deprecationReason() { return deprecationReason; }

    /**
     * Returns the argument definition with the given name.
     *
     * @param name the argument name
     * @return the argument definition, or null if not found
     */
    public ArgumentDefinition getArgument(String name) {
        for (var arg : arguments) {
            if (arg.name().equals(name)) return arg;
        }
        return null;
    }

    /**
     * Returns the data fetcher (resolver) for this field.
     *
     * @return the data fetcher, or null
     */
    public DataFetcher<?> dataFetcher() { return dataFetcher; }

    /**
     * Sets the data fetcher (resolver) for this field.
     *
     * @param dataFetcher the data fetcher
     * @return this field definition for chaining
     */
    public FieldDefinition dataFetcher(DataFetcher<?> dataFetcher) {
        this.dataFetcher = dataFetcher;
        return this;
    }

    @Override
    public String toString() {
        return name + ": " + type;
    }
}
