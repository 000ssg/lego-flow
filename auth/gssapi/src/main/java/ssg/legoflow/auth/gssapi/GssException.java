package ssg.legoflow.auth.gssapi;

import org.ietf.jgss.GSSException;

/**
 * Wraps {@link GSSException} with additional context about where in the
 * authentication flow the error occurred.
 *
 * @since 0.1.0
 */
public class GssException extends Exception {

    private final int majorCode;
    private final int minorCode;

    /**
     * Creates a new GSS exception wrapper.
     *
     * @param message descriptive message about the operation that failed
     * @param cause   the underlying GSSException
     * @since 0.1.0
     */
    public GssException(String message, GSSException cause) {
        super(message + ": " + cause.getMessage(), cause);
        this.majorCode = cause.getMajor();
        this.minorCode = cause.getMinor();
    }

    /**
     * Creates a new GSS exception with a message only.
     *
     * @param message descriptive message
     * @since 0.1.0
     */
    public GssException(String message) {
        super(message);
        this.majorCode = 0;
        this.minorCode = 0;
    }

    /**
     * Creates a new GSS exception wrapping a general cause.
     *
     * @param message descriptive message
     * @param cause   the underlying cause
     * @since 0.1.0
     */
    public GssException(String message, Throwable cause) {
        super(message + ": " + cause.getMessage(), cause);
        this.majorCode = 0;
        this.minorCode = 0;
    }

    /**
     * Returns the GSS-API major status code, or 0 if not from a GSSException.
     *
     * @return the major status code
     * @since 0.1.0
     */
    public int getMajorCode() {
        return majorCode;
    }

    /**
     * Returns the GSS-API minor (mechanism-specific) status code, or 0 if not from a GSSException.
     *
     * @return the minor status code
     * @since 0.1.0
     */
    public int getMinorCode() {
        return minorCode;
    }

    /**
     * Returns the underlying GSSException if present.
     *
     * @return the underlying GSSException, or null
     * @since 0.1.0
     */
    public GSSException getGssException() {
        return getCause() instanceof GSSException gsse ? gsse : null;
    }
}
