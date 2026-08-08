package ssg.legoflow.rpc.graphql.language;

/**
 * Represents a variable definition in a GraphQL operation.
 *
 * <p>Variables allow parameterizing GraphQL queries with external values.
 * Example: {@code $id: ID!} or {@code $limit: Int = 10}.
 *
 * @param name         the variable name (without $)
 * @param typeName     the type name as written in the query
 * @param defaultValue the default value, or null
 * @since 0.1.0
 */
public record VariableDefinition(String name, TypeReference typeName, Value defaultValue) {

    /**
     * Represents a type reference in the query language.
     * Can be a named type, list type, or non-null type.
     *
     * @since 0.1.0
     */
    public sealed interface TypeReference {
        /**
         * A named type reference (e.g., "String", "User").
         */
        record NamedType(String name) implements TypeReference {
            @Override public String toString() { return name; }
        }

        /**
         * A list type reference (e.g., "[String]").
         */
        record ListTypeRef(TypeReference elementType) implements TypeReference {
            @Override public String toString() { return "[" + elementType + "]"; }
        }

        /**
         * A non-null type reference (e.g., "String!").
         */
        record NonNullTypeRef(TypeReference wrappedType) implements TypeReference {
            @Override public String toString() { return wrappedType + "!"; }
        }
    }
}
