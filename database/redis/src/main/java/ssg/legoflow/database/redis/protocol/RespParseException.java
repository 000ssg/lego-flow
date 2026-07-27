package ssg.legoflow.database.redis.protocol;

import java.io.IOException;

/**
 * Exception thrown when a RESP message cannot be parsed.
 *
 * @since 1.0.0
 */
public class RespParseException extends IOException {

    /**
     * Creates a parse exception with the given message.
     *
     * @param message description of the parse error
     */
    public RespParseException(String message) {
        super(message);
    }

    /**
     * Creates a parse exception with the given message and cause.
     *
     * @param message description of the parse error
     * @param cause   the underlying cause
     */
    public RespParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
