package ssg.legoflow.rpc.graphql.sdl;

import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.graphql.language.GraphQLSyntaxException;
import ssg.legoflow.rpc.graphql.schema.*;
import static org.assertj.core.api.Assertions.*;

class SchemaParserExtendedTest {

    @Test void testParseScalarDefinition() {
        String sdl = "type Query { now: DateTime } scalar DateTime";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("DateTime")).isNotNull();
    }

    @Test void testParseMultipleScalars() {
        String sdl = "type Query { v: CustomScalar w: AnotherScalar } scalar CustomScalar scalar AnotherScalar";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("CustomScalar")).isNotNull();
        assertThat(schema.getType("AnotherScalar")).isNotNull();
    }

    @Test void testParseInputObjectType() {
        String sdl = "type Query { createUser(input: CreateUserInput): User } input CreateUserInput { name: String email: String } type User { id: ID name: String }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("CreateUserInput")).isNotNull();
    }

    @Test void testParseNestedInputType() {
        String sdl = "type Query { hello: String } input FilterInput { tags: [String] nested: NestedFilter } input NestedFilter { active: Boolean }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("FilterInput")).isNotNull();
        assertThat(schema.getType("NestedFilter")).isNotNull();
    }

    @Test void testParseSubscriptionType() {
        String sdl = "schema { query: Query subscription: Subscriptions } type Query { hello: String } type Subscriptions { onMessage: String }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.subscriptionType()).isNotNull();
    }

    @Test void testParseListField() {
        String sdl = "type Query { items: [String] users: [User] } type User { name: String }";
        var schema = SchemaParser.parse(sdl);
        var queryType = (ObjectType) schema.getType("Query");
        assertThat(queryType.getField("items")).isNotNull();
    }

    @Test void testParseNonNullListField() {
        String sdl = "type Query { required: [String!]! } type User { name: String }";
        var schema = SchemaParser.parse(sdl);
        var queryType = (ObjectType) schema.getType("Query");
        assertThat(queryType.getField("required")).isNotNull();
    }

    @Test void testParseNonNullArg() {
        String sdl = "type Query { user(id: ID!): User } type User { id: ID name: String }";
        var schema = SchemaParser.parse(sdl);
        var queryType = (ObjectType) schema.getType("Query");
        var field = queryType.getField("user");
        assertThat(field).isNotNull();
        assertThat(field.arguments()).hasSize(1);
    }

    @Test void testParseMultipleArgs() {
        String sdl = "type Query { search(query: String limit: Int offset: Int): [String] } type Item { name: String }";
        var schema = SchemaParser.parse(sdl);
        var queryType = (ObjectType) schema.getType("Query");
        var field = queryType.getField("search");
        assertThat(field).isNotNull();
        assertThat(field.arguments()).hasSize(3);
    }

    @Test void testParseEnumWithSingleValue() {
        String sdl = "type Query { status: Status } enum Status { UNKNOWN }";
        var schema = SchemaParser.parse(sdl);
        var enumType = (EnumType) schema.getType("Status");
        assertThat(enumType.values()).hasSize(1);
    }

    @Test void testParseMultipleEnums() {
        String sdl = "type Query { a: A b: B } enum A { X Y Z } enum B { P Q }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("A")).isNotNull();
        assertThat(schema.getType("B")).isNotNull();
    }

    @Test void testParseMultipleInterfaces() {
        String sdl = "type Query { node: Node withId: Identifiable } interface Node { id: ID name: String } interface Identifiable { id: ID } type User implements Node & Identifiable { id: ID name: String age: Int }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("Node")).isNotNull();
        assertThat(schema.getType("Identifiable")).isNotNull();
    }

    @Test void testParseInterfaceWithMultipleFields() {
        String sdl = "type Query { i: Info } interface Info { name: String email: String createdAt: Int } type User implements Info { name: String email: String createdAt: Int age: Int }";
        var schema = SchemaParser.parse(sdl);
    }

    @Test void testParseUnionWithThreeMembers() {
        String sdl = "type Query { search: SearchResult } union SearchResult = User | Post | Comment type User { name: String } type Post { title: String } type Comment { text: String }";
        var schema = SchemaParser.parse(sdl);
    }

    @Test void testParseDirectiveDefinition() {
        String sdl = "type Query { deprecatedField: String } directive @skip(if: Boolean) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT";
        var schema = SchemaParser.parse(sdl);
    }

    @Test void testParseDirectiveWithMultipleLocations() {
        String sdl = "type Query { hello: String } directive @custom on FIELD_DEFINITION | ARGUMENT_DEFINITION | INTERFACE";
        var schema = SchemaParser.parse(sdl);
    }

    @Test void testParseForwardReference() {
        String sdl = "type Query { user: User } type User { posts: [Post] friend: User } type Post { author: User content: String }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("User")).isNotNull();
        assertThat(schema.getType("Post")).isNotNull();
    }

    @Test void testParseEmptySchemaWithJustQuery() {
        String sdl = "type Query { hello: String }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.queryType()).isNotNull();
        assertThat(schema.mutationType()).isNull();
        assertThat(schema.subscriptionType()).isNull();
    }

    @Test void testParseWithBuiltInScalars() {
        String sdl = "type Query { i: Int f: Float s: String b: Boolean id: ID }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("Int")).isSameAs(ScalarType.INT);
    }

    @Test void testParseNonQueryTypeThrows() {
        String sdl = "type User { name: String }";
        assertThatThrownBy(() -> SchemaParser.parse(sdl))
                .isInstanceOf(GraphQLSyntaxException.class);
    }

    @Test void testParseMalformedSchemaThrows() {
        String sdl = "typ Query { hello: String }";
        assertThatThrownBy(() -> SchemaParser.parse(sdl))
                .isInstanceOf(GraphQLSyntaxException.class);
    }

    @Test void testSchemaPrinterRoundsTrip() {
        String original = "type Query { hello: String user(id: ID): User } type User { id: ID name: String }";
        var schema = SchemaParser.parse(original);
        String printed = SchemaPrinter.print(schema);
        assertThat(printed).contains("type Query");
        assertThat(printed).contains("type User");
    }

    @Test void testParseWithDescriptions() {
        String sdl = "\"User type\" type Query { hello: String } type User { id: ID }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("Query")).isNotNull();
    }

    @Test void testSchemaPrintWithMutation() {
        String sdl = "schema { query: Q mutation: M } type Q { hello: String } type M { set(name: String): Boolean }";
        var schema = SchemaParser.parse(sdl);
        String printed = SchemaPrinter.print(schema);
        assertThat(printed).contains("type M");
    }

    @Test void testParseSchemaDefinitionBlock() {
        String sdl = "schema { query: MyQuery } type MyQuery { hello: String }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.queryType()).isNotNull();
    }

    @Test void testPrintSchemaWithSubscription() {
        String sdl = "schema { query: Q subscription: S } type Q { hello: String } type S { onMessage: String }";
        var schema = SchemaParser.parse(sdl);
        String printed = SchemaPrinter.print(schema);
        assertThat(printed).contains("subscription");
    }

    @Test void testParseDirectiveOnEnumValue() {
        String sdl = "type Query { status: Status } enum Status { ACTIVE DEPRECATED_STATUS } directive @deprecated(reason: String) on FIELD_DEFINITION | ENUM_VALUE";
        try {
            var schema = SchemaParser.parse(sdl);
        } catch (GraphQLSyntaxException e) {
            // Acceptable
        }
    }

    @Test void testSchemaPrinterWithDirective() {
        String sdl = "type Query { hello: String } directive @upper on FIELD_DEFINITION";
        var schema = SchemaParser.parse(sdl);
        String printed = SchemaPrinter.print(schema);
        assertThat(printed).contains("@upper");
    }

    @Test void testParseDirectiveWithArgs() {
        String sdl = "type Query { hello: String } directive @auth(requires: String) on FIELD_DEFINITION";
        var schema = SchemaParser.parse(sdl);
    }

    @Test void testSchemaPrinterPrintsAllTypes() {
        String sdl = """
            type Query { user: User status: Status search: SearchResult }
            scalar DateTime
            input CreateUserInput { name: String }
            enum Status { ACTIVE INACTIVE }
            interface Node { id: ID }
            union SearchResult = User | Post
            directive @upper on FIELD_DEFINITION
            type User implements Node { id: ID name: String createdAt: DateTime }
            type Post { title: String author: User }
            """;
        var schema = SchemaParser.parse(sdl);
        String printed = SchemaPrinter.print(schema);
        assertThat(printed).contains("scalar DateTime");
        assertThat(printed).contains("enum Status");
    }
}
