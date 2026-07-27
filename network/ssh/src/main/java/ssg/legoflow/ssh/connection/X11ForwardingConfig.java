package ssg.legoflow.ssh.connection;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Configuration for X11 forwarding per RFC 4254 section 6.3.1.
 *
 * <p>Holds the X11 authentication protocol, cookie, screen number, and whether
 * only a single connection is allowed.
 *
 * @param singleConnection true to allow only a single X11 connection
 * @param authProtocol     the X11 authentication protocol (e.g., "MIT-MAGIC-COOKIE-1")
 * @param authCookie       the authentication cookie bytes (typically 16 bytes)
 * @param screenNumber     the X11 screen number
 * @since 1.0.0
 */
public record X11ForwardingConfig(
        boolean singleConnection,
        String authProtocol,
        byte[] authCookie,
        int screenNumber
) {

    /** Standard MIT-MAGIC-COOKIE-1 authentication protocol. */
    public static final String MIT_MAGIC_COOKIE_1 = "MIT-MAGIC-COOKIE-1";

    /** Default cookie length in bytes. */
    public static final int DEFAULT_COOKIE_LENGTH = 16;

    /**
     * Creates a validated X11 forwarding configuration.
     *
     * @param singleConnection true to allow only a single X11 connection
     * @param authProtocol     the X11 authentication protocol
     * @param authCookie       the authentication cookie bytes
     * @param screenNumber     the X11 screen number
     */
    public X11ForwardingConfig {
        Objects.requireNonNull(authProtocol, "authProtocol");
        Objects.requireNonNull(authCookie, "authCookie");
        authCookie = authCookie.clone();
        if (screenNumber < 0) {
            throw new IllegalArgumentException("screenNumber must be non-negative");
        }
    }

    /**
     * Returns a copy of the authentication cookie.
     *
     * @return the auth cookie bytes
     */
    @Override
    public byte[] authCookie() {
        return authCookie.clone();
    }

    /**
     * Generates a default X11 forwarding configuration with MIT-MAGIC-COOKIE-1
     * and a random 16-byte cookie.
     *
     * @return a new X11 forwarding configuration
     */
    public static X11ForwardingConfig generate() {
        return generate(false, 0);
    }

    /**
     * Generates an X11 forwarding configuration with specified parameters.
     *
     * @param singleConnection true to allow only a single X11 connection
     * @param screenNumber     the X11 screen number
     * @return a new X11 forwarding configuration
     */
    public static X11ForwardingConfig generate(boolean singleConnection, int screenNumber) {
        byte[] cookie = new byte[DEFAULT_COOKIE_LENGTH];
        new SecureRandom().nextBytes(cookie);
        return new X11ForwardingConfig(singleConnection, MIT_MAGIC_COOKIE_1, cookie, screenNumber);
    }
}
