package ssg.legoflow.http.auth;

import ssg.legoflow.http.auth.session.SessionManager;

import java.util.Objects;
import java.util.Optional;

/**
 * Authentication context providing the realm, user store lookup, and session management
 * needed by authentication schemes during request processing.
 *
 * @since 1.0.0
 */
public class AuthContext {

    private final String realm;
    private final UserStore userStore;
    private final SessionManager sessionManager;
    private AuthPrincipal authenticatedPrincipal;

    /**
     * Creates an authentication context.
     *
     * @param realm          the authentication realm displayed in challenges
     * @param userStore      the user store for credential validation
     * @param sessionManager optional session manager for session-based auth
     * @since 1.0.0
     */
    public AuthContext(String realm, UserStore userStore, SessionManager sessionManager) {
        this.realm = Objects.requireNonNull(realm, "realm must not be null");
        this.userStore = userStore;
        this.sessionManager = sessionManager;
    }

    /**
     * Creates a minimal context with just a realm.
     *
     * @param realm the authentication realm
     * @return the auth context
     * @since 1.0.0
     */
    public static AuthContext ofRealm(String realm) {
        return new AuthContext(realm, null, null);
    }

    /**
     * Returns the authentication realm.
     *
     * @return the realm
     * @since 1.0.0
     */
    public String getRealm() {
        return realm;
    }

    /**
     * Returns the user store, if available.
     *
     * @return optional user store
     * @since 1.0.0
     */
    public Optional<UserStore> getUserStore() {
        return Optional.ofNullable(userStore);
    }

    /**
     * Returns the session manager, if available.
     *
     * @return optional session manager
     * @since 1.0.0
     */
    public Optional<SessionManager> getSessionManager() {
        return Optional.ofNullable(sessionManager);
    }

    /**
     * Sets the authenticated principal after successful authentication.
     *
     * @param principal the authenticated principal
     * @since 1.0.0
     */
    public void setAuthenticatedPrincipal(AuthPrincipal principal) {
        this.authenticatedPrincipal = principal;
    }

    /**
     * Returns the authenticated principal, if authentication succeeded.
     *
     * @return optional authenticated principal
     * @since 1.0.0
     */
    public Optional<AuthPrincipal> getAuthenticatedPrincipal() {
        return Optional.ofNullable(authenticatedPrincipal);
    }

    /**
     * Interface for user credential stores used by authentication schemes.
     *
     * @since 1.0.0
     */
    public interface UserStore {

        /**
         * Validates credentials and returns the authenticated principal if valid.
         *
         * @param username the username
         * @param password the password
         * @return the principal if credentials are valid, empty otherwise
         * @since 1.0.0
         */
        Optional<AuthPrincipal> authenticate(String username, String password);

        /**
         * Looks up a principal by username without password validation.
         * Used by digest authentication where the scheme computes the hash itself.
         *
         * @param username the username
         * @return the principal if found, empty otherwise
         * @since 1.0.0
         */
        Optional<AuthPrincipal> findByUsername(String username);

        /**
         * Returns the stored password for a user. Used by digest authentication
         * to compute the expected response hash.
         *
         * @param username the username
         * @return the password if found, empty otherwise
         * @since 1.0.0
         */
        Optional<String> getPassword(String username);
    }
}
