package ssg.legoflow.rpc.graphql.schema;

import java.util.Objects;

/**
 * Represents a GraphQL non-null type wrapper: Type!
 *
 * <p>A non-null type wraps another type, indicating that the field
 * will never return null. When a non-null field returns null, a field
 * error is raised and null propagates to the parent field.
 *
 * @since 0.1.0
 */
public final class NonNullType implements GraphQLType {

    private final GraphQLType wrappedType;

    /**
     * Creates a new non-null type.
     *
     * @param wrappedType the wrapped type (must not be NonNullType)
     * @throws IllegalArgumentException if wrapping a NonNullType
     */
    public NonNullType(GraphQLType wrappedType) {
        if (wrappedType instanceof NonNullType) {
            throw new IllegalArgumentException("Cannot wrap NonNullType in NonNullType");
        }
        this.wrappedType = Objects.requireNonNull(wrappedType);
    }

    /**
     * Creates a non-null type: Type!
     *
     * @param wrappedType the wrapped type
     * @return a new non-null type
     */
    public static NonNullType of(GraphQLType wrappedType) {
        return new NonNullType(wrappedType);
    }

    @Override
    public String name() { return null; }

    /**
     * Returns the wrapped type.
     *
     * @return the wrapped type
     */
    public GraphQLType wrappedType() { return wrappedType; }

    @Override
    public String toString() { return wrappedType + "!"; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof NonNullType n && wrappedType.equals(n.wrappedType));
    }

    @Override
    public int hashCode() { return Objects.hash(NonNullType.class, wrappedType); }
}
