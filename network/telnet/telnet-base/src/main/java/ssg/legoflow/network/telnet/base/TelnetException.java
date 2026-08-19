package ssg.legoflow.network.telnet.base;

/**
 * Exception thrown for Telnet protocol errors.
 *
 * @since 0.2.0
 */
public class TelnetException extends RuntimeException {

    public TelnetException(String message) {
        super(message);
    }

    public TelnetException(String message, Throwable cause) {
        super(message, cause);
    }
}
