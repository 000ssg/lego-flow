package ssg.legoflow.database.postgresql.auth;

/**
 * Interface for PostgreSQL authentication mechanisms.
 *
 * @since 0.1.0
 */
public interface PgAuthenticator {

    /**
     * Returns the authentication method name.
     *
     * @return the method name (e.g., "cleartext", "md5", "scram-sha-256")
     */
    String method();

    /**
     * Validates credentials.
     *
     * @param username the username
     * @param password the password to validate
     * @return true if authentication succeeds
     */
    boolean authenticate(String username, String password);
}
