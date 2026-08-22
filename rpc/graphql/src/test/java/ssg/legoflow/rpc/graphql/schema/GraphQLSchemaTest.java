package ssg.legoflow.rpc.graphql.schema;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class GraphQLSchemaTest {

    @Test
    void testMinimalSchema() {
        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("hello", ScalarType.STRING)));
        var schema = GraphQLSchema.newSchema().query(queryType).build();

        assertThat(schema.queryType()).isEqualTo(queryType);
        assertThat(schema.mutationType()).isNull();
        assertThat(schema.subscriptionType()).isNull();
    }

    @Test
    void testSchemaRequiresQueryType() {
        assertThatThrownBy(() -> GraphQLSchema.newSchema().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSchemaCollectsTypes() {
        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("user", ObjectType.of("User", List.of(
                        FieldDefinition.of("name", ScalarType.STRING))))));
        var schema = GraphQLSchema.newSchema().query(queryType).build();

        assertThat(schema.getType("Query")).isNotNull();
        assertThat(schema.getType("User")).isNotNull();
        assertThat(schema.getType("String")).isEqualTo(ScalarType.STRING);
    }

    @Test
    void testSchemaHasBuiltInScalars() {
        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("x", ScalarType.INT)));
        var schema = GraphQLSchema.newSchema().query(queryType).build();

        assertThat(schema.getType("Int")).isEqualTo(ScalarType.INT);
        assertThat(schema.getType("Float")).isEqualTo(ScalarType.FLOAT);
        assertThat(schema.getType("String")).isEqualTo(ScalarType.STRING);
        assertThat(schema.getType("Boolean")).isEqualTo(ScalarType.BOOLEAN);
        assertThat(schema.getType("ID")).isEqualTo(ScalarType.ID);
    }

    @Test
    void testSchemaHasBuiltInDirectives() {
        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("x", ScalarType.INT)));
        var schema = GraphQLSchema.newSchema().query(queryType).build();

        assertThat(schema.getDirective("skip")).isNotNull();
        assertThat(schema.getDirective("include")).isNotNull();
        assertThat(schema.getDirective("deprecated")).isNotNull();
    }

    @Test
    void testSchemaWithMutation() {
        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("x", ScalarType.INT)));
        var mutationType = ObjectType.of("Mutation", List.of(
                FieldDefinition.of("addX", ScalarType.INT)));
        var schema = GraphQLSchema.newSchema()
                .query(queryType)
                .mutation(mutationType)
                .build();

        assertThat(schema.mutationType()).isNotNull();
        assertThat(schema.mutationType().name()).isEqualTo("Mutation");
    }

    @Test
    void testSchemaPossibleTypes() {
        var iface = InterfaceType.of("Node", List.of(
                FieldDefinition.of("id", ScalarType.ID)));
        var typeA = ObjectType.of("TypeA", List.of(
                FieldDefinition.of("id", ScalarType.ID)), List.of(iface));
        var typeB = ObjectType.of("TypeB", List.of(
                FieldDefinition.of("id", ScalarType.ID)), List.of(iface));

        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("node", iface)));
        var schema = GraphQLSchema.newSchema()
                .query(queryType)
                .additionalType(typeA)
                .additionalType(typeB)
                .build();

        var possibleTypes = schema.getPossibleTypes(iface);
        assertThat(possibleTypes).hasSize(2);
        assertThat(schema.isPossibleType(iface, typeA)).isTrue();
    }

    @Test
    void testSchemaUnionPossibleTypes() {
        var human = ObjectType.of("Human", List.of(
                FieldDefinition.of("name", ScalarType.STRING)));
        var droid = ObjectType.of("Droid", List.of(
                FieldDefinition.of("name", ScalarType.STRING)));
        var union = UnionType.of("SearchResult", List.of(human, droid));

        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("search", union)));
        var schema = GraphQLSchema.newSchema().query(queryType).build();

        assertThat(schema.getPossibleTypes(union)).hasSize(2);
    }

    @Test
    void testSchemaAdditionalTypes() {
        var customEnum = EnumType.of("Status", "ACTIVE", "INACTIVE");
        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("x", ScalarType.INT)));
        var schema = GraphQLSchema.newSchema()
                .query(queryType)
                .additionalType(customEnum)
                .build();

        assertThat(schema.getType("Status")).isNotNull();
    }

    @Test
    void testSchemaCustomDirective() {
        var customDirective = new Directive("auth", "Authentication required",
                List.of(ArgumentDefinition.of("role", ScalarType.STRING)),
                java.util.Set.of(Directive.Location.FIELD_DEFINITION));
        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("x", ScalarType.INT)));
        var schema = GraphQLSchema.newSchema()
                .query(queryType)
                .directive(customDirective)
                .build();

        assertThat(schema.getDirective("auth")).isNotNull();
        assertThat(schema.getDirective("auth").name()).isEqualTo("auth");
    }
}
