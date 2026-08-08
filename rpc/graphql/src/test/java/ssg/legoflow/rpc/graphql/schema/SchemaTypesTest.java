package ssg.legoflow.rpc.graphql.schema;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SchemaTypesTest {

    @Test void testScalarType() {
        assertThat(ScalarType.INT.name()).isEqualTo("Int");
        assertThat(ScalarType.STRING.name()).isEqualTo("String");
    }

    @Test void testObjectType() {
        var user = ObjectType.of("User", List.of(
                FieldDefinition.of("id", ScalarType.ID),
                FieldDefinition.of("name", NonNullType.of(ScalarType.STRING))));
        assertThat(user.name()).isEqualTo("User");
        assertThat(user.fields()).hasSize(2);
    }

    @Test void testInterfaceType() {
        var iface = InterfaceType.of("Node", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID))));
        assertThat(iface.name()).isEqualTo("Node");
    }

    @Test void testUnionType() {
        var user = ObjectType.of("User", List.of(FieldDefinition.of("name", ScalarType.STRING)));
        var post = ObjectType.of("Post", List.of(FieldDefinition.of("title", ScalarType.STRING)));
        var union = UnionType.of("SearchResult", List.of(user, post));
        assertThat(union.name()).isEqualTo("SearchResult");
    }

    @Test void testEnumType() {
        var status = EnumType.of("Status", "ACTIVE", "INACTIVE", "PENDING");
        assertThat(status.name()).isEqualTo("Status");
        assertThat(status.values()).hasSize(3);
    }

    @Test void testListType() {
        var list = ListType.of(ScalarType.STRING);
        assertThat(list.elementType()).isSameAs(ScalarType.STRING);
    }

    @Test void testNonNullType() {
        var nn = NonNullType.of(ScalarType.INT);
        assertThat(nn.wrappedType()).isSameAs(ScalarType.INT);
    }

    @Test void testFieldDefinitionWithArguments() {
        var arg = ArgumentDefinition.of("id", ScalarType.ID, null);
        var field = FieldDefinition.of("user", ScalarType.STRING, List.of(arg));
        assertThat(field.arguments()).hasSize(1);
    }

    @Test void testDirective() {
        assertThat(Directive.SKIP.name()).isEqualTo("skip");
        assertThat(Directive.INCLUDE.name()).isEqualTo("include");
    }

    @Test void testGraphQLSchemaBuilder() {
        var query = ObjectType.of("Query", List.of(
                FieldDefinition.of("hello", ScalarType.STRING)));
        var schema = GraphQLSchema.newSchema().query(query).build();
        assertThat(schema.queryType()).isSameAs(query);
    }

    @Test void testGraphQLSchemaWithMutation() {
        var query = ObjectType.of("Query", List.of(
                FieldDefinition.of("hello", ScalarType.STRING)));
        var mutation = ObjectType.of("Mutation", List.of(
                FieldDefinition.of("doThing", ScalarType.BOOLEAN)));
        var schema = GraphQLSchema.newSchema().query(query).mutation(mutation).build();
        assertThat(schema.mutationType()).isSameAs(mutation);
    }

    @Test void testGraphQLSchemaDirectives() {
        var query = ObjectType.of("Query", List.of(
                FieldDefinition.of("hello", ScalarType.STRING)));
        var schema = GraphQLSchema.newSchema().query(query).build();
        assertThat(schema.directives()).containsKeys("skip", "include", "deprecated");
    }

    @Test void testGraphQLSchemaTypes() {
        var query = ObjectType.of("Query", List.of(
                FieldDefinition.of("hello", ScalarType.STRING)));
        var schema = GraphQLSchema.newSchema().query(query).build();
        assertThat(schema.typeMap()).containsKey("Query");
    }

    @Test void testGraphQLSchemaGetType() {
        var query = ObjectType.of("Query", List.of(
                FieldDefinition.of("hello", ScalarType.STRING)));
        var schema = GraphQLSchema.newSchema().query(query).build();
        assertThat(schema.getType("String")).isSameAs(ScalarType.STRING);
    }

    @Test void testGraphQLSchemaNullQueryThrows() {
        assertThatThrownBy(() -> GraphQLSchema.newSchema().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testInterfaceAddImplementation() {
        var iface = InterfaceType.of("Node", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID))));
        var user = ObjectType.of("User", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID))
        ), List.of(iface));
        iface.addImplementation(user);
        assertThat(iface.implementations()).contains(user);
    }
}
