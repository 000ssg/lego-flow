package ssg.legoflow.rpc.graphql.introspection;

import ssg.legoflow.rpc.graphql.schema.*;

import java.util.List;

/**
 * Defines the GraphQL introspection type system.
 *
 * <p>The introspection system consists of:
 * <ul>
 *   <li>__Schema - root introspection type</li>
 *   <li>__Type - describes a type</li>
 *   <li>__Field - describes a field</li>
 *   <li>__InputValue - describes an argument or input field</li>
 *   <li>__EnumValue - describes an enum value</li>
 *   <li>__Directive - describes a directive</li>
 *   <li>__TypeKind - enum of type kinds</li>
 *   <li>__DirectiveLocation - enum of directive locations</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class IntrospectionTypes {

    /** The __TypeKind enum type. */
    public static final EnumType TYPE_KIND = EnumType.of("__TypeKind",
            "SCALAR", "OBJECT", "INTERFACE", "UNION", "ENUM",
            "INPUT_OBJECT", "LIST", "NON_NULL");

    /** The __DirectiveLocation enum type. */
    public static final EnumType DIRECTIVE_LOCATION = EnumType.of("__DirectiveLocation",
            "QUERY", "MUTATION", "SUBSCRIPTION", "FIELD", "FRAGMENT_DEFINITION",
            "FRAGMENT_SPREAD", "INLINE_FRAGMENT",
            "SCHEMA", "SCALAR", "OBJECT", "FIELD_DEFINITION", "ARGUMENT_DEFINITION",
            "INTERFACE", "UNION", "ENUM", "ENUM_VALUE", "INPUT_OBJECT", "INPUT_FIELD_DEFINITION");

    /** The __InputValue type. */
    public static final ObjectType INPUT_VALUE = ObjectType.of("__InputValue", List.of(
            FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
            FieldDefinition.of("description", ScalarType.STRING),
            FieldDefinition.of("type", NonNullType.of(ScalarType.STRING)), // Will be replaced
            FieldDefinition.of("defaultValue", ScalarType.STRING)
    ));

    /** The __EnumValue type. */
    public static final ObjectType ENUM_VALUE = ObjectType.of("__EnumValue", List.of(
            FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
            FieldDefinition.of("description", ScalarType.STRING),
            FieldDefinition.of("isDeprecated", NonNullType.of(ScalarType.BOOLEAN)),
            FieldDefinition.of("deprecationReason", ScalarType.STRING)
    ));

    /** The __Field type. */
    public static final ObjectType FIELD_TYPE = ObjectType.of("__Field", List.of(
            FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
            FieldDefinition.of("description", ScalarType.STRING),
            FieldDefinition.of("args", NonNullType.of(ListType.of(NonNullType.of(INPUT_VALUE)))),
            FieldDefinition.of("type", NonNullType.of(ScalarType.STRING)), // Will be replaced
            FieldDefinition.of("isDeprecated", NonNullType.of(ScalarType.BOOLEAN)),
            FieldDefinition.of("deprecationReason", ScalarType.STRING)
    ));

    /** The __Type type. */
    public static final ObjectType TYPE_TYPE = ObjectType.of("__Type", List.of(
            FieldDefinition.of("kind", NonNullType.of(TYPE_KIND)),
            FieldDefinition.of("name", ScalarType.STRING),
            FieldDefinition.of("description", ScalarType.STRING),
            FieldDefinition.of("fields", ListType.of(NonNullType.of(FIELD_TYPE)),
                    List.of(ArgumentDefinition.of("includeDeprecated", ScalarType.BOOLEAN, false))),
            FieldDefinition.of("interfaces", ListType.of(NonNullType.of(ScalarType.STRING))),
            FieldDefinition.of("possibleTypes", ListType.of(NonNullType.of(ScalarType.STRING))),
            FieldDefinition.of("enumValues", ListType.of(NonNullType.of(ENUM_VALUE)),
                    List.of(ArgumentDefinition.of("includeDeprecated", ScalarType.BOOLEAN, false))),
            FieldDefinition.of("inputFields", ListType.of(NonNullType.of(INPUT_VALUE))),
            FieldDefinition.of("ofType", ScalarType.STRING)
    ));

    /** The __Directive type. */
    public static final ObjectType DIRECTIVE_TYPE = ObjectType.of("__Directive", List.of(
            FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
            FieldDefinition.of("description", ScalarType.STRING),
            FieldDefinition.of("locations", NonNullType.of(ListType.of(NonNullType.of(DIRECTIVE_LOCATION)))),
            FieldDefinition.of("args", NonNullType.of(ListType.of(NonNullType.of(INPUT_VALUE))))
    ));

    /** The __Schema type. */
    public static final ObjectType SCHEMA_TYPE = ObjectType.of("__Schema", List.of(
            FieldDefinition.of("types", NonNullType.of(ListType.of(NonNullType.of(TYPE_TYPE)))),
            FieldDefinition.of("queryType", NonNullType.of(TYPE_TYPE)),
            FieldDefinition.of("mutationType", TYPE_TYPE),
            FieldDefinition.of("subscriptionType", TYPE_TYPE),
            FieldDefinition.of("directives", NonNullType.of(ListType.of(NonNullType.of(DIRECTIVE_TYPE))))
    ));

    private IntrospectionTypes() {}
}
