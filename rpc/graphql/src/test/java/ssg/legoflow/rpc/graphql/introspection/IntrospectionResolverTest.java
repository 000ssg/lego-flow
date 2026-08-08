package ssg.legoflow.rpc.graphql.introspection;

import ssg.legoflow.rpc.graphql.schema.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class IntrospectionResolverTest {

    @Test void testCreateIntrospectionFields() {
        var schema = GraphQLSchema.newSchema()
                .query(ObjectType.of("Query", List.of(
                        FieldDefinition.of("hello", ScalarType.STRING)))
                ).build();

        var fields = IntrospectionResolver.createIntrospectionFields(schema);
        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).name()).isEqualTo("__schema");
        assertThat(fields.get(1).name()).isEqualTo("__type");
    }

    @Test void testBuildSchemaMap() {
        var schema = GraphQLSchema.newSchema()
                .query(ObjectType.of("Query", List.of(
                        FieldDefinition.of("id", ScalarType.STRING)))
                ).build();

        var map = IntrospectionResolver.buildSchemaMap(schema);
        assertThat(map).containsKey("types");
        assertThat(map).containsKey("queryType");
        assertThat(map).containsKey("mutationType");
        assertThat(map).containsKey("subscriptionType");
        assertThat(map).containsKey("directives");

        var queryType = (java.util.Map<?,?>) map.get("queryType");
        assertThat(queryType.get("name")).isEqualTo("Query");
    }

    @Test void testBuildSchemaMapWithMutation() {
        var schema = GraphQLSchema.newSchema()
                .query(ObjectType.of("Query", List.of(FieldDefinition.of("x", ScalarType.STRING))))
                .mutation(ObjectType.of("Mutation", List.of(FieldDefinition.of("setX", ScalarType.STRING))))
                .build();

        var map = IntrospectionResolver.buildSchemaMap(schema);
        var mutationType = (java.util.Map<?,?>) map.get("mutationType");
        assertThat(mutationType).isNotNull();
        assertThat(mutationType.get("name")).isEqualTo("Mutation");
    }

    @Test void testBuildSchemaMapNoMutationReturnsNull() {
        var schema = GraphQLSchema.newSchema()
                .query(ObjectType.of("Query", List.of(FieldDefinition.of("x", ScalarType.STRING))))
                .build();

        var map = IntrospectionResolver.buildSchemaMap(schema);
        assertThat(map.get("mutationType")).isNull();
        assertThat(map.get("subscriptionType")).isNull();
    }

    @Test void testBuildSchemaMapFiltersIntrospectionTypes() {
        var schema = GraphQLSchema.newSchema()
                .query(ObjectType.of("Query", List.of(FieldDefinition.of("x", ScalarType.STRING))))
                .build();

        var map = IntrospectionResolver.buildSchemaMap(schema);
        var types = (java.util.List<?>) map.get("types");
        
        for (var t : types) {
            String name = (String) ((java.util.Map<?,?>) t).get("name");
            assertThat(name.startsWith("__")).isFalse();
        }
    }

    @Test void testBuildSchemaMapContainsDirectives() {
        var schema = GraphQLSchema.newSchema()
                .query(ObjectType.of("Query", List.of(FieldDefinition.of("x", ScalarType.STRING))))
                .build();

        var map = IntrospectionResolver.buildSchemaMap(schema);
        var directives = (java.util.List<?>) map.get("directives");
        assertThat(directives).isNotEmpty(); // Has at least @skip, @include, @deprecated
    }

    @Test void testBuildTypeMapForObjectType() {
        var objType = ObjectType.of("User", List.of(
                FieldDefinition.of("id", ScalarType.STRING),
                FieldDefinition.of("name", ScalarType.STRING)));

        var schema = GraphQLSchema.newSchema().query(objType).build();
        var map = IntrospectionResolver.buildTypeMap(objType, schema);

        assertThat(map.get("kind")).isEqualTo("OBJECT");
        assertThat(map.get("name")).isEqualTo("User");
    }

    @Test void testBuildTypeMapForScalarType() {
        var schema = GraphQLSchema.newSchema().query(ObjectType.of("Q", List.of())).build();
        var map = IntrospectionResolver.buildTypeMap(ScalarType.STRING, schema);

        assertThat(map.get("kind")).isEqualTo("SCALAR");
        assertThat(map.get("name")).isEqualTo("String");
    }

    @Test void testBuildTypeMapForEnumType() {
        var enumType = EnumType.of("Status", List.of(
                EnumType.EnumValue.of("ACTIVE"), EnumType.EnumValue.of("INACTIVE")));
        
        var schema = GraphQLSchema.newSchema()
                .query(ObjectType.of("Q", List.of(FieldDefinition.of("s", enumType))))
                .build();
        
        var map = IntrospectionResolver.buildTypeMap(enumType, schema);
        assertThat(map.get("kind")).isEqualTo("ENUM");
        assertThat(map.get("name")).isEqualTo("Status");
    }

    @Test void testBuildSchemaMapWithMultipleTypes() {
        var userType = ObjectType.of("User", List.of(
                FieldDefinition.of("id", ScalarType.STRING)));
        var postType = ObjectType.of("Post", List.of(
                FieldDefinition.of("title", ScalarType.STRING)));
        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("user", userType),
                FieldDefinition.of("post", postType)));

        var schema = GraphQLSchema.newSchema().query(queryType).build();
        var map = IntrospectionResolver.buildSchemaMap(schema);
        
        var types = (java.util.List<?>) map.get("types");
        assertThat(types.size()).isGreaterThanOrEqualTo(3); // Query, User, Post + built-in scalars
    }
}
