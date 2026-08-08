package ssg.legoflow.rpc.graphql.introspection;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IntrospectionTypesTest {

    @Test void testTypeKindEnum() {
        assertThat(IntrospectionTypes.TYPE_KIND.name()).isEqualTo("__TypeKind");
        var values = IntrospectionTypes.TYPE_KIND.values();
        assertThat(values).hasSize(8);
    }

    @Test void testDirectiveLocationEnum() {
        assertThat(IntrospectionTypes.DIRECTIVE_LOCATION.name()).isEqualTo("__DirectiveLocation");
        assertThat(IntrospectionTypes.DIRECTIVE_LOCATION.values()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test void testInputValueType() {
        var type = IntrospectionTypes.INPUT_VALUE;
        assertThat(type.name()).isEqualTo("__InputValue");
        assertThat(type.fields()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test void testEnumValueType() {
        var type = IntrospectionTypes.ENUM_VALUE;
        assertThat(type.name()).isEqualTo("__EnumValue");
        assertThat(type.fields()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test void testFieldType() {
        var type = IntrospectionTypes.FIELD_TYPE;
        assertThat(type.name()).isEqualTo("__Field");
        assertThat(type.fields()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test void testTypeType() {
        var type = IntrospectionTypes.TYPE_TYPE;
        assertThat(type.name()).isEqualTo("__Type");
        assertThat(type.fields()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test void testDirectiveType() {
        var type = IntrospectionTypes.DIRECTIVE_TYPE;
        assertThat(type.name()).isEqualTo("__Directive");
        assertThat(type.fields()).hasSizeGreaterThanOrEqualTo(2);
    }
}
