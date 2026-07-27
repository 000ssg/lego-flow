package ssg.legoflow.rpc.graphql.language;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizes a GraphQL query string into a sequence of tokens.
 *
 * <p>Implements the lexical grammar defined in the GraphQL specification,
 * handling all punctuators, names, numeric literals, string literals
 * (including block strings), and comments.
 *
 * @since 1.0.0
 */
public final class GraphQLLexer {

    private final String source;
    private int pos;
    private int line;
    private int col;

    /**
     * Creates a new lexer for the given source.
     *
     * @param source the GraphQL source string
     */
    public GraphQLLexer(String source) {
        this.source = source;
        this.pos = 0;
        this.line = 1;
        this.col = 1;
    }

    /**
     * Tokenizes the entire source into a list of tokens, excluding comments.
     *
     * @return the list of tokens
     * @throws GraphQLSyntaxException if the source contains invalid tokens
     */
    public List<Token> tokenize() {
        var tokens = new ArrayList<Token>();
        while (true) {
            var token = nextToken();
            if (token.type() == Token.Type.COMMENT) continue;
            tokens.add(token);
            if (token.type() == Token.Type.EOF) break;
        }
        return tokens;
    }

    /**
     * Returns the next token from the source.
     *
     * @return the next token
     * @throws GraphQLSyntaxException if the source contains invalid tokens
     */
    public Token nextToken() {
        skipWhitespaceAndCommas();

        if (pos >= source.length()) {
            return new Token(Token.Type.EOF, "", line, col);
        }

        char c = source.charAt(pos);
        int startLine = line;
        int startCol = col;

        // Comment
        if (c == '#') {
            return readComment(startLine, startCol);
        }

        // Punctuators
        switch (c) {
            case '!': advance(); return new Token(Token.Type.BANG, "!", startLine, startCol);
            case '$': advance(); return new Token(Token.Type.DOLLAR, "$", startLine, startCol);
            case '&': advance(); return new Token(Token.Type.AMP, "&", startLine, startCol);
            case '(': advance(); return new Token(Token.Type.PAREN_LEFT, "(", startLine, startCol);
            case ')': advance(); return new Token(Token.Type.PAREN_RIGHT, ")", startLine, startCol);
            case ':': advance(); return new Token(Token.Type.COLON, ":", startLine, startCol);
            case '=': advance(); return new Token(Token.Type.EQUALS, "=", startLine, startCol);
            case '@': advance(); return new Token(Token.Type.AT, "@", startLine, startCol);
            case '[': advance(); return new Token(Token.Type.BRACKET_LEFT, "[", startLine, startCol);
            case ']': advance(); return new Token(Token.Type.BRACKET_RIGHT, "]", startLine, startCol);
            case '{': advance(); return new Token(Token.Type.BRACE_LEFT, "{", startLine, startCol);
            case '}': advance(); return new Token(Token.Type.BRACE_RIGHT, "}", startLine, startCol);
            case '|': advance(); return new Token(Token.Type.PIPE, "|", startLine, startCol);
        }

        // Spread
        if (c == '.' && pos + 2 < source.length()
                && source.charAt(pos + 1) == '.' && source.charAt(pos + 2) == '.') {
            advance(); advance(); advance();
            return new Token(Token.Type.SPREAD, "...", startLine, startCol);
        }

        // String
        if (c == '"') {
            if (pos + 2 < source.length()
                    && source.charAt(pos + 1) == '"' && source.charAt(pos + 2) == '"') {
                return readBlockString(startLine, startCol);
            }
            return readString(startLine, startCol);
        }

        // Number (Int or Float)
        if (c == '-' || isDigit(c)) {
            return readNumber(startLine, startCol);
        }

        // Name
        if (c == '_' || isLetter(c)) {
            return readName(startLine, startCol);
        }

        throw new GraphQLSyntaxException("Unexpected character '" + c + "'", startLine, startCol);
    }

    private Token readComment(int startLine, int startCol) {
        advance(); // skip #
        var sb = new StringBuilder();
        while (pos < source.length() && source.charAt(pos) != '\n' && source.charAt(pos) != '\r') {
            sb.append(source.charAt(pos));
            advance();
        }
        return new Token(Token.Type.COMMENT, sb.toString().trim(), startLine, startCol);
    }

    private Token readName(int startLine, int startCol) {
        var sb = new StringBuilder();
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '_' || isLetter(c) || isDigit(c)) {
                sb.append(c);
                advance();
            } else {
                break;
            }
        }
        return new Token(Token.Type.NAME, sb.toString(), startLine, startCol);
    }

    private Token readNumber(int startLine, int startCol) {
        var sb = new StringBuilder();
        boolean isFloat = false;

        if (pos < source.length() && source.charAt(pos) == '-') {
            sb.append('-');
            advance();
        }

        // Integer part
        if (pos < source.length() && source.charAt(pos) == '0') {
            sb.append('0');
            advance();
        } else {
            while (pos < source.length() && isDigit(source.charAt(pos))) {
                sb.append(source.charAt(pos));
                advance();
            }
        }

        // Fractional part
        if (pos < source.length() && source.charAt(pos) == '.') {
            isFloat = true;
            sb.append('.');
            advance();
            while (pos < source.length() && isDigit(source.charAt(pos))) {
                sb.append(source.charAt(pos));
                advance();
            }
        }

        // Exponent part
        if (pos < source.length() && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
            isFloat = true;
            sb.append(source.charAt(pos));
            advance();
            if (pos < source.length() && (source.charAt(pos) == '+' || source.charAt(pos) == '-')) {
                sb.append(source.charAt(pos));
                advance();
            }
            while (pos < source.length() && isDigit(source.charAt(pos))) {
                sb.append(source.charAt(pos));
                advance();
            }
        }

        return new Token(isFloat ? Token.Type.FLOAT_VALUE : Token.Type.INT_VALUE,
                sb.toString(), startLine, startCol);
    }

    private Token readString(int startLine, int startCol) {
        advance(); // skip opening quote
        var sb = new StringBuilder();
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '"') {
                advance();
                return new Token(Token.Type.STRING_VALUE, sb.toString(), startLine, startCol);
            }
            if (c == '\\') {
                advance();
                if (pos >= source.length()) break;
                char escaped = source.charAt(pos);
                switch (escaped) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u': {
                        advance();
                        if (pos + 3 >= source.length()) {
                            throw new GraphQLSyntaxException("Invalid unicode escape", line, col);
                        }
                        String hex = source.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        advance(); advance(); advance(); // advance 3, the 4th is done below
                        break;
                    }
                    default:
                        throw new GraphQLSyntaxException("Invalid escape: \\" + escaped, line, col);
                }
                advance();
            } else if (c == '\n' || c == '\r') {
                throw new GraphQLSyntaxException("Unterminated string", line, col);
            } else {
                sb.append(c);
                advance();
            }
        }
        throw new GraphQLSyntaxException("Unterminated string", startLine, startCol);
    }

    private Token readBlockString(int startLine, int startCol) {
        advance(); advance(); advance(); // skip """
        var sb = new StringBuilder();
        while (pos < source.length()) {
            if (pos + 2 < source.length()
                    && source.charAt(pos) == '"'
                    && source.charAt(pos + 1) == '"'
                    && source.charAt(pos + 2) == '"') {
                // Check it's not escaped
                if (pos > 0 && source.charAt(pos - 1) == '\\') {
                    sb.deleteCharAt(sb.length() - 1);
                    sb.append("\"\"\"");
                    advance(); advance(); advance();
                    continue;
                }
                advance(); advance(); advance();
                return new Token(Token.Type.BLOCK_STRING, processBlockString(sb.toString()),
                        startLine, startCol);
            }
            char c = source.charAt(pos);
            sb.append(c);
            if (c == '\n') {
                line++;
                col = 1;
            } else if (c == '\r') {
                line++;
                col = 1;
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '\n') {
                    sb.append('\n');
                    pos++;
                }
            } else {
                col++;
            }
            pos++;
        }
        throw new GraphQLSyntaxException("Unterminated block string", startLine, startCol);
    }

    private String processBlockString(String raw) {
        // Split into lines
        var lines = raw.split("\r\n|\r|\n", -1);
        // Find common indent
        int commonIndent = Integer.MAX_VALUE;
        for (int i = 1; i < lines.length; i++) {
            int indent = leadingWhitespace(lines[i]);
            if (indent < lines[i].length()) {
                commonIndent = Math.min(commonIndent, indent);
            }
        }
        // Remove common indent from all lines except first
        if (commonIndent != Integer.MAX_VALUE) {
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].length() >= commonIndent) {
                    lines[i] = lines[i].substring(commonIndent);
                }
            }
        }
        // Remove leading blank lines
        int start = 0;
        while (start < lines.length && lines[start].isBlank()) start++;
        // Remove trailing blank lines
        int end = lines.length;
        while (end > start && lines[end - 1].isBlank()) end--;

        var sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private int leadingWhitespace(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ' || s.charAt(i) == '\t') count++;
            else break;
        }
        return count;
    }

    private void skipWhitespaceAndCommas() {
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == ' ' || c == '\t' || c == ',') {
                advance();
            } else if (c == '\n') {
                pos++;
                line++;
                col = 1;
            } else if (c == '\r') {
                pos++;
                line++;
                col = 1;
                if (pos < source.length() && source.charAt(pos) == '\n') {
                    pos++;
                }
            } else if (c == '﻿') { // BOM
                advance();
            } else {
                break;
            }
        }
    }

    private void advance() {
        pos++;
        col++;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
