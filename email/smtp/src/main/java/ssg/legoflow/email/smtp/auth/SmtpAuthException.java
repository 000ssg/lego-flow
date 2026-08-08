package ssg.legoflow.email.smtp.auth;

/**
 * Exception thrown when SMTP authentication fails.
 *
 * @since 0.1.0
 */
public class SmtpAuthException extends Exception {

    /**
     * Creates an authentication exception with a message.
     *
     * @param message the error message
     */
    public SmtpAuthException(String message) {
        super(message);
    }

    /**
     * Creates an authentication exception with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public SmtpAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
