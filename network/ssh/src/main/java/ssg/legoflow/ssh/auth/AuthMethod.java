package ssg.legoflow.ssh.auth;

/**
 * Interface for SSH authentication methods per RFC 4252.
 *
 * @since 0.1.0
 */
public interface AuthMethod {

    /**
     * Returns the method name as used in SSH protocol.
     *
     * @return the method name (e.g., "password", "publickey")
     */
    String methodName();

    /**
     * Encodes the method-specific authentication data for a request.
     *
     * @param username    the user name
     * @param serviceName the service name
     * @return the encoded authentication request payload
     */
    byte[] encodeRequest(String username, String serviceName);

    /**
     * Returns whether this method requires additional interaction.
     *
     * @return true if the method may need multiple round-trips
     */
    boolean isInteractive();
}
