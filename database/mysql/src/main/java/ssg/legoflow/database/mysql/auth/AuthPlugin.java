package ssg.legoflow.database.mysql.auth;

/**
 * Interface for MySQL authentication plugins.
 *
 * <p>Authentication plugins generate the auth response that the client sends
 * during the handshake, and verify that response on the server side.
 *
 * @since 0.1.0
 */
public interface AuthPlugin {

    /**
     * Returns the plugin name as used in the MySQL protocol.
     *
     * @return the plugin name (e.g., "mysql_native_password")
     */
    String name();

    /**
     * Generates the authentication response for the client.
     *
     * @param password the user's password
     * @param scramble the server-provided scramble (auth plugin data)
     * @return the auth response bytes to send to the server
     */
    byte[] generateAuthResponse(String password, byte[] scramble);

    /**
     * Verifies a client's auth response on the server side.
     *
     * @param authResponse the client's auth response
     * @param scramble the scramble that was sent to the client
     * @param storedHash the stored password hash (format depends on plugin)
     * @return true if authentication succeeds
     */
    boolean verify(byte[] authResponse, byte[] scramble, byte[] storedHash);
}
