package ssg.legoflow.rpc.graphql.language;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphQLLexerTest {

    @Test
    void testPunctuators() {
        var tokens = new GraphQLLexer("{ } ( ) [ ] : = @ ! $ & |").tokenize();
        assertThat(tokens).extracting(Token::type).containsExactly(
                Token.Type.BRACE_LEFT, Token.Type.BRACE_RIGHT,
                Token.Type.PAREN_LEFT, Token.Type.PAREN_RIGHT,
                Token.Type.BRACKET_LEFT, Token.Type.BRACKET_RIGHT,
                Token.Type.COLON, Token.Type.EQUALS,
                Token.Type.AT, Token.Type.BANG,
                Token.Type.DOLLAR, Token.Type.AMP, Token.Type.PIPE,
                Token.Type.EOF);
    }

    @Test
    void testSpread() {
        var tokens = new GraphQLLexer("...").tokenize();
        assertThat(tokens.getFirst().type()).isEqualTo(Token.Type.SPREAD);
    }

    @Test
    void testName() {
        var tokens = new GraphQLLexer("query myQuery _id").tokenize();
        assertThat(tokens).extracting(Token::type).containsExactly(
                Token.Type.NAME, Token.Type.NAME, Token.Type.NAME, Token.Type.EOF);
        assertThat(tokens.get(0).value()).isEqualTo("query");
        assertThat(tokens.get(1).value()).isEqualTo("myQuery");
        assertThat(tokens.get(2).value()).isEqualTo("_id");
    }

    @Test
    void testIntValue() {
        var tokens = new GraphQLLexer("42 -7 0").tokenize();
        assertThat(tokens).extracting(Token::type).containsExactly(
                Token.Type.INT_VALUE, Token.Type.INT_VALUE, Token.Type.INT_VALUE, Token.Type.EOF);
        assertThat(tokens.get(0).value()).isEqualTo("42");
        assertThat(tokens.get(1).value()).isEqualTo("-7");
    }

    @Test
    void testFloatValue() {
        var tokens = new GraphQLLexer("3.14 -2.5 1e10 1.5E-3").tokenize();
        assertThat(tokens).extracting(Token::type).containsExactly(
                Token.Type.FLOAT_VALUE, Token.Type.FLOAT_VALUE,
                Token.Type.FLOAT_VALUE, Token.Type.FLOAT_VALUE, Token.Type.EOF);
    }

    @Test
    void testStringValue() {
        var tokens = new GraphQLLexer("\"hello world\"").tokenize();
        assertThat(tokens.getFirst().type()).isEqualTo(Token.Type.STRING_VALUE);
        assertThat(tokens.getFirst().value()).isEqualTo("hello world");
    }

    @Test
    void testStringEscapes() {
        var tokens = new GraphQLLexer("\"tab\\there\\nnewline\"").tokenize();
        assertThat(tokens.getFirst().value()).isEqualTo("tab\there\nnewline");
    }

    @Test
    void testStringUnicodeEscape() {
        var tokens = new GraphQLLexer("\"\\u0041\"").tokenize();
        assertThat(tokens.getFirst().value()).isEqualTo("A");
    }

    @Test
    void testBlockString() {
        var tokens = new GraphQLLexer("\"\"\"hello\nworld\"\"\"").tokenize();
        assertThat(tokens.getFirst().type()).isEqualTo(Token.Type.BLOCK_STRING);
        assertThat(tokens.getFirst().value()).isEqualTo("hello\nworld");
    }

    @Test
    void testCommentSkipped() {
        var tokens = new GraphQLLexer("query # this is a comment\n{ }").tokenize();
        assertThat(tokens).extracting(Token::type).containsExactly(
                Token.Type.NAME, Token.Type.BRACE_LEFT, Token.Type.BRACE_RIGHT, Token.Type.EOF);
    }

    @Test
    void testCommasIgnored() {
        var tokens = new GraphQLLexer("a, b, c").tokenize();
        assertThat(tokens).extracting(Token::type).containsExactly(
                Token.Type.NAME, Token.Type.NAME, Token.Type.NAME, Token.Type.EOF);
    }

    @Test
    void testLineTracking() {
        var tokens = new GraphQLLexer("a\nb\nc").tokenize();
        assertThat(tokens.get(0).line()).isEqualTo(1);
        assertThat(tokens.get(1).line()).isEqualTo(2);
        assertThat(tokens.get(2).line()).isEqualTo(3);
    }

    @Test
    void testUnterminatedString() {
        assertThatThrownBy(() -> new GraphQLLexer("\"unterminated").tokenize())
                .isInstanceOf(GraphQLSyntaxException.class);
    }

    @Test
    void testUnexpectedCharacter() {
        assertThatThrownBy(() -> new GraphQLLexer("~").tokenize())
                .isInstanceOf(GraphQLSyntaxException.class);
    }

    @Test
    void testEmptyInput() {
        var tokens = new GraphQLLexer("").tokenize();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().type()).isEqualTo(Token.Type.EOF);
    }

    @Test
    void testWhitespaceOnly() {
        var tokens = new GraphQLLexer("   \t\n  ").tokenize();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().type()).isEqualTo(Token.Type.EOF);
    }

    @Test
    void testComplexQuery() {
        var query = """
                query HeroQuery($id: ID!) {
                  hero(id: $id) {
                    name
                    friends {
                      name
                    }
                  }
                }
                """;
        var tokens = new GraphQLLexer(query).tokenize();
        assertThat(tokens).hasSizeGreaterThan(15);
        assertThat(tokens.getFirst().value()).isEqualTo("query");
    }
}
