package ssg.legoflow.rpc.graphql.schema;

/**
 * Sealed interface representing all GraphQL type system types.
 *
 * <p>The GraphQL type system describes the capabilities of a GraphQL server
 * and is used to determine if a query is valid. Types are divided into
 * named types (scalars, objects, interfaces, unions, enums, input objects)
 * and wrapping types (list, non-null).
 *
 * @since 0.1.0
 */
public sealed interface GraphQLType
        permits ScalarType, ObjectType, InterfaceType, UnionType,
                EnumType, InputObjectType, ListType, NonNullType {

    /**
     * Returns the name of this type, or null for wrapping types.
     *
     * @return the type name, or null
     */
    String name();

    /**
     * Returns the unwrapped named type, stripping all List and NonNull wrappers.
     *
     * @return the innermost named type
     */
    default GraphQLType unwrap() {
        return switch (this) {
            case ListType l -> l.elementType().unwrap();
            case NonNullType n -> n.wrappedType().unwrap();
            default -> this;
        };
    }

    /**
     * Returns whether this type is a named type (not a wrapper).
     *
     * @return true if this is a named type
     */
    default boolean isNamedType() {
        return !(this instanceof ListType) && !(this instanceof NonNullType);
    }

    /**
     * Returns whether this type is a wrapping type (List or NonNull).
     *
     * @return true if this is a wrapping type
     */
    default boolean isWrappingType() {
        return this instanceof ListType || this instanceof NonNullType;
    }

    /**
     * Returns whether this type is an input type (scalar, enum, or input object,
     * possibly wrapped in list/non-null).
     *
     * @return true if this is a valid input type
     */
    default boolean isInputType() {
        var unwrapped = unwrap();
        return unwrapped instanceof ScalarType
                || unwrapped instanceof EnumType
                || unwrapped instanceof InputObjectType;
    }

    /**
     * Returns whether this type is an output type (scalar, object, interface,
     * union, or enum, possibly wrapped in list/non-null).
     *
     * @return true if this is a valid output type
     */
    default boolean isOutputType() {
        var unwrapped = unwrap();
        return unwrapped instanceof ScalarType
                || unwrapped instanceof ObjectType
                || unwrapped instanceof InterfaceType
                || unwrapped instanceof UnionType
                || unwrapped instanceof EnumType;
    }
}
