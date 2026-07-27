package ssg.legoflow.email.smtp.client;

import ssg.legoflow.email.smtp.protocol.SmtpReply;

/**
 * Exception thrown when an SMTP operation fails.
 *
 * <p>Contains the server reply that caused the failure, if available.
 *
 * @since 1.0.0
 */
public class SmtpException extends Exception {

    private final SmtpReply reply;

    /**
     * Creates an SMTP exception with a message and reply.
     *
     * @param message the error message
     * @param reply   the server reply
     */
    public SmtpException(String message, SmtpReply reply) {
        super(message + ": " + reply);
        this.reply = reply;
    }

    /**
     * Creates an SMTP exception with a message.
     *
     * @param message the error message
     */
    public SmtpException(String message) {
        super(message);
        this.reply = null;
    }

    /**
     * Creates an SMTP exception with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public SmtpException(String message, Throwable cause) {
        super(message, cause);
        this.reply = null;
    }

    /**
     * Returns the server reply, if available.
     *
     * @return the reply, or {@code null}
     */
    public SmtpReply reply() {
        return reply;
    }

    /**
     * Returns the reply code, or -1 if no reply.
     *
     * @return the reply code
     */
    public int replyCode() {
        return reply != null ? reply.code() : -1;
    }
}
