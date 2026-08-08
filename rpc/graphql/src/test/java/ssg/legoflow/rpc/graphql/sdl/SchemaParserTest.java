package ssg.legoflow.rpc.graphql.sdl;

import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.graphql.language.GraphQLSyntaxException;
import static org.assertj.core.api.Assertions.*;

class SchemaParserTest {

    @Test void testParseSimpleSchema() {
        String sdl = "type Query { hello: String user(id: ID): User } type User { id: ID name: String age: Int }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.queryType()).isNotNull();
        assertThat(schema.getType("User")).isNotNull();
    }

    @Test void testParseSchemaWithMutation() {
        String sdl = """
            schema { query: Query mutation: Mutation }
            type Query { user: User }
            type Mutation { createUser(name: String!): User }
            type User { id: ID name: String }
            """;
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.queryType()).isNotNull();
        assertThat(schema.mutationType()).isNotNull();
    }

    @Test void testParseEnum() {
        String sdl = "type Query { status: Status } enum Status { ACTIVE INACTIVE PENDING }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("Status")).isNotNull();
    }

    @Test void testParseInterface() {
        String sdl = """
            type Query { node: Node }
            interface Node { id: ID! }
            type User implements Node { id: ID! name: String }
            """;
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("Node")).isNotNull();
    }

    @Test void testParseUnion() {
        String sdl = """
            type Query { search: SearchResult }
            union SearchResult = User | Post
            type User { name: String }
            type Post { title: String }
            """;
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("SearchResult")).isNotNull();
    }

    @Test void testParseInputObject() {
        String sdl = """
            type Query { user: User }
            input CreateUserInput { name: String! age: Int }
            type User { id: ID name: String }
            """;
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("CreateUserInput")).isNotNull();
    }

    @Test void testParseScalar() {
        String sdl = "type Query { date: Date } scalar Date";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("Date")).isNotNull();
    }

    @Test void testParseSchemaDefinition() {
        String sdl = """
            schema { query: MyQuery mutation: MyMutation }
            type MyQuery { hello: String }
            type MyMutation { doThing: Boolean }
            """;
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.queryType().name()).isEqualTo("MyQuery");
    }

    @Test void testParseInvalidSdl() {
        assertThatThrownBy(() -> SchemaParser.parse("invalid syntax here"))
                .isInstanceOf(GraphQLSyntaxException.class);
    }

    @Test void testParseEmptySchemaThrows() {
        assertThatThrownBy(() -> SchemaParser.parse(""))
                .isInstanceOf(GraphQLSyntaxException.class);
    }

    @Test void testParseListType() {
        String sdl = "type Query { users: [User] } type User { name: String }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.queryType()).isNotNull();
    }

    @Test void testParseNonNullType() {
        String sdl = "type Query { count: Int! }";
        var schema = SchemaParser.parse(sdl);
        var field = schema.queryType().getField("count");
        assertThat(field).isNotNull();
    }

    @Test void testParseWithArguments() {
        String sdl = "type Query { user(id: ID!, name: String): User } type User { id: ID }";
        var schema = SchemaParser.parse(sdl);
        var field = schema.queryType().getField("user");
        assertThat(field.arguments()).hasSize(2);
    }

    @Test void testParseSchemaWithSubscription() {
        String sdl = """
            schema { query: Query mutation: Mutation subscription: Sub }
            type Query { user: User }
            type Mutation { createUser: User }
            type Sub { onUserCreated: User }
            type User { id: ID }
            """;
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.subscriptionType()).isNotNull();
    }

    @Test void testBuiltInScalarsAvailable() {
        String sdl = "type Query { i: Int f: Float s: String b: Boolean id: ID }";
        var schema = SchemaParser.parse(sdl);
        assertThat(schema.getType("Int")).isNotNull();
        assertThat(schema.getType("Float")).isNotNull();
        assertThat(schema.getType("String")).isNotNull();
        assertThat(schema.getType("Boolean")).isNotNull();
        assertThat(schema.getType("ID")).isNotNull();
    }
}
