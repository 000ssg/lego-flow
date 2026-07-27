package ssg.legoflow.rpc.graphql.execution;

import java.util.*;

/**
 * Represents the result of executing a GraphQL operation.
 *
 * <p>A result contains optional data and optional errors. Per the spec,
 * partial results are allowed: data may be present alongside errors.
 *
 * @since 1.0.0
 */
public final class ExecutionResult {

    private final Object data;
    private final List<GraphQLError> errors;

    /**
     * Creates a new execution result.
     *
     * @param data   the result data
     * @param errors the errors, if any
     */
    public ExecutionResult(Object data, List<GraphQLError> errors) {
        this.data = data;
        this.errors = errors != null ? List.copyOf(errors) : List.of();
    }

    /**
     * Creates a successful result with no errors.
     *
     * @param data the result data
     * @return a new execution result
     */
    public static ExecutionResult of(Object data) {
        return new ExecutionResult(data, List.of());
    }

    /**
     * Creates an error-only result with no data.
     *
     * @param errors the errors
     * @return a new execution result
     */
    public static ExecutionResult ofErrors(List<GraphQLError> errors) {
        return new ExecutionResult(null, errors);
    }

    /**
     * Returns the result data.
     *
     * @param <T> the expected type
     * @return the data, or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getData() { return (T) data; }

    /**
     * Returns the errors.
     *
     * @return the errors list (may be empty)
     */
    public List<GraphQLError> getErrors() { return errors; }

    /**
     * Returns whether there are any errors.
     *
     * @return true if there are errors
     */
    public boolean hasErrors() { return !errors.isEmpty(); }

    /**
     * Converts this result to a map suitable for JSON serialization.
     *
     * @return the map representation
     */
    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        if (data != null) {
            map.put("data", data);
        } else if (errors.isEmpty()) {
            map.put("data", null);
        }
        if (!errors.isEmpty()) {
            if (data == null) map.put("data", null);
            map.put("errors", errors.stream().map(GraphQLError::toMap).toList());
        }
        return map;
    }

    /**
     * Represents a GraphQL error.
     *
     * @since 1.0.0
     */
    public record GraphQLError(String message, List<Object> path,
                                List<SourceLocation> locations,
                                Map<String, Object> extensions) {

        /**
         * Creates a simple error with just a message.
         *
         * @param message the error message
         * @return a new error
         */
        public static GraphQLError of(String message) {
            return new GraphQLError(message, null, null, null);
        }

        /**
         * Creates an error with a message and path.
         *
         * @param message the error message
         * @param path    the field path
         * @return a new error
         */
        public static GraphQLError of(String message, List<Object> path) {
            return new GraphQLError(message, path, null, null);
        }

        /**
         * Converts this error to a map for JSON serialization.
         *
         * @return the map representation
         */
        public Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("message", message);
            if (path != null && !path.isEmpty()) map.put("path", path);
            if (locations != null && !locations.isEmpty()) {
                map.put("locations", locations.stream()
                        .map(l -> Map.of("line", l.line, "column", l.column))
                        .toList());
            }
            if (extensions != null && !extensions.isEmpty()) map.put("extensions", extensions);
            return map;
        }

        /**
         * Represents a source location in the query.
         */
        public record SourceLocation(int line, int column) {}
    }
}
