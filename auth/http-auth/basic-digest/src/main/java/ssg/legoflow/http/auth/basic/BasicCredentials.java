package ssg.legoflow.http.auth.basic;

/**
 * HTTP Basic authentication credentials extracted from the Authorization header.
 *
 * @param username the username
 * @param password the password
 * @since 0.1.0
 */
public record BasicCredentials(String username, String password) {

    /**
     * Creates basic credentials.
     *
     * @param username the username
     * @param password the password
     * @since 0.1.0
     */
    public BasicCredentials {
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("username must not be null or empty");
        }
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
    }
}
