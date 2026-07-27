package ssg.legoflow.rpc.graphql.schema;

import java.util.Objects;

/**
 * Represents a GraphQL list type wrapper: [Type].
 *
 * <p>A list type wraps another type, indicating that the field returns
 * a list of that type. Lists can be nested and combined with NonNull.
 *
 * @since 1.0.0
 */
public final class ListType implements GraphQLType {

    private final GraphQLType elementType;

    /**
     * Creates a new list type.
     *
     * @param elementType the element type
     */
    public ListType(GraphQLType elementType) {
        this.elementType = Objects.requireNonNull(elementType);
    }

    /**
     * Creates a list type: [Type].
     *
     * @param elementType the element type
     * @return a new list type
     */
    public static ListType of(GraphQLType elementType) {
        return new ListType(elementType);
    }

    @Override
    public String name() { return null; }

    /**
     * Returns the element type of this list.
     *
     * @return the element type
     */
    public GraphQLType elementType() { return elementType; }

    @Override
    public String toString() { return "[" + elementType + "]"; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ListType l && elementType.equals(l.elementType));
    }

    @Override
    public int hashCode() { return Objects.hash(ListType.class, elementType); }
}
