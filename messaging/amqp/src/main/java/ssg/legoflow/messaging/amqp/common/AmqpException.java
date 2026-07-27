package ssg.legoflow.messaging.amqp.common;

/**
 * Exception thrown when an AMQP protocol error occurs.
 *
 * @since 1.0.0
 */
public class AmqpException extends RuntimeException {

    private final String condition;

    /**
     * Creates an AMQP exception with the given condition and description.
     *
     * @param condition   the AMQP error condition symbol (e.g. {@code amqp:decode-error})
     * @param description a human-readable description
     */
    public AmqpException(String condition, String description) {
        super(description);
        this.condition = condition;
    }

    /**
     * Creates an AMQP exception with the given condition, description, and cause.
     *
     * @param condition   the AMQP error condition symbol
     * @param description a human-readable description
     * @param cause       the underlying cause
     */
    public AmqpException(String condition, String description, Throwable cause) {
        super(description, cause);
        this.condition = condition;
    }

    /**
     * Returns the AMQP error condition symbol.
     *
     * @return the condition string (e.g. {@code amqp:decode-error})
     */
    public String condition() {
        return condition;
    }
}
