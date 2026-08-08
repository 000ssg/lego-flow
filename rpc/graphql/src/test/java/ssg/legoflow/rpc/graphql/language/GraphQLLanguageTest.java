package ssg.legoflow.rpc.graphql.language;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GraphQLLanguageTest {

    @Test void testLexerSimpleKeywords() {
        var lexer = new GraphQLLexer("query type enum");
        assertThat(lexer.tokenize()).isNotEmpty();
    }

    @Test void testParserSimpleQuery() {
        var doc = GraphQLParser.parse("query { hello }");
        assertThat(doc.operations()).hasSize(1);
    }

    @Test void testParserQueryWithArguments() {
        var doc = GraphQLParser.parse("query { user(id: \"1\") { name } }");
        assertThat(doc.operations()).hasSize(1);
    }

    @Test void testParserMutation() {
        var doc = GraphQLParser.parse("mutation { createUser(name: \"Alice\") { id } }");
        assertThat(doc.operations()).hasSize(1);
    }

    @Test void testParserWithVariables() {
        var doc = GraphQLParser.parse("query GetUser($id: ID!) { user(id: $id) { name } }");
        assertThat(doc.operations()).hasSize(1);
        assertThat(doc.getOperation(null).variableDefinitions()).isNotEmpty();
    }

    @Test void testParserFragment() {
        var doc = GraphQLParser.parse("query { user { ...UserFields } } fragment UserFields on User { name email }");
        assertThat(doc.fragments()).hasSize(1);
    }

    @Test void testParserWithAlias() {
        var doc = GraphQLParser.parse("{ me: viewer { name } }");
        assertThat(doc.operations()).hasSize(1);
    }

    @Test void testParserInvalidSyntax() {
        assertThatThrownBy(() -> GraphQLParser.parse("{ invalid syntax here @@ }"))
                .isInstanceOf(GraphQLSyntaxException.class);
    }

    @Test void testTokenTypes() {
        var lexer = new GraphQLLexer("query { hello: \"world\" 42 true false null }");
        assertThat(lexer.tokenize()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test void testDocumentOperationsAndFragments() {
        String sdl = "query GetUser($id: ID!) { user(id: $id) { name email } }";
        var parsed = GraphQLParser.parse(sdl);
        assertThat(parsed.operations()).hasSize(1);
        assertThat(parsed.fragments()).isEmpty();
    }

    @Test void testTokenStringValue() {
        var lexer = new GraphQLLexer("\"hello world\"");
        var tokens = lexer.tokenize();
        assertThat(tokens).hasSize(2); // value + EOF
        assertThat(tokens.get(0).value()).isEqualTo("hello world");
    }

    @Test void testTokenIntValue() {
        var lexer = new GraphQLLexer("12345");
        assertThat(lexer.tokenize()).hasSize(2); // value + EOF
    }

    @Test void testTokenFloatValue() {
        var lexer = new GraphQLLexer("3.14");
        assertThat(lexer.tokenize()).hasSize(2); // value + EOF
    }

    @Test void testParserInlineFragment() {
        var doc = GraphQLParser.parse("{ ... on User { name } }");
        assertThat(doc.operations()).hasSize(1);
    }

    @Test void testIntrospectionQuery() {
        String sdl = "{ __schema { types { name kind } } }";
        var parsed = GraphQLParser.parse(sdl);
        assertThat(parsed.operations()).hasSize(1);
    }

    @Test void testGetOperationByName() {
        String sdl = "query GetUser($id: ID!) { user(id: $id) { name } } query GetPost { post { title } }";
        var parsed = GraphQLParser.parse(sdl);
        assertThat(parsed.operations()).hasSize(2);
        var op = parsed.getOperation("GetUser");
        assertThat(op.name()).isEqualTo("GetUser");
    }

    @Test void testFragmentMap() {
        String sdl = "fragment F1 on User { name } fragment F2 on Post { title }";
        var doc = GraphQLParser.parse(sdl);
        assertThat(doc.fragmentMap()).hasSize(2);
    }
}
