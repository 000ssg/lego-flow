package ssg.legoflow.rpc.graphql.validation;

/**
 * Represents a validation error found during query validation.
 *
 * @param message the error message
 * @param rule    the validation rule that produced this error
 * @since 0.1.0
 */
public record ValidationError(String message, String rule) {

    /**
     * Creates a validation error with a message and rule name.
     *
     * @param message the error message
     * @param rule    the rule name
     * @return a new validation error
     */
    public static ValidationError of(String message, String rule) {
        return new ValidationError(message, rule);
    }

    /**
     * Creates a validation error with just a message.
     *
     * @param message the error message
     * @return a new validation error
     */
    public static ValidationError of(String message) {
        return new ValidationError(message, "unknown");
    }

    @Override
    public String toString() {
        return "[" + rule + "] " + message;
    }
}
