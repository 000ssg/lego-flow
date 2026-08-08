package ssg.legoflow.rpc.graphql.schema;

import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.graphql.sdl.SchemaPrinter;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SchemaPrinterTest {

    @Test
    void testPrintQueryType() {
        var query = ObjectType.of("Query", List.of(
            FieldDefinition.of("hello", ScalarType.STRING)));
        var schema = GraphQLSchema.newSchema().query(query).build();
        String printed = SchemaPrinter.print(schema);
        assertThat(printed).contains("type Query");
        assertThat(printed).contains("hello");
    }

    @Test
    void testPrintWithMutation() {
        var query = ObjectType.of("Query", List.of(
            FieldDefinition.of("user", ScalarType.STRING)));
        var mutation = ObjectType.of("Mutation", List.of(
            FieldDefinition.of("addUser", ScalarType.STRING)));
        var schema = GraphQLSchema.newSchema()
            .query(query).mutation(mutation).build();
        String printed = SchemaPrinter.print(schema);
        assertThat(printed).contains("type Query");
        assertThat(printed).contains("type Mutation");
    }

    @Test
    void testPrintTypeRef() {
        String ref = SchemaPrinter.printTypeRef(ScalarType.STRING);
        assertThat(ref).isEqualTo("String");
        String listRef = SchemaPrinter.printTypeRef(ListType.of(ScalarType.INT));
        assertThat(listRef).contains("[");
    }

    @Test
    void testPrintNonNullType() {
        String ref = SchemaPrinter.printTypeRef(NonNullType.of(ScalarType.STRING));
        assertThat(ref).contains("!");
    }

    @Test
    void testPrintMinimalSchema() {
        var query = ObjectType.of("Query", List.of(
            FieldDefinition.of("x", ScalarType.INT)));
        var schema = GraphQLSchema.newSchema().query(query).build();
        assertThat(SchemaPrinter.print(schema)).isNotBlank();
    }

    @Test
    void testPrintType() {
        var user = ObjectType.of("User", List.of(
            FieldDefinition.of("name", ScalarType.STRING)));
        String printed = SchemaPrinter.printType(user);
        assertThat(printed).contains("type User");
    }

    @Test
    void testPrintEnum() {
        var enumType = EnumType.of("Status", List.of(
            EnumType.EnumValue.of("ACTIVE"),
            EnumType.EnumValue.of("INACTIVE")));
        var query = ObjectType.of("Query", List.of(
            FieldDefinition.of("status", enumType)));
        var schema = GraphQLSchema.newSchema().query(query).build();
        String printed = SchemaPrinter.print(schema);
        assertThat(printed).contains("enum Status");
    }

    @Test
    void testPrintComplexTypes() {
        var user = ObjectType.of("User", List.of(
            FieldDefinition.of("id", NonNullType.of(ScalarType.ID)),
            FieldDefinition.of("name", ScalarType.STRING)));
        var query = ObjectType.of("Query", List.of(
            FieldDefinition.of("user", user),
            FieldDefinition.of("users", ListType.of(user))));
        var schema = GraphQLSchema.newSchema().query(query).build();
        String printed = SchemaPrinter.print(schema);
        assertThat(printed).contains("type User");
    }
}
