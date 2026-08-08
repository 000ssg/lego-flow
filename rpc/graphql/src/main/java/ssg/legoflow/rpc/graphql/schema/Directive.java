package ssg.legoflow.rpc.graphql.schema;

import java.util.*;

/**
 * Represents a GraphQL directive definition.
 *
 * <p>Directives provide a way to describe alternate runtime execution and
 * type validation behavior. The built-in directives are @skip, @include,
 * and @deprecated.
 *
 * @since 0.1.0
 */
public final class Directive {

    /**
     * Locations where directives can be applied.
     */
    public enum Location {
        // Executable
        QUERY, MUTATION, SUBSCRIPTION, FIELD, FRAGMENT_DEFINITION,
        FRAGMENT_SPREAD, INLINE_FRAGMENT,
        // Type system
        SCHEMA, SCALAR, OBJECT, FIELD_DEFINITION, ARGUMENT_DEFINITION,
        INTERFACE, UNION, ENUM, ENUM_VALUE, INPUT_OBJECT, INPUT_FIELD_DEFINITION
    }

    /** Built-in @skip directive. */
    public static final Directive SKIP = new Directive("skip",
            "Directs the executor to skip this field or fragment when the `if` argument is true.",
            List.of(ArgumentDefinition.of("if", NonNullType.of(ScalarType.BOOLEAN))),
            Set.of(Location.FIELD, Location.FRAGMENT_SPREAD, Location.INLINE_FRAGMENT));

    /** Built-in @include directive. */
    public static final Directive INCLUDE = new Directive("include",
            "Directs the executor to include this field or fragment only when the `if` argument is true.",
            List.of(ArgumentDefinition.of("if", NonNullType.of(ScalarType.BOOLEAN))),
            Set.of(Location.FIELD, Location.FRAGMENT_SPREAD, Location.INLINE_FRAGMENT));

    /** Built-in @deprecated directive. */
    public static final Directive DEPRECATED = new Directive("deprecated",
            "Marks an element of a GraphQL schema as no longer supported.",
            List.of(ArgumentDefinition.of("reason", ScalarType.STRING, "No longer supported")),
            Set.of(Location.FIELD_DEFINITION, Location.ENUM_VALUE));

    private final String name;
    private final String description;
    private final List<ArgumentDefinition> arguments;
    private final Set<Location> locations;

    /**
     * Creates a new directive definition.
     *
     * @param name        the directive name (without @)
     * @param description the directive description
     * @param arguments   the directive arguments
     * @param locations   the valid locations for this directive
     */
    public Directive(String name, String description,
                     List<ArgumentDefinition> arguments,
                     Set<Location> locations) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.arguments = arguments != null ? List.copyOf(arguments) : List.of();
        this.locations = locations != null ? Set.copyOf(locations) : Set.of();
    }

    public String name() { return name; }
    public String description() { return description; }
    public List<ArgumentDefinition> arguments() { return arguments; }
    public Set<Location> locations() { return locations; }

    /**
     * Returns the argument definition with the given name.
     *
     * @param name the argument name
     * @return the argument definition, or null
     */
    public ArgumentDefinition getArgument(String name) {
        for (var arg : arguments) {
            if (arg.name().equals(name)) return arg;
        }
        return null;
    }

    @Override
    public String toString() { return "@" + name; }

    /**
     * Represents the usage of a directive on an element.
     *
     * @param name      the directive name (without @)
     * @param arguments the argument values
     * @since 0.1.0
     */
    public record DirectiveUsage(String name, Map<String, Object> arguments) {

        /**
         * Creates a directive usage with no arguments.
         *
         * @param name the directive name
         * @return a new directive usage
         */
        public static DirectiveUsage of(String name) {
            return new DirectiveUsage(name, Map.of());
        }

        /**
         * Creates a directive usage with arguments.
         *
         * @param name the directive name
         * @param args the argument values
         * @return a new directive usage
         */
        public static DirectiveUsage of(String name, Map<String, Object> args) {
            return new DirectiveUsage(name, args != null ? Map.copyOf(args) : Map.of());
        }

        /**
         * Returns the value of the argument with the given name.
         *
         * @param name the argument name
         * @return the argument value, or null
         */
        public Object getArgument(String name) {
            return arguments.get(name);
        }
    }
}
