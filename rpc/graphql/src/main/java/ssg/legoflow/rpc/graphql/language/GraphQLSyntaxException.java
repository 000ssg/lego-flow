package ssg.legoflow.rpc.graphql.language;

/**
 * Exception thrown when a GraphQL syntax error is encountered during parsing.
 *
 * @since 1.0.0
 */
public class GraphQLSyntaxException extends RuntimeException {

    private final int line;
    private final int column;

    /**
     * Creates a new syntax exception.
     *
     * @param message the error message
     * @param line    the source line number
     * @param column  the source column number
     */
    public GraphQLSyntaxException(String message, int line, int column) {
        super(message + " at line " + line + ", column " + column);
        this.line = line;
        this.column = column;
    }

    /**
     * Creates a new syntax exception with a cause.
     *
     * @param message the error message
     * @param line    the source line number
     * @param column  the source column number
     * @param cause   the underlying cause
     */
    public GraphQLSyntaxException(String message, int line, int column, Throwable cause) {
        super(message + " at line " + line + ", column " + column, cause);
        this.line = line;
        this.column = column;
    }

    /**
     * Returns the line number where the error occurred.
     *
     * @return the line number
     */
    public int line() { return line; }

    /**
     * Returns the column number where the error occurred.
     *
     * @return the column number
     */
    public int column() { return column; }
}
