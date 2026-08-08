package ssg.legoflow.rpc.graphql.schema;

import java.util.List;
import java.util.Objects;

/**
 * Represents a GraphQL union type.
 *
 * <p>A union type indicates that a field can return one of several object types,
 * but does not define any fields of its own. Union members must all be object types.
 *
 * @since 0.1.0
 */
public final class UnionType implements GraphQLType {

    private final String name;
    private final String description;
    private final List<ObjectType> memberTypes;

    /**
     * Creates a new union type.
     *
     * @param name        the union name
     * @param description the union description
     * @param memberTypes the member object types
     */
    public UnionType(String name, String description, List<ObjectType> memberTypes) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.memberTypes = memberTypes != null ? List.copyOf(memberTypes) : List.of();
    }

    /**
     * Creates a simple union type.
     *
     * @param name    the union name
     * @param members the member object types
     * @return a new union type
     */
    public static UnionType of(String name, List<ObjectType> members) {
        return new UnionType(name, null, members);
    }

    @Override
    public String name() { return name; }
    public String description() { return description; }

    /**
     * Returns the member types of this union.
     *
     * @return the member types
     */
    public List<ObjectType> memberTypes() { return memberTypes; }

    /**
     * Returns whether the given object type is a member of this union.
     *
     * @param objectType the object type to check
     * @return true if the object type is a member
     */
    public boolean isMember(ObjectType objectType) {
        return memberTypes.stream().anyMatch(m -> m.name().equals(objectType.name()));
    }

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof UnionType t && name.equals(t.name));
    }

    @Override
    public int hashCode() { return name.hashCode(); }
}
