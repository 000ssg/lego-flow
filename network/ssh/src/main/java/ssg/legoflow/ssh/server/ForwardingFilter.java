package ssg.legoflow.ssh.server;

/**
 * Filter that controls which port forwarding requests are allowed.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface ForwardingFilter {

    /**
     * Checks whether a forwarding request should be allowed.
     *
     * @param username the authenticated user
     * @param host     the target host
     * @param port     the target port
     * @return true if the forwarding is allowed
     */
    boolean allow(String username, String host, int port);

    /**
     * Creates a filter that allows all forwarding.
     *
     * @return an allow-all filter
     */
    static ForwardingFilter allowAll() {
        return (username, host, port) -> true;
    }

    /**
     * Creates a filter that denies all forwarding.
     *
     * @return a deny-all filter
     */
    static ForwardingFilter denyAll() {
        return (username, host, port) -> false;
    }
}
