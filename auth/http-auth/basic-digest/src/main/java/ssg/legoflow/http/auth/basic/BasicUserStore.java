package ssg.legoflow.http.auth.basic;

import ssg.legoflow.http.auth.AuthPrincipal;

import java.util.Optional;

/**
 * Interface for user/password storage used by Basic authentication.
 * Implementations can store passwords in memory, in a database, or hashed.
 *
 * @since 0.1.0
 */
public interface BasicUserStore {

    /**
     * Validates credentials and returns the authenticated principal.
     *
     * @param username the username
     * @param password the password
     * @return the principal if credentials are valid, empty otherwise
     * @since 0.1.0
     */
    Optional<AuthPrincipal> authenticate(String username, String password);

    /**
     * Checks if a user exists.
     *
     * @param username the username
     * @return true if the user exists
     * @since 0.1.0
     */
    boolean userExists(String username);
}
