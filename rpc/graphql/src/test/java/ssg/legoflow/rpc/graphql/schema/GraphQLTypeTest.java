package ssg.legoflow.rpc.graphql.schema;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class GraphQLTypeTest {

    @Test
    void testScalarTypeNames() {
        assertThat(ScalarType.INT.name()).isEqualTo("Int");
        assertThat(ScalarType.FLOAT.name()).isEqualTo("Float");
        assertThat(ScalarType.STRING.name()).isEqualTo("String");
        assertThat(ScalarType.BOOLEAN.name()).isEqualTo("Boolean");
        assertThat(ScalarType.ID.name()).isEqualTo("ID");
    }

    @Test
    void testScalarIsBuiltIn() {
        assertThat(ScalarType.INT.isBuiltIn()).isTrue();
        assertThat(ScalarType.STRING.isBuiltIn()).isTrue();
        var custom = new ScalarType("DateTime", "Custom date", v -> v, v -> v);
        assertThat(custom.isBuiltIn()).isFalse();
    }

    @Test
    void testIntScalarCoercion() {
        assertThat(ScalarType.INT.serialize(42)).isEqualTo(42);
        assertThat(ScalarType.INT.serialize(42L)).isEqualTo(42);
        assertThat(ScalarType.INT.serialize("123")).isEqualTo(123);
        assertThat(ScalarType.INT.parseLiteral(42)).isEqualTo(42);
    }

    @Test
    void testFloatScalarCoercion() {
        assertThat(ScalarType.FLOAT.serialize(3.14)).isEqualTo(3.14);
        assertThat(ScalarType.FLOAT.serialize(42)).isEqualTo(42.0);
        assertThat(ScalarType.FLOAT.serialize("2.5")).isEqualTo(2.5);
    }

    @Test
    void testStringScalarCoercion() {
        assertThat(ScalarType.STRING.serialize("hello")).isEqualTo("hello");
        assertThat(ScalarType.STRING.serialize(42)).isEqualTo("42");
    }

    @Test
    void testBooleanScalarCoercion() {
        assertThat(ScalarType.BOOLEAN.serialize(true)).isEqualTo(true);
        assertThat(ScalarType.BOOLEAN.serialize(false)).isEqualTo(false);
        assertThat(ScalarType.BOOLEAN.serialize("nope")).isNull();
    }

    @Test
    void testIdScalarCoercion() {
        assertThat(ScalarType.ID.serialize("abc")).isEqualTo("abc");
        assertThat(ScalarType.ID.serialize(123)).isEqualTo("123");
    }

    @Test
    void testObjectType() {
        var type = ObjectType.of("User", List.of(
                FieldDefinition.of("id", ScalarType.ID),
                FieldDefinition.of("name", ScalarType.STRING)
        ));
        assertThat(type.name()).isEqualTo("User");
        assertThat(type.fields()).hasSize(2);
        assertThat(type.getField("id")).isNotNull();
        assertThat(type.getField("name")).isNotNull();
        assertThat(type.getField("missing")).isNull();
    }

    @Test
    void testObjectTypeWithInterfaces() {
        var iface = InterfaceType.of("Node", List.of(
                FieldDefinition.of("id", ScalarType.ID)));
        var type = ObjectType.of("User", List.of(
                FieldDefinition.of("id", ScalarType.ID)),
                List.of(iface));
        assertThat(type.interfaces()).hasSize(1);
        assertThat(type.interfaces().getFirst().name()).isEqualTo("Node");
    }

    @Test
    void testInterfaceType() {
        var iface = InterfaceType.of("Character", List.of(
                FieldDefinition.of("name", ScalarType.STRING)));
        assertThat(iface.name()).isEqualTo("Character");
        assertThat(iface.getField("name")).isNotNull();
        assertThat(iface.implementations()).isEmpty();
    }

    @Test
    void testUnionType() {
        var human = ObjectType.of("Human", List.of());
        var droid = ObjectType.of("Droid", List.of());
        var union = UnionType.of("SearchResult", List.of(human, droid));
        assertThat(union.name()).isEqualTo("SearchResult");
        assertThat(union.memberTypes()).hasSize(2);
        assertThat(union.isMember(human)).isTrue();
        assertThat(union.isMember(ObjectType.of("Other", List.of()))).isFalse();
    }

    @Test
    void testEnumType() {
        var enumType = EnumType.of("Episode", "NEWHOPE", "EMPIRE", "JEDI");
        assertThat(enumType.name()).isEqualTo("Episode");
        assertThat(enumType.values()).hasSize(3);
        assertThat(enumType.isValidValue("NEWHOPE")).isTrue();
        assertThat(enumType.isValidValue("PHANTOM")).isFalse();
        assertThat(enumType.getValue("EMPIRE")).isNotNull();
    }

    @Test
    void testEnumValueDeprecated() {
        var deprecated = EnumType.EnumValue.deprecated("OLD_VALUE", "Use NEW_VALUE");
        assertThat(deprecated.deprecated()).isTrue();
        assertThat(deprecated.deprecationReason()).isEqualTo("Use NEW_VALUE");
    }

    @Test
    void testInputObjectType() {
        var input = InputObjectType.of("CreateUser", List.of(
                InputObjectType.InputFieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
                InputObjectType.InputFieldDefinition.of("age", ScalarType.INT, 0)
        ));
        assertThat(input.name()).isEqualTo("CreateUser");
        assertThat(input.fields()).hasSize(2);
        assertThat(input.getField("name")).isNotNull();
        assertThat(input.getField("age").hasDefaultValue()).isTrue();
        assertThat(input.getField("age").defaultValue()).isEqualTo(0);
    }

    @Test
    void testListType() {
        var listType = ListType.of(ScalarType.STRING);
        assertThat(listType.name()).isNull();
        assertThat(listType.elementType()).isEqualTo(ScalarType.STRING);
        assertThat(listType.toString()).isEqualTo("[String]");
    }

    @Test
    void testNonNullType() {
        var nonNull = NonNullType.of(ScalarType.STRING);
        assertThat(nonNull.name()).isNull();
        assertThat(nonNull.wrappedType()).isEqualTo(ScalarType.STRING);
        assertThat(nonNull.toString()).isEqualTo("String!");
    }

    @Test
    void testNonNullCannotWrapNonNull() {
        assertThatThrownBy(() -> NonNullType.of(NonNullType.of(ScalarType.STRING)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNestedListNonNull() {
        // [String!]!
        var type = NonNullType.of(ListType.of(NonNullType.of(ScalarType.STRING)));
        assertThat(type.toString()).isEqualTo("[String!]!");
        assertThat(type.unwrap().name()).isEqualTo("String");
    }

    @Test
    void testUnwrap() {
        var type = NonNullType.of(ListType.of(NonNullType.of(ScalarType.INT)));
        assertThat(type.unwrap()).isEqualTo(ScalarType.INT);
    }

    @Test
    void testIsInputType() {
        assertThat(ScalarType.STRING.isInputType()).isTrue();
        assertThat(EnumType.of("E", "A").isInputType()).isTrue();
        assertThat(InputObjectType.of("I", List.of()).isInputType()).isTrue();
        assertThat(ObjectType.of("O", List.of()).isInputType()).isFalse();
    }

    @Test
    void testIsOutputType() {
        assertThat(ScalarType.STRING.isOutputType()).isTrue();
        assertThat(ObjectType.of("O", List.of()).isOutputType()).isTrue();
        assertThat(InterfaceType.of("I", List.of()).isOutputType()).isTrue();
        assertThat(InputObjectType.of("IO", List.of()).isOutputType()).isFalse();
    }

    @Test
    void testIsNamedType() {
        assertThat(ScalarType.STRING.isNamedType()).isTrue();
        assertThat(ListType.of(ScalarType.STRING).isNamedType()).isFalse();
        assertThat(NonNullType.of(ScalarType.STRING).isNamedType()).isFalse();
    }

    @Test
    void testFieldDefinition() {
        var field = FieldDefinition.of("name", "User's name", ScalarType.STRING);
        assertThat(field.name()).isEqualTo("name");
        assertThat(field.description()).isEqualTo("User's name");
        assertThat(field.type()).isEqualTo(ScalarType.STRING);
        assertThat(field.isDeprecated()).isFalse();
    }

    @Test
    void testFieldDefinitionWithArguments() {
        var field = FieldDefinition.of("user", ObjectType.of("User", List.of()),
                List.of(ArgumentDefinition.of("id", NonNullType.of(ScalarType.ID))));
        assertThat(field.arguments()).hasSize(1);
        assertThat(field.getArgument("id")).isNotNull();
        assertThat(field.getArgument("missing")).isNull();
    }

    @Test
    void testArgumentDefinition() {
        var arg = ArgumentDefinition.of("limit", ScalarType.INT, 10);
        assertThat(arg.name()).isEqualTo("limit");
        assertThat(arg.type()).isEqualTo(ScalarType.INT);
        assertThat(arg.hasDefaultValue()).isTrue();
        assertThat(arg.defaultValue()).isEqualTo(10);
    }

    @Test
    void testDirective() {
        assertThat(Directive.SKIP.name()).isEqualTo("skip");
        assertThat(Directive.INCLUDE.name()).isEqualTo("include");
        assertThat(Directive.DEPRECATED.name()).isEqualTo("deprecated");
        assertThat(Directive.SKIP.getArgument("if")).isNotNull();
    }

    @Test
    void testDirectiveUsage() {
        var usage = Directive.DirectiveUsage.of("skip", java.util.Map.of("if", true));
        assertThat(usage.name()).isEqualTo("skip");
        assertThat(usage.getArgument("if")).isEqualTo(true);
    }

    @Test
    void testTypeEquality() {
        assertThat(ScalarType.INT).isEqualTo(ScalarType.INT);
        assertThat(ObjectType.of("A", List.of())).isEqualTo(ObjectType.of("A", List.of()));
        assertThat(ListType.of(ScalarType.INT)).isEqualTo(ListType.of(ScalarType.INT));
    }
}
