package ssg.legoflow.ftp.security;

/**
 * FTPS connection mode as defined in RFC 4217.
 *
 * @since 0.1.0
 */
public enum FtpsMode {

    /**
     * Implicit FTPS — TLS is established immediately on connection (port 990).
     * The entire session is encrypted from the start.
     */
    IMPLICIT(990),

    /**
     * Explicit FTPS — the client connects in plain text (port 21) and then
     * upgrades to TLS using the AUTH TLS command.
     */
    EXPLICIT(21);

    private final int defaultPort;

    FtpsMode(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    /**
     * Returns the default port for this FTPS mode.
     *
     * @return 990 for implicit, 21 for explicit
     */
    public int defaultPort() {
        return defaultPort;
    }
}
