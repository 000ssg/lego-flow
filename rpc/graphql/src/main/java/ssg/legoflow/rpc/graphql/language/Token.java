package ssg.legoflow.rpc.graphql.language;

/**
 * Represents a lexical token from the GraphQL query language.
 *
 * @param type  the token type
 * @param value the token value
 * @param line  the source line number
 * @param col   the source column number
 * @since 0.1.0
 */
public record Token(Type type, String value, int line, int col) {

    /**
     * GraphQL token types.
     */
    public enum Type {
        // Punctuators
        BANG,           // !
        DOLLAR,         // $
        AMP,            // &
        PAREN_LEFT,     // (
        PAREN_RIGHT,    // )
        SPREAD,         // ...
        COLON,          // :
        EQUALS,         // =
        AT,             // @
        BRACKET_LEFT,   // [
        BRACKET_RIGHT,  // ]
        BRACE_LEFT,     // {
        BRACE_RIGHT,    // }
        PIPE,           // |

        // Literals
        NAME,           // identifier
        INT_VALUE,      // integer literal
        FLOAT_VALUE,    // float literal
        STRING_VALUE,   // string literal
        BLOCK_STRING,   // block string literal (""")

        // Keywords (subset of NAME)
        // These are recognized contextually

        // Special
        EOF,            // end of input
        COMMENT         // # comment (usually skipped)
    }

    @Override
    public String toString() {
        if (value != null && !value.isEmpty()) {
            return type + "(" + value + ") at " + line + ":" + col;
        }
        return type + " at " + line + ":" + col;
    }
}
